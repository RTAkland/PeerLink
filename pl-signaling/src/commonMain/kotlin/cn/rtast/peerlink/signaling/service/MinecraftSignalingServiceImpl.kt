/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.signaling.service

import cn.rtast.klogging.KLogging
import cn.rtast.peerlink.data.play.PlayerInfo
import cn.rtast.peerlink.data.play.RoomState
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import cn.rtast.peerlink.service.MinecraftSignalingService
import cn.rtast.peerlink.signaling.data.ServiceContext
import cn.rtast.peerlink.signaling.util.CoroutineConcurrentMap
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
        return RoomState(roomId, hostPlayer.uuid, session.players.values())
    }

    override suspend fun joinRoom(roomId: String): RoomState? {
        val player = context.requirePlayer()
        refreshHeartbeatTimer(player.uuid)
        val session = rooms[roomId] ?: return null
        leaveRoomInternal(player.uuid)
        val joinEvent = SignalEvent.PlayerJoined(player)

        // 遍历 suspend 的 keys() 集合
        session.players.keys().forEach { existingPlayerId ->
            playerEventFlows[existingPlayerId]?.emit(joinEvent)
        }

        session.players[player.uuid] = player
        playerRoomMap[player.uuid] = roomId
        return RoomState(session.roomId, session.hostId, session.players.values())
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
            ?: throw IllegalStateException("Target player $targetPlayerId is offline or unsubscribed signal event")
        val signalEvent = SignalEvent.SignalingReceived(fromPlayerId = sender.uuid, message = message)
        targetFlow.emit(signalEvent)
    }

    private suspend fun refreshHeartbeatTimer(playerId: Uuid) {
        refreshHeartbeatTimer(playerId, serverScope, heartbeatTimeoutMs) { timeoutPlayerId ->
            logger.info("[RPC Server] Heartbeat timeout for player: $timeoutPlayerId. Force disconnecting...")
            handlePlayerDisconnect(timeoutPlayerId)
        }
    }

    private suspend fun handlePlayerDisconnect(playerId: Uuid) {
        logger.info("[RPC Server] Player disconnected/cleaned up: $playerId")
        // suspend 移除并取消任务
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
        session.players.remove(playerId)

        if (session.players.isNotEmpty()) {
            val leftEvent = SignalEvent.PlayerLeft(playerId)
            session.players.keys().forEach { remainingPlayerId ->
                playerEventFlows[remainingPlayerId]?.emit(leftEvent)
            }
        } else {
            rooms.remove(roomId)
            logger.info("[RPC Server] Room $roomId destroyed (empty).")
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