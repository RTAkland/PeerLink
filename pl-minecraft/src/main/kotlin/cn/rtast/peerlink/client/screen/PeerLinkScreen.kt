/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.screen

import cn.rtast.peerlink.client.webrtc.guest.WebRTCJoinManager
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component


class PeerLinkScreen : Screen(Component.literal("PeerLink")) {
    private var inputField: EditBox? = null

    override fun init() {
        inputField = EditBox(this.font, this.width / 2 - 100, this.height / 2 - 20, 200, 20, Component.literal("输入"))
        this.addRenderableWidget(inputField!!)
        this.addRenderableWidget(Button.builder(Component.literal("连接")) {
            val text = inputField!!.value
            WebRTCJoinManager.joinRoom(text)
        }.bounds(this.width / 2 - 100, this.height / 2 + 10, 200, 20).build())
    }
}