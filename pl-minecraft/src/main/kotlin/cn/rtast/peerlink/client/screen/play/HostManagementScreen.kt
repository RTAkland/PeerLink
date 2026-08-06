/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */

package cn.rtast.peerlink.client.screen.play

import cn.rtast.peerlink.client.PeerLink.Companion.rpcClient
import cn.rtast.peerlink.client.util.HostPlayerStorage
import cn.rtast.peerlink.client.util.asTooltip
import cn.rtast.peerlink.data.play.PlayerInfo
import com.mojang.authlib.GameProfile
import kotlinx.coroutines.*
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class HostManagementScreen(private val screen: Screen) : Screen(Component.translatable("peerlink.hostManagement")) {
    private val layout = HeaderAndFooterLayout(this)
    private var playerSelectionList: PlayerSelectionList? = null
    private val screenScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null

    override fun init() {
        this.layout.addTitleHeader(this.title, this.font)
        this.playerSelectionList = PlayerSelectionList(this.minecraft!!).also {
            this.layout.addToContents(it)
        }

        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE) { this.onClose() }.width(200).build())
        this.layout.visitWidgets { this.addRenderableWidget(it) }
        this.repositionElements()
        this.startPollingPlayers()
    }

    private fun startPollingPlayers() {
        this.pollJob?.cancel()
        this.pollJob = this.screenScope.launch {
            while (isActive) {
                try {
                    val players = rpcClient?.signalingService?.getRoomState()
                        ?.members?.toMutableList()
                        ?.also { it.removeIf { element -> element.uuid == minecraft?.gameProfile?.id?.toKotlinUuid() } }
                        ?: emptyList()
                    minecraft?.execute { updatePlayerListUI(players) }
                } catch (e: Exception) {
                    if (e is CancellationException) break
                    e.printStackTrace()
                }
                delay(3000.milliseconds)
            }
        }
    }

    private fun updatePlayerListUI(newPlayers: List<PlayerInfo>) {
        val selectionList = this.playerSelectionList ?: return
        val currentEntries = selectionList.children().associateBy { it.playerInfo.uuid }
//        val newUuids = newPlayers.map { it.uuid }.toSet()
        val updatedEntries = newPlayers.map { info ->
            currentEntries[info.uuid]?.apply { this.updatePlayerInfo(info) } ?: PlayerEntry(info)
        }
        selectionList.replaceEntries(updatedEntries)
        if (updatedEntries.isEmpty()) {
            this.minecraft?.narrator?.say(NO_PLAYERS_TEXT)
        }
    }

    override fun repositionElements() {
        this.layout.arrangeElements()
        this.playerSelectionList?.updateSize(this.width, this.layout)
    }

    override fun onClose() {
        this.screenScope.cancel()
        this.minecraft?.setScreen(this.screen)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, a: Float) {
        super.render(graphics, mouseX, mouseY, a)
        if (this.playerSelectionList?.hasNoPlayers() == true) {
            graphics.drawCenteredString(this.font, NO_PLAYERS_TEXT, this.width / 2, this.height / 2 - 20, -1)
        }
    }

    private inner class PlayerSelectionList(minecraft: Minecraft) : ContainerObjectSelectionList<PlayerEntry>(
        minecraft,
        this@HostManagementScreen.width,
        this@HostManagementScreen.layout.contentHeight,
        this@HostManagementScreen.layout.headerHeight,
        36
    ) {
        override fun getRowWidth(): Int = 280
        fun hasNoPlayers(): Boolean = this.itemCount == 0
        fun removePlayerEntry(entry: PlayerEntry) {
            this.removeEntry(entry)
            if (this.hasNoPlayers()) this@HostManagementScreen.minecraft?.narrator?.say(NO_PLAYERS_TEXT)
        }
    }

    private inner class PlayerEntry(var playerInfo: PlayerInfo) :
        ContainerObjectSelectionList.Entry<PlayerEntry>() {
        private val childrenList = mutableListOf<AbstractWidget>()
        private val opButton: CycleButton<Boolean>
        private val kickButton: SpriteIconButton
        private val playerNameWidget: StringWidget
        private var playerSkinFuture: CompletableFuture<PlayerSkin>? = null
        private var isProcessing = false

        init {
            val listWidth = this@HostManagementScreen.playerSelectionList?.rowWidth ?: 280
            val maxTextWidth = listWidth - 28 - 50 - 21 - 32
            val uuidTooltip = Component.literal(playerInfo.uuid.toString()).asTooltip()
            this.playerNameWidget = StringWidget(
                Component.literal(playerInfo.name),
                this@HostManagementScreen.font
            ).also {
                it.width = maxTextWidth
                it.tooltip = uuidTooltip
            }
            this.opButton = CycleButton.booleanBuilder(
                Component.translatable("peerlink.operator"),
                Component.translatable("peerlink.guest"),
            ).displayOnlyValue().withInitialValue(playerInfo.isOp)
                .create(0, 0, 45, 20, Component.translatable("peerlink.opStatus")) { _, isNowOp ->
                    this.toggleOpStatus(isNowOp, playerInfo)
                }
            this.kickButton = SpriteIconButton.builder(KICK_PLAYER, { this.handleKick() }, false)
                .sprite(KICK_SPRITE, 18, 18).size(21, 21).build()
            this.childrenList.addAll(listOf(this.playerNameWidget, this.opButton, this.kickButton))
            this.loadPlayerSkin()
        }

        fun updatePlayerInfo(newInfo: PlayerInfo) {
            this.playerInfo = newInfo
            this.playerNameWidget.message = Component.literal(newInfo.name)
        }

        private fun loadPlayerSkin() {
            val javaUuid = UUID.fromString(playerInfo.uuid.toString())
            val profile = GameProfile(javaUuid, playerInfo.name)
            val skinManager = this@HostManagementScreen.minecraft!!.skinManager
            this.playerSkinFuture = skinManager.getOrLoad(profile)
        }

        override fun children(): List<GuiEventListener> = this.childrenList
        override fun narratables(): List<NarratableEntry> = this.childrenList

        override fun render(
            guiGraphics: GuiGraphics,
            index: Int,
            y: Int,
            x: Int,
            entryWidth: Int,
            entryHeight: Int,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val headSize = 20
            val headX = x + 8
            val headY = y + (entryHeight - headSize) / 2
            val javaUuid = UUID.fromString(playerInfo.uuid.toString())
            val skin = this.playerSkinFuture?.getNow(null)
                ?: DefaultPlayerSkin.get(javaUuid)
            PlayerFaceRenderer.draw(guiGraphics, skin, headX, headY, headSize)
            val textX = headX + headSize + 8
            val textY = y + (entryHeight - this@HostManagementScreen.font.lineHeight) / 2
            this.playerNameWidget.setPosition(textX, textY)
            this.playerNameWidget.renderWidget(guiGraphics, mouseX, mouseY, x.toFloat())
            val buttonY = y + entryHeight / 2 - 10
            this.opButton.setPosition(x + entryWidth - 16 - 21 - 48, buttonY)
            this.opButton.render(guiGraphics, mouseX, mouseY, delta)
            this.kickButton.setPosition(x + entryWidth - 8 - 21, buttonY)
            this.kickButton.render(guiGraphics, mouseX, mouseY, delta)
        }

        private fun toggleOpStatus(isOp: Boolean, playerInfo: PlayerInfo) {
            HostPlayerStorage.setOp(playerInfo.uuid, playerInfo.name, isOp)
            val server = minecraft?.singleplayerServer ?: return
            val targetPlayer = server.playerList.getPlayer(playerInfo.uuid.toJavaUuid()) ?: return
            val source = server.createCommandSourceStack()
            val messageKey = if (isOp) "commands.op.success" else "commands.deop.success"
            source.sendSuccess({ Component.translatable(messageKey, playerInfo.name) }, true)
            server.playerList.sendPlayerPermissionLevel(targetPlayer)
        }

        private fun handleKick() {
            if (this.isProcessing) return
            this.isProcessing = true
            this.kickButton.active = false
            this.opButton.active = false
            this@HostManagementScreen.screenScope.launch {
                try {
                    rpcClient?.signalingService?.kickPlayer(playerInfo.uuid, "Kicked by host")
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    this@HostManagementScreen.minecraft?.execute {
                        this@HostManagementScreen.playerSelectionList?.removePlayerEntry(this@PlayerEntry)
                    }
                }
            }
        }
    }

    private companion object {
        private val NO_PLAYERS_TEXT = Component.translatable("peerlink.noConnectedPlayers")
        private val KICK_PLAYER = Component.translatable("peerlink.kick")
        private val KICK_SPRITE = ResourceLocation("peerlink", "icon/management/reject")
    }

    private val PlayerInfo.isOp get() = HostPlayerStorage.isOp(this.uuid)
}