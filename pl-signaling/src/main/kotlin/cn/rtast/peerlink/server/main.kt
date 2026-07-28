/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.server

import cn.rtast.peerlink.server.routes.registerRpcRouting
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 7879, module = Application::module)
        .start(true)
}

fun Application.module() {
    registerRpcRouting()
}