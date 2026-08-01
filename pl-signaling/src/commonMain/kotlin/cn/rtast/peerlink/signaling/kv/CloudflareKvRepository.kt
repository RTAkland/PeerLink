/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.signaling.kv

import cn.rtast.peerlink.data.play.PlayerInfo
import cn.rtast.peerlink.data.play.RoomState
import kotlin.uuid.Uuid

interface CloudflareKvRepository {
    suspend fun getRoomState(roomId: String): RoomState?
    suspend fun getRoomHost(roomId: String): Uuid?
    suspend fun setRoomHost(roomId: String, hostId: Uuid)
    suspend fun getPlayerRoom(playerId: Uuid): String?
    suspend fun setPlayerRoom(playerId: Uuid, roomId: String)
    suspend fun removePlayerRoom(playerId: Uuid)
    suspend fun addRoomPlayer(roomId: String, player: PlayerInfo)
    suspend fun removeRoomPlayer(roomId: String, playerId: Uuid)
    suspend fun getRoomPlayers(roomId: String): List<PlayerInfo>
    suspend fun deleteRoom(roomId: String)
    suspend fun roomExists(roomId: String): Boolean
}