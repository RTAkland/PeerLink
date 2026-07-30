/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.service

import cn.rtast.peerlink.data.play.RoomState
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Rpc
interface MinecraftSignalingService {
    /**
     * 监听服务端事件
     */
    fun observeEvents(): Flow<SignalEvent>

    /**
     * 创建房间
     */
    suspend fun createRoom(): RoomState

    /**
     * 加入房间
     */
    suspend fun joinRoom(roomId: String): RoomState?

    /**
     * 离开房间
     */
    suspend fun leaveRoom()

    /**
     * 发送信令
     */
    suspend fun sendSignal(targetPlayerId: Uuid, message: SignalingMessage)

    /**
     * 心跳包
     */
    suspend fun sendHeartbeat(clientTimestamp: Long = Clock.System.now().toEpochMilliseconds()): Long
}