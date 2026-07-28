/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.server.service

import cn.rtast.peerlink.data.PlayerInfo
import cn.rtast.peerlink.data.RoomEvent
import cn.rtast.peerlink.data.RoomState
import cn.rtast.peerlink.data.SignalingMessage
import cn.rtast.peerlink.service.MinecraftSignalingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class MinecraftSignalingServiceImpl : MinecraftSignalingService {

    private class RoomSession(
        val roomId: String,
        val hostId: Uuid,
    ) {
        val players = ConcurrentHashMap<Uuid, PlayerInfo>()
        val eventChannel = MutableSharedFlow<RoomEvent>(extraBufferCapacity = 64)
    }

    private val rooms = ConcurrentHashMap<String, RoomSession>()

    override suspend fun createRoom(hostPlayer: PlayerInfo): RoomState {
        val roomId = generateRoomId()
        val session = RoomSession(roomId, hostPlayer.uuid)
        rooms[roomId] = session
        return RoomState(roomId, hostPlayer.uuid, session.players.values.toList())
    }

    override suspend fun joinRoom(roomId: String, player: PlayerInfo): RoomState {
        val session = rooms[roomId] ?: throw IllegalArgumentException("房间不存在")
        session.players[player.uuid] = player

        session.eventChannel.emit(RoomEvent.PlayerJoined(player))
        return RoomState(session.roomId, session.hostId, session.players.values.toList())
    }

    override suspend fun leaveRoom(roomId: String, playerId: Uuid) {
        val session = rooms[roomId] ?: return
        session.players.remove(playerId)
        session.eventChannel.emit(RoomEvent.PlayerLeft(playerId))
        if (session.players.isEmpty()) rooms.remove(roomId)
    }

    override fun subscribeRoomEvents(roomId: String, playerId: Uuid): Flow<RoomEvent> {
        val session = rooms[roomId] ?: throw IllegalArgumentException("房间不存在")
        return session.eventChannel.asSharedFlow().filter {
            when (it) {
                is RoomEvent.SignalReceived -> it.message.targetPlayerUuid == playerId
                else -> true
            }
        }
    }

    override suspend fun sendSignal(roomId: String, message: SignalingMessage) {
        val session = rooms[roomId] ?: return
        session.eventChannel.emit(RoomEvent.SignalReceived(message))
    }

    private fun generateRoomId(): String = (10000..99999).random().toString()
}