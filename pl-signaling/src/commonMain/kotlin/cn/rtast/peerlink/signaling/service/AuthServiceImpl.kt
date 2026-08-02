/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.signaling.service

import cn.rtast.klogging.KLogging
import cn.rtast.peerlink.data.play.PlayerInfo
import cn.rtast.peerlink.signaling.data.ServiceContext
import cn.rtast.peerlink.service.AuthService

class AuthServiceImpl(
    private val context: ServiceContext,
    private val onPlayerRegistered: suspend (PlayerInfo) -> Unit = {},
): AuthService {
    private val logger = KLogging.getLogger("Signaling Server | Auth")

    override suspend fun registerIdentity(player: PlayerInfo) {
        context.bindPlayer(player)
        onPlayerRegistered(player)
        logger.info("Player identity registered: ${player.name} (${player.uuid})")
    }
}