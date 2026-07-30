/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.signaling.data

import cn.rtast.peerlink.data.play.PlayerInfo
import io.ktor.util.*

class ServiceContext(val attributes: Attributes = Attributes(true)) {
    companion object {
        private val PLAYER_KEY = AttributeKey<PlayerInfo>("PlayerIdentity")
    }

    fun bindPlayer(player: PlayerInfo) {
        if (attributes.contains(PLAYER_KEY)) {
            val existing = attributes[PLAYER_KEY]
            throw IllegalStateException("Already registered identity: ${existing.username}")
        }
        attributes.put(PLAYER_KEY, player)
    }

    fun getPlayer(): PlayerInfo? {
        return attributes.getOrNull(PLAYER_KEY)
    }

    fun requirePlayer(): PlayerInfo {
        return getPlayer() ?: throw IllegalStateException("Identity not registered, call registerIdentity first")
    }
}