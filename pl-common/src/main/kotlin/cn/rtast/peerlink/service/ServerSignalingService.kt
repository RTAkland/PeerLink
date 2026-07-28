/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.service

import cn.rtast.peerlink.data.ICEServerConfig
import cn.rtast.peerlink.data.ServerInfo
import kotlinx.rpc.annotations.Rpc

@Rpc
interface ServerSignalingService {
    suspend fun serverInfo(): ServerInfo
    suspend fun acquireICEServerConfig(): ICEServerConfig
}