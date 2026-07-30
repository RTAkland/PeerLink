/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.signaling.service

import cn.rtast.klogging.KLogging
import cn.rtast.peerlink.data.webrtc.OriginTurnCredentials
import cn.rtast.peerlink.data.play.*
import cn.rtast.peerlink.data.webrtc.toTurnCredentials
import cn.rtast.peerlink.service.MinecraftSignalingService
import cn.rtast.peerlink.signaling.CLOUDFLARE_TURN_TOKEN_ID
import cn.rtast.peerlink.signaling.CLOUDFLARE_TURN_TOKEN_KEY
import cn.rtast.peerlink.signaling.data.ServiceContext
import cn.rtast.peerlink.signaling.httpClient
import cn.rtast.peerlink.signaling.util.CoroutineConcurrentMap
import cn.rtast.peerlink.util.fromJson
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class MinecraftSignalingServiceImpl(
    private val context: ServiceContext,
    private val serverScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val heartbeatTimeoutMs: Long = 30_000L,
) : MinecraftSignalingService {

    private class RoomSession(
        val roomId: String,
        val hostId: Uuid,
    ) {
        val players = CoroutineConcurrentMap<Uuid, PlayerInfo>()
        val pendingApplicants = CoroutineConcurrentMap<Uuid, PlayerInfo>()
    }

    companion object {
        private val rooms = CoroutineConcurrentMap<String, RoomSession>()
        private val playerRoomMap = CoroutineConcurrentMap<Uuid, String>()
        private val playerEventFlows = CoroutineConcurrentMap<Uuid, MutableSharedFlow<SignalEvent>>()
        private val playerHeartbeatJobs = CoroutineConcurrentMap<Uuid, Job>()
        private val logger = KLogging.getLogger("Signaling Server | RPC")

        suspend fun refreshHeartbeatTimer(
            playerId: Uuid,
            serverScope: CoroutineScope,
            timeoutMs: Long,
            onTimeout: suspend (Uuid) -> Unit,
        ) {
            playerHeartbeatJobs[playerId]?.cancel()
            val job = serverScope.launch {
                delay(timeoutMs.milliseconds)
                onTimeout(playerId)
            }
            playerHeartbeatJobs[playerId] = job
        }

        suspend fun getOrCreatePlayerFlow(playerId: Uuid): MutableSharedFlow<SignalEvent> {
            return playerEventFlows.computeIfAbsent(playerId) {
                MutableSharedFlow(
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            }
        }
    }

    override suspend fun sendHeartbeat(clientTimestamp: Long): Long {
        val player = context.requirePlayer()
        refreshHeartbeatTimer(player.uuid)
        return clientTimestamp
    }

    override fun observeEvents(): Flow<SignalEvent> = flow {
        val player = context.requirePlayer()
        val userFlow = getOrCreatePlayerFlow(player.uuid)
        emitAll(userFlow.asSharedFlow().onCompletion {
            handlePlayerDisconnect(player.uuid)
        })
    }

    override suspend fun createRoom(): RoomState {
        val hostPlayer = context.requirePlayer()
        refreshHeartbeatTimer(hostPlayer.uuid)
        leaveRoomInternal(hostPlayer.uuid)
        val roomId = generateRoomId()
        val session = RoomSession(roomId, hostPlayer.uuid)
        session.players[hostPlayer.uuid] = hostPlayer
        rooms[roomId] = session
        playerRoomMap[hostPlayer.uuid] = roomId
        logger.info("[RPC Server] Room $roomId created by host: ${hostPlayer.name} (${hostPlayer.uuid})")
        return RoomState(roomId, hostPlayer.uuid, session.players.values())
    }

    override suspend fun sendIntent(intent: PeerIntent) {
        val operator = context.requirePlayer()
        refreshHeartbeatTimer(operator.uuid)
        when (intent.type) {
            IntentType.JOIN_REQUEST -> {
                val targetRoomId =
                    intent.targetRoomId ?: throw IllegalArgumentException("Target roomId required for JOIN_REQUEST.")
                val session = rooms[targetRoomId] ?: run {
                    logger.warn("[RPC Server] Player ${operator.name} tried to join non-existent room: $targetRoomId")
                    playerEventFlows[operator.uuid]?.emit(
                        SignalEvent.IntentResult(
                            intentType = IntentType.JOIN_REQUEST,
                            success = false,
                            reason = "Room $targetRoomId does not exist."
                        )
                    )
                    return
                }
                if (session.players.containsKey(operator.uuid) || session.pendingApplicants.containsKey(operator.uuid)) return
                leaveRoomInternal(operator.uuid)
                session.pendingApplicants[operator.uuid] = operator
                playerRoomMap[operator.uuid] = targetRoomId
                val hostFlow = playerEventFlows[session.hostId]
                if (hostFlow != null) {
                    hostFlow.emit(SignalEvent.JoinRequested(operator.uuid, operator.name))
                    logger.info("[RPC Server] Join request from ${operator.name} forwarded to host ${session.hostId}")
                } else logger.error("[RPC Server] Host ${session.hostId} is offline or has no event flow!")
            }

            else -> {
                val roomId = playerRoomMap[operator.uuid] ?: throw IllegalStateException("Operator is not in any room.")
                val session = rooms[roomId] ?: throw IllegalStateException("Room $roomId not found.")
                when (intent.type) {
                    IntentType.ACCEPT_JOIN -> handleAcceptJoin(session, operator, intent.targetPlayerId)
                    IntentType.REJECT_JOIN -> handleRejectJoin(session, operator, intent.targetPlayerId, intent.reason)
                    IntentType.KICK_PLAYER -> handleKickPlayer(session, operator, intent.targetPlayerId, intent.reason)
                    IntentType.LEAVE_ROOM -> leaveRoomInternal(operator.uuid)
                    else -> error("Unreachable code")
                }
            }
        }
    }

    override suspend fun leaveRoom() {
        val player = context.requirePlayer()
        refreshHeartbeatTimer(player.uuid)
        leaveRoomInternal(player.uuid)
    }

    override suspend fun sendSignal(targetPlayerId: Uuid, message: SignalingMessage) {
        val sender = context.requirePlayer()
        refreshHeartbeatTimer(sender.uuid)
        val targetFlow = playerEventFlows[targetPlayerId]
            ?: throw IllegalStateException("Target player $targetPlayerId is offline or unsubscribed.")
        val signalEvent = SignalEvent.MessageReceived(fromPlayerId = sender.uuid, message = message)
        targetFlow.emit(signalEvent)
    }

    private suspend fun handleAcceptJoin(session: RoomSession, operator: PlayerInfo, targetPlayerId: Uuid?) {
        require(operator.uuid == session.hostId) { "Only room host can accept join requests." }
        requireNotNull(targetPlayerId) { "Target player ID must not be null." }
        val applicant = session.pendingApplicants.remove(targetPlayerId)
            ?: throw IllegalStateException("Player $targetPlayerId is not in pending queue.")
        session.players[targetPlayerId] = applicant
        val credentials =
            httpClient.post("https://rtc.live.cloudflare.com/v1/turn/keys/$CLOUDFLARE_TURN_TOKEN_ID/credentials/generate-ice-servers") {
                headers {
                    header("Authorization", "Bearer $CLOUDFLARE_TURN_TOKEN_KEY")
                    header("Content-Type", "application/json")
                }
                setBody("{\"ttl\":3600}")
            }.bodyAsText().fromJson<OriginTurnCredentials>().toTurnCredentials()
        val turnEventForApplicant = SignalEvent.TurnCredentialsIssued(session.hostId, credentials)
        val turnEventForHost = SignalEvent.TurnCredentialsIssued(targetPlayerId, credentials)

        playerEventFlows[targetPlayerId]?.emit(turnEventForApplicant)
        playerEventFlows[session.hostId]?.emit(turnEventForHost)
        val joinEvent = SignalEvent.PlayerJoined(applicant)
        session.players.keys().forEach { existingId ->
            if (existingId != targetPlayerId) playerEventFlows[existingId]?.emit(joinEvent)
        }

        logger.info("[RPC Server] Host accepted ${applicant.name} into room ${session.roomId}. TURN issued.")
    }

    private suspend fun handleRejectJoin(
        session: RoomSession,
        operator: PlayerInfo,
        targetPlayerId: Uuid?,
        reason: String?,
    ) {
        require(operator.uuid == session.hostId) { "Only room host can reject join requests." }
        requireNotNull(targetPlayerId) { "Target player ID must not be null." }

        session.pendingApplicants.remove(targetPlayerId)
        playerRoomMap.remove(targetPlayerId)

        val resultEvent = SignalEvent.IntentResult(
            intentType = IntentType.REJECT_JOIN,
            success = false,
            reason = reason ?: "Host rejected your request."
        )
        playerEventFlows[targetPlayerId]?.emit(resultEvent)
        logger.info("[RPC Server] Host rejected $targetPlayerId. Reason: $reason")
    }

    private suspend fun handleKickPlayer(
        session: RoomSession,
        operator: PlayerInfo,
        targetPlayerId: Uuid?,
        reason: String?,
    ) {
        require(operator.uuid == session.hostId) { "Only room host can kick players." }
        requireNotNull(targetPlayerId) { "Target player ID must not be null." }
        require(targetPlayerId != session.hostId) { "Host cannot kick themselves." }

        playerEventFlows[targetPlayerId]?.emit(SignalEvent.PlayerKicked(reason ?: "Kicked by host."))
        leaveRoomInternal(targetPlayerId)
        logger.info("[RPC Server] Host kicked $targetPlayerId from room ${session.roomId}.")
    }

    private suspend fun refreshHeartbeatTimer(playerId: Uuid) {
        refreshHeartbeatTimer(playerId, serverScope, heartbeatTimeoutMs) { timeoutPlayerId ->
            logger.info("[RPC Server] Heartbeat timeout for player: $timeoutPlayerId. Force disconnecting...")
            handlePlayerDisconnect(timeoutPlayerId)
        }
    }

    private suspend fun handlePlayerDisconnect(playerId: Uuid) {
        logger.info("[RPC Server] Player disconnected/cleaned up: $playerId")
        val job = playerHeartbeatJobs[playerId]
        playerHeartbeatJobs.remove(playerId)
        job?.cancel()
        leaveRoomInternal(playerId)
        playerEventFlows.remove(playerId)
    }

    private suspend fun leaveRoomInternal(playerId: Uuid) {
        val roomId = playerRoomMap[playerId] ?: return
        playerRoomMap.remove(playerId)
        val session = rooms[roomId] ?: return
        val isHost = (playerId == session.hostId)
        val wasApplicant = session.pendingApplicants.remove(playerId) != null
        val wasPlayer = session.players.remove(playerId) != null
        if (isHost || session.players.isEmpty()) {
            rooms.remove(roomId)
            logger.info("[RPC Server] Room $roomId destroyed (Host $playerId left or room empty).")

            val closeEvent = SignalEvent.RoomClosed(
                reason = if (isHost) "Host has closed or left the room." else "Room empty."
            )
            session.players.keys().forEach { remainingId ->
                if (remainingId != playerId) {
                    playerRoomMap.remove(remainingId)
                    playerEventFlows[remainingId]?.emit(closeEvent)
                }
            }
            session.pendingApplicants.keys().forEach { applicantId ->
                if (applicantId != playerId) {
                    playerRoomMap.remove(applicantId)
                    playerEventFlows[applicantId]?.emit(closeEvent)
                }
            }
        } else {
            if (wasPlayer) {
                val leftEvent = SignalEvent.PlayerLeft(playerId)
                session.players.keys().forEach { remainingPlayerId ->
                    playerEventFlows[remainingPlayerId]?.emit(leftEvent)
                }
            }
        }
    }

    private suspend fun generateRoomId(): String {
        var id: String
        do {
            id = (10000..99999).random().toString()
        } while (rooms.containsKey(id))
        return id
    }
}