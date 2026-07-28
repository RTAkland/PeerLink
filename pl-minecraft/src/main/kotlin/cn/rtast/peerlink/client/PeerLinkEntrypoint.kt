/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client

import cn.rtast.peerlink.client.screen.PeerLinkScreen
import cn.rtast.peerlink.client.util.RpcManager
import cn.rtast.peerlink.client.webrtc.guest.WebRTCJoinManager
import cn.rtast.peerlink.client.webrtc.host.WebRTCHostManager
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.TitleScreen
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

        ScreenEvents.AFTER_INIT.register(AfterInit { client, screen, scaledWidth, scaledHeight ->
            if (screen is TitleScreen) {
                var mpBtn: Button? = null
                for (widget in Screens.getWidgets(screen)) {
                    if (widget.getMessage().string.contains("Multiplayer")) {
                        mpBtn = widget as Button
                        break
                    }
                }
                if (mpBtn != null) {
                    val smallBtn = Button.builder(Component.literal("⚙")) {
                        minecraft.setScreenAndShow(PeerLinkScreen())
                    }.bounds(mpBtn.x + mpBtn.getWidth() + 4, mpBtn.y, 20, 20).build()
                    Screens.getWidgets(screen).add(smallBtn)
                }
            }
        })

        ClientLifecycleEvents.CLIENT_STOPPING.register { _ ->
            logger.debug("清理RPC连接")
            RpcManager.stop()
        }
    }
}