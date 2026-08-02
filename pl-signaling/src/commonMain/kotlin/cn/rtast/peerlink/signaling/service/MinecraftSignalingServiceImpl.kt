/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.signaling.service

import cn.rtast.klogging.KLogging
import cn.rtast.peerlink.data.play.*
import cn.rtast.peerlink.data.webrtc.CloudflareTurnCredentials
import cn.rtast.peerlink.data.webrtc.TurnCredentials
import cn.rtast.peerlink.data.webrtc.toTurnCredentials
import cn.rtast.peerlink.service.MinecraftSignalingService
import cn.rtast.peerlink.signaling.data.ServiceContext
import cn.rtast.peerlink.signaling.data.SignalingServerConfig
import cn.rtast.peerlink.signaling.httpClient
import cn.rtast.peerlink.signaling.kv.CloudflareKvRepository
import cn.rtast.peerlink.signaling.util.CLOUDFLARE_TURN_TOKEN_ID
import cn.rtast.peerlink.signaling.util.CLOUDFLARE_TURN_TOKEN_KEY
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
    private val kvRepository: CloudflareKvRepository,
    private val serverScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val heartbeatTimeoutMs: Long = 30_000L,
    private val config: SignalingServerConfig,
) : MinecraftSignalingService {

    private class LocalRoomContext(val roomId: String, val hostId: Uuid) {
        val pendingRequests = CoroutineConcurrentMap<Uuid, DeferredRequest>()
    }

    private data class DeferredRequest(
        val applicant: PlayerInfo,
        val deferred: CompletableDeferred<JoinResponse>,
    )

    companion object {
        private val localRoomContexts = CoroutineConcurrentMap<String, LocalRoomContext>()
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
                    replay = 64,
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
        kvRepository.setRoomHost(roomId, hostPlayer.uuid)
        kvRepository.addRoomPlayer(roomId, hostPlayer)
        kvRepository.setPlayerRoom(hostPlayer.uuid, roomId)
        val localContext = LocalRoomContext(roomId, hostPlayer.uuid)
        localRoomContexts[roomId] = localContext
        logger.info("[RPC Server] Room $roomId created in KV by host: ${hostPlayer.name} (${hostPlayer.uuid})")
        return RoomState(roomId, hostPlayer.uuid, listOf(hostPlayer))
    }

    override suspend fun joinRoom(roomId: String): JoinResponse {
        val applicant = context.requirePlayer()
        refreshHeartbeatTimer(applicant.uuid)
        val hostId = kvRepository.getRoomHost(roomId) ?: return JoinResponse.InvalidRoom
        val players = kvRepository.getRoomPlayers(roomId)
        if (players.any { it.uuid == applicant.uuid }) return JoinResponse.Error("You are already in this room")
        leaveRoomInternal(applicant.uuid)
        val deferred = CompletableDeferred<JoinResponse>()
        val localContext = localRoomContexts.computeIfAbsent(roomId) { LocalRoomContext(roomId, hostId) }
        localContext.pendingRequests[applicant.uuid] = DeferredRequest(applicant, deferred)
        kvRepository.setPlayerRoom(applicant.uuid, roomId)
        val hostFlow = playerEventFlows[hostId]
        if (hostFlow != null) {
            hostFlow.emit(SignalEvent.JoinRequested(applicant.uuid, applicant.name))
            logger.info("[RPC Server] Join request from ${applicant.name} sent to host $hostId")
        } else {
            localContext.pendingRequests.remove(applicant.uuid)
            kvRepository.removePlayerRoom(applicant.uuid)
            return JoinResponse.Error("Host is offline or unreachable")
        }

        return try {
            withTimeout(30_000L.milliseconds) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            localContext.pendingRequests.remove(applicant.uuid)
            kvRepository.removePlayerRoom(applicant.uuid)
            JoinResponse.Error("The host did not respond to the request")
        }
    }

    override suspend fun respondJoinRequest(applicantId: Uuid, accept: Boolean, reason: String?) {
        val host = context.requirePlayer()
        refreshHeartbeatTimer(host.uuid)
        val roomId = kvRepository.getPlayerRoom(host.uuid)
            ?: throw IllegalStateException("Host is not in any room")
        val hostId = kvRepository.getRoomHost(roomId)
            ?: throw IllegalStateException("Room not found")
        val localContext = localRoomContexts[roomId]
            ?: throw IllegalStateException("Local room context lost")
        val pending = localContext.pendingRequests.remove(applicantId)
            ?: throw IllegalStateException("No pending request for applicant $applicantId")
        if (accept) {
            kvRepository.addRoomPlayer(roomId, pending.applicant)
            val credentials = fetchTurnCredentials()
            pending.deferred.complete(JoinResponse.Accepted(hostId, credentials))
            val turnEventForHost = SignalEvent.TurnCredentialsIssued(applicantId, credentials)
            val joinEvent = SignalEvent.PlayerJoined(pending.applicant)
            val allPlayers = kvRepository.getRoomPlayers(roomId)
            allPlayers.forEach { existing ->
                if (existing.uuid != applicantId && existing.uuid != hostId) playerEventFlows[existing.uuid]?.emit(
                    joinEvent
                )
            }
            playerEventFlows[hostId]?.emit(turnEventForHost)
            logger.info("[RPC Server] Host accepted ${pending.applicant.name} into room $roomId")
        } else {
            pending.deferred.complete(JoinResponse.Rejected(reason ?: "Host rejected your request"))
            kvRepository.removePlayerRoom(applicantId)
            logger.info("[RPC Server] Host rejected $applicantId. Reason: $reason")
        }
    }

    override suspend fun kickPlayer(targetPlayerId: Uuid, reason: String?) {
        val host = context.requirePlayer()
        refreshHeartbeatTimer(host.uuid)
        val roomId = kvRepository.getPlayerRoom(host.uuid)
            ?: throw IllegalStateException("Operator is not in any room")
        val hostId = kvRepository.getRoomHost(roomId)
            ?: throw IllegalStateException("Room $roomId not found")
        playerEventFlows[targetPlayerId]?.emit(SignalEvent.PlayerKicked(targetPlayerId, reason ?: "Kicked by host"))
        leaveRoomInternal(targetPlayerId)
        logger.info("[RPC Server] Host kicked $targetPlayerId from room $roomId")
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
            ?: throw IllegalStateException("Target player $targetPlayerId is offline or unsubscribed")
        targetFlow.emit(SignalEvent.MessageReceived(fromPlayerId = sender.uuid, message = message))
    }

    override suspend fun getRoomState(): RoomState {
        val player = context.requirePlayer()
        refreshHeartbeatTimer(player.uuid)
        val roomId = kvRepository.getPlayerRoom(player.uuid)
            ?: throw IllegalStateException("Player ${player.uuid} is not in any room")
        val hostId = kvRepository.getRoomHost(roomId)
            ?: throw IllegalStateException("Room $roomId not found")
        val players = kvRepository.getRoomPlayers(roomId)
        return RoomState(roomId, hostId, players)
    }

    override suspend fun getRoomStateById(roomId: String): RoomState? {
        val player = context.requirePlayer()
        refreshHeartbeatTimer(player.uuid)
        return kvRepository.getRoomState(roomId)
    }

    override suspend fun acquireTurnCredentials(): TurnCredentials = fetchTurnCredentials()

    private suspend fun fetchTurnCredentials(): TurnCredentials = if (config.useCloudflareTurn) {
        httpClient.post(
            "https://rtc.live.cloudflare.com/v1/turn/keys/$CLOUDFLARE_TURN_TOKEN_ID/credentials/generate-ice-servers"
        ) {
            headers {
                header("Authorization", "Bearer $CLOUDFLARE_TURN_TOKEN_KEY")
                header("Content-Type", "application/json")
            }
            setBody("{\"ttl\":86400}")
        }.bodyAsText().fromJson<CloudflareTurnCredentials>().toTurnCredentials()
    } else config.customStunConfig

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
        val roomId = kvRepository.getPlayerRoom(playerId) ?: return
        kvRepository.removePlayerRoom(playerId)
        val hostId = kvRepository.getRoomHost(roomId) ?: return
        val isHost = (playerId == hostId)
        val localContext = localRoomContexts[roomId]
        localContext?.pendingRequests?.remove(playerId)?.deferred?.complete(
            JoinResponse.Error("Disconnected before host response.")
        )
        kvRepository.removeRoomPlayer(roomId, playerId)
        val remainingPlayers = kvRepository.getRoomPlayers(roomId)
        if (isHost || remainingPlayers.isEmpty()) {
            kvRepository.deleteRoom(roomId)
            localRoomContexts.remove(roomId)
            logger.info("[RPC Server] Room $roomId destroyed in KV (Host $playerId left or room empty).")
            val closeEvent = SignalEvent.RoomClosed(
                reason = if (isHost) "Host has closed or left the room." else "Room empty."
            )
            remainingPlayers.forEach { remaining ->
                if (remaining.uuid != playerId) {
                    kvRepository.removePlayerRoom(remaining.uuid)
                    playerEventFlows[remaining.uuid]?.emit(closeEvent)
                }
            }
        } else {
            val leftEvent = SignalEvent.PlayerLeft(playerId)
            remainingPlayers.forEach { remaining ->
                playerEventFlows[remaining.uuid]?.emit(leftEvent)
            }
        }
    }

    private suspend fun generateRoomId(): String {
        var id: String
        do {
            id = (10000..99999).random().toString()
        } while (kvRepository.roomExists(id))
        return id
    }
}