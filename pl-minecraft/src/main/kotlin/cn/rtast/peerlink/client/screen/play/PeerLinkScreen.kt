/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.client.screen.play

import cn.rtast.peerlink.client.PeerLink
import cn.rtast.peerlink.client.data.ConnectResult
import cn.rtast.peerlink.client.util.showNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.client.gui.GuiGraphics
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
        private val roomIdComponent = Component.translatable("peerlink.sessionid")
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
            Button.builder(Component.translatable("peerlink.joinSession")) { joinRoom() }
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
        this.minecraft.setScreen(this.parent)
    }

    private fun updateSelectButtonStatus() {
        val isRpcConnected = PeerLink.rpcClient?.isConnected == true
        this.selectButton?.active = isRpcConnected && this.roomIdEdit!!.value.isNotBlank()
    }

    private fun joinRoom() {
        val roomId = roomIdEdit!!.value.trim()
        if (roomId.isBlank()) return
        val manager = PeerLink.manager ?: return
        val rpcClient = PeerLink.rpcClient
        if (rpcClient?.isConnected != true) {
            showNotification(
                Component.translatable("peerlink.joinFailed"),
                Component.translatable("peerlink.signalingServerNotConnected")
            )
            return
        }
        this.selectButton?.active = false
        try {
            minecraft.setScreen(
                PeerLinkConnectingScreen(
                    this, Component.translatable("peerlink.signaling.waitingResponse"),
                    { screen ->
                        this.updateSelectButtonStatus()
                        screen.hidePlayerHead()
                    }
                ) { screen ->
                    manager.connect(roomId) { result ->
                        when (result) {
                            is ConnectResult.Awaiting -> {
                                screen.showPlayerHead(result.host)
                                screen.updateTitle(Component.translatable("peerlink.waitingForHostApproval"))
                                screen.startAwaitingCountdown(30)
                            }

                            is ConnectResult.Rejected -> {
                                screen.updateTitle(Component.translatable("peerlink.hostRejectedJoinRequest"))
                                screen.stopAwaitingCountdown()
                            }

                            is ConnectResult.Accepted -> {
                                screen.updateTitle(Component.translatable("peerlink.p2p.connecting"))
                                screen.stopAwaitingCountdown()
                            }

                            is ConnectResult.Invalid -> screen.updateTitle(Component.translatable("peerlink.signaling.invalidSessionId"))
                            is ConnectResult.SignalingError, ConnectResult.Failed -> screen.updateTitle(
                                Component.translatable("peerlink.signalingRespondError")
                            )
                        }
                    }
                }
            )
        } catch (e: Exception) {
            this.updateSelectButtonStatus()
            throw e
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, a: Float) {
        super.render(graphics, mouseX, mouseY, a)
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, -1)
        graphics.drawString(this.font, roomIdComponent, this.width / 2 - 100 + 1, 100, -6250336)
        this.roomIdEdit!!.render(graphics, mouseX, mouseY, a)
    }
}