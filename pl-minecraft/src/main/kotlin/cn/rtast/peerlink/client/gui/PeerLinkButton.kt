/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */

@file:JvmName("PeerLinkButtons")

package cn.rtast.peerlink.client.gui

import cn.rtast.peerlink.client.minecraft
import net.minecraft.client.gui.components.SpriteIconButton
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

fun peerlinkEntrypointButton(targetScreen: Screen, x: Int = -1, y: Int = -1) =
    SpriteIconButton.builder(Component.translatable("peerlink.entrypoint.button"), {
        minecraft.gui.setScreen(targetScreen)
    }, true).width(20).sprite(Identifier.fromNamespaceAndPath("peerlink", "icon/peerlink_button"), 16, 16)
        .tooltip(Component.translatable("peerlink.entrypoint.button"))
        .build().apply { if (x != -1) this.x = x; if (y != -1) this.y = y }
