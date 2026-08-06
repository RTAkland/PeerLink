/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */

package cn.rtast.peerlink.client.screen.play

import cn.rtast.peerlink.client.PeerLink
import cn.rtast.peerlink.client.data.PendingJoinRequest
import cn.rtast.peerlink.client.util.asTooltip
import com.mojang.authlib.GameProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.*
import java.util.concurrent.CompletableFuture

class PendingJoinRequestsScreen(private val lastScreen: Screen) :
    Screen(Component.translatable("peerlink.pendingJoinRequests")) {
    private val layout = HeaderAndFooterLayout(this)
    private var pendingRequestSelectionList: PendingRequestSelectionList? = null
    private val screenScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null

    override fun init() {
        this.layout.addTitleHeader(this.title, this.font)
        this.pendingRequestSelectionList =
            PendingRequestSelectionList(this.minecraft!!).also { this.layout.addToContents(it) }
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE) { this.onClose() }.width(200).build())
        this.layout.visitWidgets { this.addRenderableWidget(it) }
        this.repositionElements()
        this.startPollingRequests()
    }

    private fun startPollingRequests() {
        this.pollJob?.cancel()
        this.pollJob = PeerLink.manager!!.pendingRequestsFlow.onEach { requests ->
            if (requests.isNotEmpty()) updateRequestsUI(requests)
        }.launchIn(screenScope)
    }

    private fun updateRequestsUI(requests: List<PendingJoinRequest>) = minecraft?.execute {
        val selectionList = this.pendingRequestSelectionList ?: return@execute
        if (requests.isEmpty()) {
            selectionList.children().clear()
            selectionList.replaceEntries(emptyList())
            return@execute
        }
        val currentEntries = selectionList.children().associateBy { it.request.applicantId }
        val updatedEntries = requests.map { request -> currentEntries[request.applicantId] ?: _Entry(request) }
        selectionList.replaceEntries(updatedEntries)
    }

    override fun repositionElements() {
        this.layout.arrangeElements()
        this.pendingRequestSelectionList?.updateSize(this.width, this.layout)
    }

    override fun onClose() {
        this.screenScope.cancel()
        this.minecraft?.setScreen(this.lastScreen)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, a: Float) {
        super.render(graphics, mouseX, mouseY, a)
        if (this.pendingRequestSelectionList?.hasNoPendingRequests() == true) {
            graphics.drawCenteredString(this.font, NO_PENDING_REQUESTS_TEXT, this.width / 2, this.height / 2 - 20, -1)
        }
    }

    private inner class PendingRequestSelectionList(minecraft: Minecraft) : ContainerObjectSelectionList<_Entry>(
        minecraft,
        this@PendingJoinRequestsScreen.width,
        this@PendingJoinRequestsScreen.layout.contentHeight,
        this@PendingJoinRequestsScreen.layout.headerHeight,
        36
    ) {
        override fun getRowWidth(): Int = 280
        fun hasNoPendingRequests(): Boolean = this.itemCount == 0
        fun removeRequest(entry: _Entry) {
            this.removeEntry(entry)
        }
    }

    @Suppress("CLASSNAME")
    private inner class _Entry(val request: PendingJoinRequest) : ContainerObjectSelectionList.Entry<_Entry>() {
        private val childrenList = mutableListOf<AbstractWidget>()
        private val acceptButton: SpriteIconButton
        private val rejectButton: SpriteIconButton
        private val applicantNameWidget: StringWidget
        private var playerSkinFuture: CompletableFuture<PlayerSkin>? = null
        private var isProcessing = false

        init {
            val listWidth = this@PendingJoinRequestsScreen.pendingRequestSelectionList?.rowWidth ?: 280
            val maxTextWidth = listWidth - 32 - 20 - 60
            val uuidTooltip = Component.literal("UUID: ${request.applicantId}").asTooltip()
            this.applicantNameWidget = StringWidget(
                Component.literal(request.applicantName),
                this@PendingJoinRequestsScreen.font
            ).also {
                it.width = maxTextWidth
                it.tooltip = uuidTooltip
            }
            this.acceptButton = SpriteIconButton.builder(ACCEPT_REQUEST, { this.handleRequest(true) }, true)
                .sprite(ACCEPT_SPRITE, 18, 18).size(21, 21).build()
            this.rejectButton = SpriteIconButton.builder(REJECT_REQUEST, { this.handleRequest(false) }, true)
                .sprite(REJECT_SPRITE, 18, 18).size(21, 21).build()
            this.childrenList.addAll(listOf(this.applicantNameWidget, this.acceptButton, this.rejectButton))
            this.loadPlayerSkin()
        }

        private fun loadPlayerSkin() {
            val javaUuid = UUID.fromString(request.applicantId.toString())
            val profile = GameProfile(javaUuid, request.applicantName)
            val skinManager = this@PendingJoinRequestsScreen.minecraft!!.skinManager
            this.playerSkinFuture = skinManager.getOrLoad(profile)
        }

        override fun children(): List<GuiEventListener> = this.childrenList
        override fun narratables(): List<NarratableEntry> = this.childrenList

        // name from mappings.dev
        override fun render(
            guiGraphics: GuiGraphics,
            index: Int,
            y: Int,
            x: Int,
            entryWidth: Int,
            entryHeight: Int,
            mouseX: Int,
            mouseY: Int,
            hoverd: Boolean,
            delta: Float,
        ) {
            val headSize = 20
            val headX = x + 8
            val headY = y + (entryHeight - headSize) / 2
            UUID.fromString(request.applicantId.toString()).let { javaUuid ->
                val skin = this.playerSkinFuture?.getNow(null) ?: DefaultPlayerSkin.get(javaUuid)
                PlayerFaceRenderer.draw(guiGraphics, skin, headX, headY, headSize)
            }
            val textX = headX + headSize + 8
            val textY = y + (entryHeight - this@PendingJoinRequestsScreen.font.lineHeight) / 2
            this.applicantNameWidget.setPosition(textX, textY)
            this.applicantNameWidget.renderWidget(guiGraphics, mouseX, mouseY, x.toFloat())
            val buttonY = y + entryHeight / 2 - 10
            this.acceptButton.setPosition(x + entryHeight - 16 - 42, buttonY)
            this.acceptButton.render(guiGraphics, mouseX, mouseY, delta)
            this.rejectButton.setPosition(x + entryHeight - 8 - 21, buttonY)
            this.rejectButton.render(guiGraphics, mouseX, mouseY, delta)
        }

        private fun handleRequest(accept: Boolean) {
            if (this.isProcessing) return
            this.isProcessing = true
            this.acceptButton.active = false
            this.rejectButton.active = false
            if (accept) PeerLink.manager!!.acceptJoinRequest(request.applicantId)
            else PeerLink.manager!!.rejectJoinRequest(request.applicantId)
            this@PendingJoinRequestsScreen.pendingRequestSelectionList?.removeRequest(this)
        }
    }

    private companion object {
        private val NO_PENDING_REQUESTS_TEXT = Component.translatable("peerlink.noPendingRequests")
        private val ACCEPT_REQUEST = Component.translatable("peerlink.accept")
        private val REJECT_REQUEST = Component.translatable("peerlink.reject")
        private val ACCEPT_SPRITE = ResourceLocation("peerlink", "icon/management/accept")
        private val REJECT_SPRITE = ResourceLocation("peerlink", "icon/management/reject")
    }
}