/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.signaling

import cn.rtast.peerlink.signaling.kv.CloudflareKvRepositoryImpl
import cn.rtast.peerlink.signaling.routes.registerRpcRouting
import cn.rtast.peerlink.signaling.routes.service.registerIndexRouting
import cn.rtast.peerlink.signaling.util.CLOUDFLARE_ACCOUNT_ID
import cn.rtast.peerlink.signaling.util.CLOUDFLARE_KV
import cn.rtast.peerlink.signaling.util.CLOUDFLARE_TOKEN
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

private val cloudflareKvRepository = CloudflareKvRepositoryImpl(
    CLOUDFLARE_ACCOUNT_ID!!, CLOUDFLARE_KV!!, CLOUDFLARE_TOKEN!!
)

fun main() {
    embeddedServer(CIO, port = 7879, module = Application::module).start(true)
}

fun Application.module() {
    registerRpcRouting(cloudflareKvRepository)
    registerIndexRouting()
}