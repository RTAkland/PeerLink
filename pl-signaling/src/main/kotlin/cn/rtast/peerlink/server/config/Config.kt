/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.server.config

import cn.rtast.peerlink.util.fromJson
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Config(
    val cloudflareTurnTokenId: String,
    val cloudflareTurnTokenKey: String,
) {
    companion object {
        private val file = File("./config.json")

        fun readConfig(): Config = if (!file.exists()) {
            Config(
                System.getenv("CLOUDFLARE_TURN_TOKEN_ID"),
                System.getenv("CLOUDFLARE_TURN_TOKEN_KEY")
            )
        } else file.readText().fromJson()
    }
}