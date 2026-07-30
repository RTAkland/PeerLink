/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.signaling

import cn.rtast.peerlink.signaling.util.getenv
import io.ktor.client.*

val httpClient = HttpClient {}

val CLOUDFLARE_TURN_TOKEN_ID = getenv("CLOUDFLARE_TURN_TOKEN_ID")!!
val CLOUDFLARE_TURN_TOKEN_KEY = getenv("CLOUDFLARE_TURN_TOKEN_KEY")!!
const val SIGNALING_SERVER_VERSION = "0.1-INTERNAL-TEST"