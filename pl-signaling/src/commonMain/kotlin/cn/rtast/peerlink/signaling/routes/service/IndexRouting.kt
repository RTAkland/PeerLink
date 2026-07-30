/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/30
 */


package cn.rtast.peerlink.signaling.routes.service

import cn.rtast.peerlink.signaling.SIGNALING_SERVER_VERSION
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.registerIndexRouting() {
    routing {
        get("/") {
            call.respond("PeerLink Signaling Server -> $SIGNALING_SERVER_VERSION")
        }
    }
}