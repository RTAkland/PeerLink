/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.signaling

import cn.rtast.peerlink.signaling.config.Config.Companion.readConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*

val httpClient = HttpClient(CIO)

val CLOUDFLARE_TURN_TOKEN_ID = readConfig().cloudflareTurnTokenId
val CLOUDFLARE_TURN_TOKEN_KEY = readConfig().cloudflareTurnTokenKey
const val SIGNALING_SERVER_VERSION = "0.1-INTERNAL-TEST"