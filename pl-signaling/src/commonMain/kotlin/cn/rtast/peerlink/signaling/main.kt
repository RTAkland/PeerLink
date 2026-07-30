/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.signaling

import cn.rtast.peerlink.signaling.routes.registerRpcRouting
import cn.rtast.peerlink.signaling.routes.service.registerIndexRouting
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

fun main() {
    embeddedServer(CIO, port = 7879, module = Application::module).start(true)
}

fun Application.module() {
    registerRpcRouting()
    registerIndexRouting()
}