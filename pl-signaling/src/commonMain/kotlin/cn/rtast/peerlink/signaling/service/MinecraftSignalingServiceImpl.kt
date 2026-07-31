/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.signaling.service

import cn.rtast.klogging.KLogging
import cn.rtast.peerlink.data.play.*
import cn.rtast.peerlink.data.webrtc.OriginTurnCredentials
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
        val pendingRequests = CoroutineConcurrentMap<Uuid, DeferredRequest>()
    }

    private data class DeferredRequest(
        val applicant: PlayerInfo,
        val deferred: CompletableDeferred<JoinResponse>,
    )

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

    override suspend fun joinRoom(roomId: String): JoinResponse {
        val applicant = context.requirePlayer()
        refreshHeartbeatTimer(applicant.uuid)
        val session = rooms[roomId] ?: return JoinResponse.Error("Room $roomId does not exist.")
        if (session.players.containsKey(applicant.uuid)) {
            return JoinResponse.Error("You are already in this room.")
        }
        leaveRoomInternal(applicant.uuid)
        val deferred = CompletableDeferred<JoinResponse>()
        session.pendingRequests[applicant.uuid] = DeferredRequest(applicant, deferred)
        playerRoomMap[applicant.uuid] = roomId
        val hostFlow = playerEventFlows[session.hostId]
        if (hostFlow != null) {
            hostFlow.emit(SignalEvent.JoinRequested(applicant.uuid, applicant.name))
            logger.info("[RPC Server] Join request from ${applicant.name} sent to host ${session.hostId}")
        } else {
            session.pendingRequests.remove(applicant.uuid)
            playerRoomMap.remove(applicant.uuid)
            return JoinResponse.Error("Host is offline or unreachable.")
        }
        return try {
            withTimeout(30_000L.milliseconds) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            session.pendingRequests.remove(applicant.uuid)
            playerRoomMap.remove(applicant.uuid)
            JoinResponse.Error("Join request timed out (Host did not respond).")
        }
    }

    override suspend fun respondJoinRequest(applicantId: Uuid, accept: Boolean, reason: String?) {
        val host = context.requirePlayer()
        refreshHeartbeatTimer(host.uuid)
        val roomId = playerRoomMap[host.uuid] ?: throw IllegalStateException("Host is not in any room.")
        val session = rooms[roomId] ?: throw IllegalStateException("Room not found.")
        require(session.hostId == host.uuid) { "Only host can respond to join requests." }
        val pending = session.pendingRequests.remove(applicantId)
            ?: throw IllegalStateException("No pending request for applicant $applicantId")
        if (accept) {
            session.players[applicantId] = pending.applicant
            val credentials = fetchTurnCredentials()
            pending.deferred.complete(JoinResponse.Accepted(session.hostId, credentials))
            val turnEventForHost = SignalEvent.TurnCredentialsIssued(applicantId, credentials)
            playerEventFlows[session.hostId]?.emit(turnEventForHost)
            val joinEvent = SignalEvent.PlayerJoined(pending.applicant)
            session.players.keys().forEach { existingId ->
                if (existingId != applicantId && existingId != session.hostId) playerEventFlows[existingId]?.emit(
                    joinEvent
                )
            }
            logger.info("[RPC Server] Host accepted ${pending.applicant.name} into room $roomId.")
        } else {
            playerRoomMap.remove(applicantId)
            pending.deferred.complete(JoinResponse.Rejected(reason ?: "Host rejected your request."))
            logger.info("[RPC Server] Host rejected $applicantId. Reason: $reason")
        }
    }

    override suspend fun kickPlayer(targetPlayerId: Uuid, reason: String?) {
        val host = context.requirePlayer()
        refreshHeartbeatTimer(host.uuid)
        val roomId = playerRoomMap[host.uuid] ?: throw IllegalStateException("Operator is not in any room.")
        val session = rooms[roomId] ?: throw IllegalStateException("Room $roomId not found.")
        require(host.uuid == session.hostId) { "Only room host can kick players." }
        require(targetPlayerId != session.hostId) { "Host cannot kick themselves." }
        playerEventFlows[targetPlayerId]?.emit(SignalEvent.PlayerKicked(reason ?: "Kicked by host."))
        leaveRoomInternal(targetPlayerId)
        logger.info("[RPC Server] Host kicked $targetPlayerId from room $roomId.")
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
        targetFlow.emit(SignalEvent.MessageReceived(fromPlayerId = sender.uuid, message = message))
    }

    override suspend fun getRoomState(): RoomState {
        val player = context.requirePlayer()
        refreshHeartbeatTimer(player.uuid)
        val roomId =
            playerRoomMap[player.uuid] ?: throw IllegalStateException("Player ${player.uuid} is not in any room.")
        val session = rooms[roomId] ?: throw IllegalStateException("Room $roomId not found.")
        return RoomState(session.roomId, session.hostId, session.players.values())
    }

    private suspend fun fetchTurnCredentials() = httpClient.post(
        "https://rtc.live.cloudflare.com/v1/turn/keys/$CLOUDFLARE_TURN_TOKEN_ID/credentials/generate-ice-servers"
    ) {
        headers {
            header("Authorization", "Bearer $CLOUDFLARE_TURN_TOKEN_KEY")
            header("Content-Type", "application/json")
        }
        setBody("{\"ttl\":3600}")
    }.bodyAsText().fromJson<OriginTurnCredentials>().toTurnCredentials()

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
        session.pendingRequests.remove(playerId)?.deferred?.complete(
            JoinResponse.Error("Disconnected before host response.")
        )
        val wasPlayer = session.players.remove(playerId) != null
        if (isHost || session.players.isEmpty()) {
            rooms.remove(roomId)
            logger.info("[RPC Server] Room $roomId destroyed (Host $playerId left or room empty).")
            val closeEvent =
                SignalEvent.RoomClosed(reason = if (isHost) "Host has closed or left the room." else "Room empty.")
            session.players.keys().forEach { remainingId ->
                if (remainingId != playerId) {
                    playerRoomMap.remove(remainingId)
                    playerEventFlows[remainingId]?.emit(closeEvent)
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