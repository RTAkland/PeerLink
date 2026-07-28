/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.server

import io.ktor.client.*
import io.ktor.client.engine.cio.*

val httpClient = HttpClient(CIO)

const val CLOUDFLARE_TURN_TOKEN_ID = "2b85e24602215148062181b7023779dd"
const val CLOUDFLARE_TURN_TOKEN_KEY = "9dc542fa37d8c0126047460e467ceb3c24d2416c634ef271e6fe6990daefa20f"
const val SIGNALING_SERVER_VERSION = "0.0.1-INTERNAL"