/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/30
 */


package cn.rtast.peerlink.client.screen.play

import com.mojang.authlib.GameProfile
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.LoadingDotsWidget
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import java.util.*
import java.util.concurrent.*
import kotlin.concurrent.thread
import kotlin.uuid.Uuid

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

    @Volatile
    private var showHead = false
    private var currentProfile: GameProfile? = null
    private var playerSkinFuture: CompletableFuture<PlayerSkin>? = null

    private var countdownScheduler: ScheduledExecutorService? = null
    private var countdownFuture: ScheduledFuture<*>? = null

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

    fun startAwaitingCountdown(seconds: Int, onTimeout: () -> Unit = {}) {
        stopAwaitingCountdown()
        var remaining = seconds
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        this.countdownScheduler = scheduler
        this.countdownFuture = scheduler.scheduleAtFixedRate({
            if (isAborted) {
                stopAwaitingCountdown()
                return@scheduleAtFixedRate
            }
            if (remaining >= 0) {
                val formattedTitle = Component.empty()
                    .append(this.initialTitle)
                    .append(" ($remaining)")
                this.loadingDotsWidget?.message = formattedTitle
                remaining--
            } else {
                stopAwaitingCountdown()
                onTimeout.invoke()
            }
        }, 0, 1, TimeUnit.SECONDS)
    }

    fun stopAwaitingCountdown() {
        this.countdownFuture?.cancel(true)
        this.countdownScheduler?.shutdownNow()
        this.countdownFuture = null
        this.countdownScheduler = null
    }

    fun showPlayerHead(profile: GameProfile) = this.minecraft?.execute {
        this.currentProfile = profile
        this.showHead = true
        val skinManager = this.minecraft!!.skinManager
        this.playerSkinFuture = skinManager.getOrLoad(profile)
    }

    fun showPlayerHead(uuid: Uuid, name: String? = null) =
        showPlayerHead(GameProfile(UUID.fromString(uuid.toString()), name ?: "Player"))

    fun hidePlayerHead() = this.minecraft?.execute {
        this.showHead = false
        this.currentProfile = null
        this.playerSkinFuture = null
    }

    fun updateTitle(newTitle: Component) {
        this.minecraft?.execute {
            this.loadingDotsWidget?.message = newTitle
            this.initialTitle = newTitle
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, a: Float) {
        super.render(graphics, mouseX, mouseY, a)
        if (this.showHead && this.loadingDotsWidget != null && this.currentProfile != null) {
            val dots = this.loadingDotsWidget!!
            val headSize = 32
            val headX = (this.width - headSize) / 2
            val headY = dots.y - headSize - 8
            val profile = this.currentProfile!!
            val skin = this.playerSkinFuture?.getNow(null) ?: DefaultPlayerSkin.get(profile.id)
            PlayerFaceRenderer.draw(
                graphics, skin.texture, headX, headY,
                headSize, true, false,
            )
        }
    }

    override fun keyPressed(i: Int, j: Int, k: Int): Boolean {
        if (i == 256) {
            this.cancel()
            return true
        }
        return super.keyPressed(i, j, k)
    }

    private fun cancel() {
        this.isAborted = true
        this.minecraft?.setScreen(this.lastScreen)
        onCancel(this)
    }
}