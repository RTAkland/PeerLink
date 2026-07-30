/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.signaling.config

import cn.rtast.peerlink.signaling.util.getenv
import cn.rtast.peerlink.util.fromJson
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val cloudflareTurnTokenId: String,
    val cloudflareTurnTokenKey: String,
) {
    companion object {
        private val file = Path("./config.json")

        fun readConfig(): Config = if (!SystemFileSystem.exists(file)) {
            Config(
                getenv("CLOUDFLARE_TURN_TOKEN_ID")!!,
                getenv("CLOUDFLARE_TURN_TOKEN_KEY")!!
            )
        } else SystemFileSystem.source(file).buffered().use { it.readString().fromJson() }
    }
}