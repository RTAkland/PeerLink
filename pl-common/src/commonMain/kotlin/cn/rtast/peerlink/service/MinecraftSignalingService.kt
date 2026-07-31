/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.service

import cn.rtast.peerlink.data.play.JoinResponse
import cn.rtast.peerlink.data.play.RoomState
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc
import kotlin.uuid.Uuid

@Rpc
interface MinecraftSignalingService {
    /**
     * 监听信令事件流
     */
    fun observeEvents(): Flow<SignalEvent>

    /**
     * 发送心跳包
     */
    suspend fun sendHeartbeat(clientTimestamp: Long): Long

    /**
     * 创建房间
     */
    suspend fun createRoom(): RoomState

    /**
     * 发送进房请求
     */
    suspend fun joinRoom(roomId: String): JoinResponse

    /**
     * 响应进房请求
     */
    suspend fun respondJoinRequest(applicantId: Uuid, accept: Boolean, reason: String? = null)

    /**
     * 离开房间
     */
    suspend fun leaveRoom()

    /**
     * 踢出玩家
     */
    suspend fun kickPlayer(targetPlayerId: Uuid, reason: String? = null)

    /**
     * 发送信令
     */
    suspend fun sendSignal(targetPlayerId: Uuid, message: SignalingMessage)

    /**
     * 获取房间信息
     */
    suspend fun getRoomState(): RoomState
}