/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.service

import cn.rtast.peerlink.data.ServerInfo
import kotlinx.rpc.annotations.Rpc

@Rpc
interface ServerSignalingService {
    /**
     * 获取信令服务器信息哦
     */
    suspend fun serverInfo(): ServerInfo
}