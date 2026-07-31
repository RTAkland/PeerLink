/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.client.screen

import cn.rtast.peerlink.client.PeerLinkInitializer
import cn.rtast.peerlink.client.util.showNotification
import cn.rtast.peerlink.data.play.JoinResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class PeerLinkScreen(private val parent: Screen) : Screen(Component.translatable("peerlink.joinGameViaWebRTC")) {
    private var selectButton: Button? = null
    private var roomIdEdit: EditBox? = null
    private val screenScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private val roomIdComponent = Component.translatable("peerlink.roomid")
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (this.selectButton?.active == true && this.focused === this.roomIdEdit && event.isConfirmation) {
            joinRoom()
            return true
        } else return super.keyPressed(event)
    }

    override fun init() {
        this.roomIdEdit = EditBox(this.font, this.width / 2 - 100, 116, 200, 20, roomIdComponent)
        this.roomIdEdit!!.setMaxLength(128)
        this.roomIdEdit!!.setResponder { this.updateSelectButtonStatus() }
        this.addWidget(this.roomIdEdit!!)
        this.selectButton = this.addRenderableWidget(
            Button.builder(Component.translatable("peerlink.joinRoom")) { joinRoom() }
                .bounds(this.width / 2 - 100, this.height / 4 + 96 + 12, 200, 20).build()
        )
        this.addRenderableWidget(
            Button.builder(
                CommonComponents.GUI_CANCEL
            ) { minecraft.setScreenAndShow(parent) }
                .bounds(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20).build()
        )
        this.updateSelectButtonStatus()
    }

    override fun setInitialFocus() {
        this.setInitialFocus(this.roomIdEdit!!)
    }

    override fun resize(width: Int, height: Int) {
        val oldEdit = this.roomIdEdit!!.value
        this.init(width, height)
        this.roomIdEdit!!.value = oldEdit
    }

    override fun onClose() {
        this.minecraft.gui.setScreen(this.parent)
    }

    private fun updateSelectButtonStatus() {
        val isRpcConnected = PeerLinkInitializer.rpcClient?.isConnected == true
        this.selectButton?.active = isRpcConnected && this.roomIdEdit!!.value.isNotBlank()
    }

    private fun joinRoom() {
        val roomId = roomIdEdit!!.value.trim()
        if (roomId.isBlank()) return
        val manager = PeerLinkInitializer.manager ?: return
        val rpcClient = PeerLinkInitializer.rpcClient
        if (rpcClient?.isConnected != true) {
            showNotification(
                Component.translatable("peerlink.joinFailed"),
                Component.translatable("peerlink.signalingServerNotConnected")
            )
            return
        }
        this.selectButton?.active = false
        try {
            minecraft.gui.setScreen(
                PeerLinkConnectingScreen(
                    this, Component.translatable("peerlink.signaling.waitingResponse"),
                    { this.updateSelectButtonStatus() }
                ) { screen ->
                    screenScope.launch {
                        try {
                            manager.joinRoom(roomId, { response ->
                                minecraft.execute {
                                    when (response) {
                                        is JoinResponse.Rejected -> screen.updateTitle(
                                            Component.translatable("peerlink.hostRejectedJoinRequest")
                                                .append(" ${response.reason}")
                                        )

                                        is JoinResponse.Accepted -> screen.updateTitle(Component.translatable("peerlink.p2p.connecting"))
                                        is JoinResponse.InvalidRoom -> screen.updateTitle(Component.translatable("peerlink.signaling.invalidRoomId"))
                                        is JoinResponse.Error -> screen.updateTitle(
                                            Component.translatable("peerlink.signalingRespondError")
                                                .append(" ${response.message}")
                                        )
                                    }
                                }
                            }) { minecraft.execute { screen.updateTitle(Component.translatable("peerlink.waitingForHostApproval")) } }
                        } catch (e: Exception) {
                            minecraft.execute { screen.updateTitle(Component.literal(e.message ?: "Connection Error")) }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            this.updateSelectButtonStatus()
            throw e
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
        graphics.centeredText(this.font, this.title, this.width / 2, 20, -1)
        graphics.text(this.font, roomIdComponent, this.width / 2 - 100 + 1, 100, -6250336)
        this.roomIdEdit!!.extractRenderState(graphics, mouseX, mouseY, a)
    }
}