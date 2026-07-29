/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.server.routes

import cn.rtast.peerlink.server.data.ServiceContext
import cn.rtast.peerlink.server.service.AuthServiceImpl
import cn.rtast.peerlink.server.service.MinecraftSignalingServiceImpl
import cn.rtast.peerlink.server.service.ServerSignalingServiceImpl
import cn.rtast.peerlink.service.AuthService
import cn.rtast.peerlink.service.MinecraftSignalingService
import cn.rtast.peerlink.service.ServerSignalingService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json

fun Application.registerRpcRouting() {
    routing {
        install(Krpc)
        rpc("/rpc") {
            rpcConfig { serialization { json() } }

            val connectionContext = ServiceContext()

            registerService<AuthService> {
                AuthServiceImpl(connectionContext) { player ->
                    MinecraftSignalingServiceImpl.getOrCreatePlayerFlow(player.uuid)
                }
            }

            registerService<MinecraftSignalingService> { MinecraftSignalingServiceImpl(connectionContext) }
            registerService<ServerSignalingService> { ServerSignalingServiceImpl(connectionContext) }
        }
    }
}