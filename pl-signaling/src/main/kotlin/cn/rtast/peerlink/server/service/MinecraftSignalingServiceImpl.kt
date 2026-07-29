/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */

package cn.rtast.peerlink.server.service

import cn.rtast.klogging.KLogging
import cn.rtast.peerlink.data.play.PlayerInfo
import cn.rtast.peerlink.data.play.RoomState
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import cn.rtast.peerlink.service.MinecraftSignalingService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class MinecraftSignalingServiceImpl(
    private val serverScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val heartbeatTimeoutMs: Long = 30_000L,
) : MinecraftSignalingService {

    private class RoomSession(
        val roomId: String,
        val hostId: Uuid,
    ) {
        val players = ConcurrentHashMap<Uuid, PlayerInfo>()
    }

    private var boundPlayer: PlayerInfo? = null

    private val logger = KLogging.getLogger("Signaling Server | RPC")

    companion object {
        private val rooms = ConcurrentHashMap<String, RoomSession>()
        private val playerRoomMap = ConcurrentHashMap<Uuid, String>()
        private val playerEventFlows = ConcurrentHashMap<Uuid, MutableSharedFlow<SignalEvent>>()
        private val playerHeartbeatJobs = ConcurrentHashMap<Uuid, Job>()
    }

    private fun requireBoundPlayer(): PlayerInfo {
        return boundPlayer ?: throw IllegalStateException("Identity not registered, call registerIdentity first")
    }

    override suspend fun registerIdentity(player: PlayerInfo) {
        if (this.boundPlayer != null) throw IllegalStateException("Already registered identity: ${this.boundPlayer?.username}")
        this.boundPlayer = player
        getOrCreatePlayerFlow(player.uuid)
        refreshHeartbeatTimer(player.uuid)
        logger.info("[RPC Server] Player identity registered: ${player.username} (${player.uuid})")
    }

    /**
     * 心跳包
     */
    override suspend fun sendHeartbeat(clientTimestamp: Long): Long {
        val player = requireBoundPlayer()
        refreshHeartbeatTimer(player.uuid)
        return clientTimestamp
    }

    override fun observeEvents(): Flow<SignalEvent> {
        val player = requireBoundPlayer()
        val userFlow = getOrCreatePlayerFlow(player.uuid)
        return userFlow.asSharedFlow().onCompletion {
            handlePlayerDisconnect(player.uuid)
        }
    }

    override suspend fun createRoom(): RoomState {
        val hostPlayer = requireBoundPlayer()
        refreshHeartbeatTimer(hostPlayer.uuid)
        leaveRoomInternal(hostPlayer.uuid)
        val roomId = generateRoomId()
        val session = RoomSession(roomId, hostPlayer.uuid)
        session.players[hostPlayer.uuid] = hostPlayer
        rooms[roomId] = session
        playerRoomMap[hostPlayer.uuid] = roomId
        return RoomState(roomId, hostPlayer.uuid, session.players.values.toList())
    }

    override suspend fun joinRoom(roomId: String): RoomState? {
        val player = requireBoundPlayer()
        refreshHeartbeatTimer(player.uuid)
        val session = rooms[roomId] ?: return null
        leaveRoomInternal(player.uuid)
        val joinEvent = SignalEvent.PlayerJoined(player)
        session.players.keys.forEach { existingPlayerId -> playerEventFlows[existingPlayerId]?.emit(joinEvent) }
        session.players[player.uuid] = player
        playerRoomMap[player.uuid] = roomId
        return RoomState(session.roomId, session.hostId, session.players.values.toList())
    }

    override suspend fun leaveRoom() {
        val player = requireBoundPlayer()
        refreshHeartbeatTimer(player.uuid)
        leaveRoomInternal(player.uuid)
    }

    override suspend fun sendSignal(targetPlayerId: Uuid, message: SignalingMessage) {
        val sender = requireBoundPlayer()
        refreshHeartbeatTimer(sender.uuid)
        val targetFlow = playerEventFlows[targetPlayerId]
            ?: throw IllegalStateException("Target player $targetPlayerId is offline or unsubscribed signal event")
        val signalEvent = SignalEvent.SignalingReceived(fromPlayerId = sender.uuid, message = message)
        targetFlow.emit(signalEvent)
    }

    private fun refreshHeartbeatTimer(playerId: Uuid) {
        playerHeartbeatJobs[playerId]?.cancel()
        playerHeartbeatJobs[playerId] = serverScope.launch {
            delay(heartbeatTimeoutMs.milliseconds)
            logger.info("[RPC Server] Heartbeat timeout for player: $playerId. Force disconnecting...")
            handlePlayerDisconnect(playerId)
        }
    }

    private fun getOrCreatePlayerFlow(playerId: Uuid): MutableSharedFlow<SignalEvent> {
        return playerEventFlows.computeIfAbsent(playerId) {
            MutableSharedFlow(
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }
    }

    private suspend fun leaveRoomInternal(playerId: Uuid) {
        val roomId = playerRoomMap.remove(playerId) ?: return
        val session = rooms[roomId] ?: return
        session.players.remove(playerId)
        if (session.players.isNotEmpty()) {
            val leftEvent = SignalEvent.PlayerLeft(playerId)
            session.players.keys.forEach { remainingPlayerId ->
                playerEventFlows[remainingPlayerId]?.emit(leftEvent)
            }
        } else {
            rooms.remove(roomId)
            logger.info("[RPC Server] Room $roomId destroyed (empty).")
        }
    }

    private suspend fun handlePlayerDisconnect(playerId: Uuid) {
        logger.info("[RPC Server] Player disconnected/cleaned up: $playerId")
        playerHeartbeatJobs.remove(playerId)?.cancel()
        leaveRoomInternal(playerId)
        playerEventFlows.remove(playerId)
    }

    private fun generateRoomId(): String {
        var id: String
        do {
            id = (10000..99999).random().toString()
        } while (rooms.containsKey(id))
        return id
    }
}