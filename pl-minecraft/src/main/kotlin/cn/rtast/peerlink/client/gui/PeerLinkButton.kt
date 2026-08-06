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

@JvmOverloads
fun peerlinkIconButton(
    targetScreen: Screen,
    component: Component,
    icon: Identifier,
    x: Int = -1, y: Int = -1,
    width: Int = 16, height: Int = 16,
) = SpriteIconButton.builder(component, {
    minecraft.execute { minecraft.setScreen(targetScreen) }
}, true).width(20).sprite(icon, width, height).withTootip().build()
    .apply { if (x != -1) this.x = x; if (y != -1) this.y = y }