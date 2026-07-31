/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/30
 */


package cn.rtast.peerlink.client.screen

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.LoadingDotsWidget
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import kotlin.concurrent.thread

class PeerLinkConnectingScreen(
    private val lastScreen: Screen,
    private var initialTitle: Component,
    private val onCancel: (screen: PeerLinkConnectingScreen) -> Unit,
    private val onConnectTask: (screen: PeerLinkConnectingScreen) -> Unit,
) : Screen(initialTitle) {

    private val layout = LinearLayout.vertical()
    private var loadingDotsWidget: LoadingDotsWidget? = null

    @Volatile
    private var isAborted = false

    override fun init() {
        this.layout.defaultCellSetting().alignHorizontallyCenter()
        val dotsWidget = LoadingDotsWidget(this.font, this.initialTitle)
        this.loadingDotsWidget = dotsWidget
        this.layout.addChild(dotsWidget) { settings -> settings.paddingTop(20).paddingBottom(30) }
        val cancelButton = Button.builder(CommonComponents.GUI_CANCEL) { this.cancel() }
            .width(200).build()
        this.layout.addChild(cancelButton)
        this.layout.visitWidgets { this.addRenderableWidget(it) }
        this.repositionElements()
        thread(isDaemon = true, name = "PeerLink-Connect-Thread") {
            try {
                if (!isAborted) onConnectTask(this)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun repositionElements() {
        this.layout.arrangeElements()
        FrameLayout.centerInRectangle(this.layout, this.rectangle)
    }

    fun updateTitle(newTitle: Component) {
        this.minecraft.execute {
            this.loadingDotsWidget?.message = newTitle
            this.initialTitle = newTitle
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) {
            this.cancel()
            return true
        }
        return super.keyPressed(event)
    }

    private fun cancel() {
        this.isAborted = true
        this.minecraft.gui.setScreen(this.lastScreen)
        onCancel(this)
    }
}