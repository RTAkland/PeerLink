/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client

import cn.rtast.peerlink.client.util.RpcManager
import cn.rtast.peerlink.client.webrtc.guest.WebRTCClientManager
import cn.rtast.peerlink.client.webrtc.guest.WebRTCJoinManager
import cn.rtast.peerlink.client.webrtc.host.WebRTCHostManager
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component


class PeerLinkEntrypoint : ModInitializer {
    override fun onInitialize() {
        RpcManager.start("ws://127.0.0.1:7879/rpc")

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("pl").then(
                    Commands.literal("create").executes { ctx ->
                        WebRTCHostManager.openWebRTCRoom(
                            RpcManager.scope,
                            RpcManager.minecraftSignalingService!!,
                            RpcManager.serverSignalingService!!,
                        ) { res ->
                            ctx.source.sendSystemMessage(Component.literal(res.getOrThrow().roomId))
                        }
                        0
                    }
                ).then(
                    Commands.literal("join").then(
                        Commands.argument("roomId", StringArgumentType.string()).executes { ctx ->
                            val roomId = ctx.getArgument("roomId", String::class.java)
                            WebRTCJoinManager.joinRoom(roomId)
                            0
                        }
                    )
                )
            )
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register { _ ->
            logger.debug("清理RPC连接")
            RpcManager.stop()
            WebRTCHostManager.stopHosting()
            WebRTCClientManager.reset()
        }
    }
}