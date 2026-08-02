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
            throw IllegalStateException("Already registered identity: ${existing.name}")
        }
        attributes.put(PLAYER_KEY, player)
    }

    fun getPlayer(): PlayerInfo = attributes.getOrNull(PLAYER_KEY)
        ?: throw IllegalStateException("Identity not registered, call registerIdentity first")
}