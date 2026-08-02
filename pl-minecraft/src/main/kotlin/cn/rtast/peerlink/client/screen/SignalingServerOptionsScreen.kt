/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/3
 */


package cn.rtast.peerlink.client.screen

import cn.rtast.peerlink.client.PeerLink
import cn.rtast.peerlink.client.data.PeerLinkClientConfig
import cn.rtast.peerlink.client.defaultConfig
import cn.rtast.peerlink.client.util.asTooltip
import cn.rtast.peerlink.util.encodeJson
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import kotlin.io.path.writeText

class SignalingServerOptionsScreen(
    private val parent: Screen,
) : Screen(Component.translatable("peerlink.options")) {
    private val layout = LinearLayout.vertical().spacing(8)
    private lateinit var addressEditBox: EditBox

    override fun init() {
        this.layout.defaultCellSetting().alignHorizontallyCenter()
        this.layout.addChild(StringWidget(this.title, this.font))
        val labelWidget = StringWidget(Component.translatable("peerlink.options.signalingServer"), this.font)
        this.layout.addChild(labelWidget) { settings -> settings.paddingTop(10) }

        this.addressEditBox = EditBox(
            this.font, 260, 20,
            Component.translatable("peerlink.options.signalingServer")
        ).apply {
            maxLength = 256  // Accessed via access widener
            value = PeerLink.config.signalingServer
        }
        this.layout.addChild(this.addressEditBox)
        val buttonLayout = LinearLayout.horizontal().spacing(8)
        val defaultButton = Button.builder(Component.translatable("peerlink.options.resetDefault")) {
            this.addressEditBox.value = defaultConfig.signalingServer
        }.width(80).build()
        val cancelButton = Button.builder(CommonComponents.GUI_CANCEL) {
            this.onClose()
        }.width(80).build()
        val saveButton = Button.builder(Component.translatable("peerlink.options.save")) {
            this.saveAndClose()
        }.width(80).tooltip(
            Component.translatable("peerlink.options.saveNeedsRestart")
                .setStyle(Style.EMPTY.withColor(TextColor.RED)).asTooltip()
        ).build()

        buttonLayout.addChild(defaultButton)
        buttonLayout.addChild(cancelButton)
        buttonLayout.addChild(saveButton)

        this.layout.addChild(buttonLayout) { settings -> settings.paddingTop(15) }
        this.repositionElements()
    }

    override fun repositionElements() {
        this.clearWidgets()
        this.layout.arrangeElements()
        this.layout.visitWidgets { this.addRenderableWidget(it) }
        FrameLayout.centerInRectangle(this.layout, this.rectangle)
    }

    private fun saveAndClose() {
        val signalingServer = this.addressEditBox.value.trim()
        val config = PeerLinkClientConfig(
            signalingServer = signalingServer
        ).encodeJson()
        PeerLink.configFile.writeText(config)
        this.onClose()
    }

    override fun onClose() {
        this.minecraft.gui.setScreen(this.parent)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isConfirmation) {
            this.saveAndClose()
            return true
        }
        return super.keyPressed(event)
    }
}