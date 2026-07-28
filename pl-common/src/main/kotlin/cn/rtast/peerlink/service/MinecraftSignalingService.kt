/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.service

import cn.rtast.peerlink.data.PlayerInfo
import cn.rtast.peerlink.data.RoomEvent
import cn.rtast.peerlink.data.RoomState
import cn.rtast.peerlink.data.SignalingMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc
import kotlin.uuid.Uuid

@Rpc
interface MinecraftSignalingService {
    suspend fun createRoom(hostPlayer: PlayerInfo): RoomState
    suspend fun joinRoom(roomId: String, player: PlayerInfo): RoomState
    suspend fun leaveRoom(roomId: String, playerId: Uuid)

    fun subscribeRoomEvents(roomId: String, playerId: Uuid): Flow<RoomEvent>
    suspend fun sendSignal(roomId: String, message: SignalingMessage)
}