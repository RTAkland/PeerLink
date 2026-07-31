/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.signaling.service

import cn.rtast.peerlink.data.ServerInfo
import cn.rtast.peerlink.service.ServerSignalingService
import cn.rtast.peerlink.signaling.SIGNALING_SERVER_VERSION
import cn.rtast.peerlink.signaling.data.ServiceContext

class ServerSignalingServiceImpl(
    private val context: ServiceContext,
) : ServerSignalingService {
    override suspend fun serverInfo(): ServerInfo = ServerInfo(SIGNALING_SERVER_VERSION)
}