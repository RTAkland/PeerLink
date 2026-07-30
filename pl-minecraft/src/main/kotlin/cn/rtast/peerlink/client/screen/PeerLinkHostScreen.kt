/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.client.screen

import cn.rtast.peerlink.client.util.rpc.RpcManager
import cn.rtast.peerlink.client.util.showNotification
import cn.rtast.peerlink.client.webrtc.host.WebRTCHostManager
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.WorldOptionsScreen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType

class PeerLinkHostScreen(private val parent: Screen) : Screen(Component.translatable("peerlink.openToWebRTC")) {
    private val layout = HeaderAndFooterLayout(this)
    private var peerLinkEnabled = WebRTCHostManager.currentRoomId != null
    private var initialPeerLinkEnabled = peerLinkEnabled
    private var onlineMode = true
    private var initialOnlineMode = onlineMode
    private var gameMode = GameType.SURVIVAL
    private var initialGameMode = GameType.SURVIVAL
    private var allowCommands = false
    private var initialAllowCommands = false
    private var applyChangesButton: Button? = null

    val currentRoomId =
        if (WebRTCHostManager.currentRoomId != null) Component.literal(WebRTCHostManager.currentRoomId.toString()) else PLACEHOLDER_ROOM_ID

    val roomIdButton = Button.builder(currentRoomId) {
        if (WebRTCHostManager.currentRoomId != null) {
            minecraft.keyboardHandler.clipboard = WebRTCHostManager.currentRoomId.toString()
            showNotification(Component.translatable("peerlink.roomIdCopied"), null)
        }
    }.width(210).build()

    companion object {
        private val PEERLINK_ENABLE_LABEL = Component.translatable("peerlink.screen.host.enable")
        private val GAME_MODE_LABEL = Component.translatable("selectWorld.gameMode")
        private val ALLOW_COMMANDS_LABEL = Component.translatable("selectWorld.allowCommands")
        private val ROOM_ID_HEADER =
            Component.translatable("peerlink.screen.host.roomIdHeader").withStyle(ChatFormatting.GRAY)
        private val OTHER_PLAYERS_HEADER = Component.translatable("menu.multiplayerOptions.otherPlayers.header")
            .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BOLD)
        private val APPLY_CHANGES = Component.translatable("menu.multiplayerOptions.applyChanges")
        private val PLACEHOLDER_ROOM_ID = Component.translatable("peerlink.screen.host.roomIdPlaceholder")
    }

    override fun init() {
        val singleplayerServer = minecraft.singleplayerServer
        if (singleplayerServer == null) {
            this.onClose()
            return
        }
        this.layout.addTitleHeader(this.title, this.font)
        val content = this.layout.addToContents(LinearLayout.vertical().spacing(8))
        content.defaultCellSetting().alignHorizontallyCenter()
        content.addChild(
            CycleButton.onOffBuilder(this.peerLinkEnabled).create(
                PEERLINK_ENABLE_LABEL
            ) { _, value ->
                this.peerLinkEnabled = value
                this.updateApplyChangesActiveState()
            }
        )
        content.addChild(StringWidget(ROOM_ID_HEADER, this.font))
        roomIdButton.active = WebRTCHostManager.currentRoomId != null
        content.addChild(roomIdButton)
        content.addChild(StringWidget(OTHER_PLAYERS_HEADER, this.font))
        val otherPlayerSettings = content.addChild(LinearLayout.horizontal().spacing(8))
        otherPlayerSettings.defaultCellSetting().alignHorizontallyCenter()
        this.gameMode = singleplayerServer.gameTypeForOtherPlayers
        this.initialGameMode = this.gameMode
        val gameModeButton = otherPlayerSettings.addChild(
            CycleButton.builder(GameType::getShortDisplayName, this.gameMode)
                .withValues(GameType.SURVIVAL, GameType.SPECTATOR, GameType.CREATIVE, GameType.ADVENTURE)
                .create(GAME_MODE_LABEL) { _, value ->
                    this.gameMode = value
                    this.updateApplyChangesActiveState()
                }
        )
        this.allowCommands = singleplayerServer.commandsAllowedForOtherPlayers()
        this.initialAllowCommands = this.allowCommands
        val allowCommandsButton = otherPlayerSettings.addChild(
            CycleButton.onOffBuilder(this.allowCommands).create(
                ALLOW_COMMANDS_LABEL
            ) { _, value ->
                this.allowCommands = value
                this.updateApplyChangesActiveState()
            }
        )
        if (singleplayerServer.isHardcore) {
            gameModeButton.active = false
            gameModeButton.setTooltip(WorldOptionsScreen.GAME_MODE_DISABLED_HARDCORE_TOOLTIP)
            allowCommandsButton.active = false
            allowCommandsButton.setTooltip(WorldOptionsScreen.ALLOW_COMMANDS_DISABLED_TOOLTIP)
        }
        val footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8))
        this.applyChangesButton = Button.builder(APPLY_CHANGES) { button ->
            button.active = false
            singleplayerServer.gameTypeForOtherPlayers = this.gameMode
            if (this.allowCommands != this.initialAllowCommands) {
                singleplayerServer.setCommandsAllowedForOtherPlayers(this.allowCommands)
            }
            if (this.peerLinkEnabled) {
                if (WebRTCHostManager.currentRoomId == null) {
                    showNotification(Component.translatable("peerlink.p2p.creatingRoom"), null)
                    WebRTCHostManager.openWebRTCRoom(
                        RpcManager.scope,
                        RpcManager.minecraftSignalingService!!,
                        RpcManager.serverSignalingService!!,
                        onlineMode, allowCommands, gameMode
                    ) { result ->
                        result.onSuccess {
                            this.initialPeerLinkEnabled = this.peerLinkEnabled
                            this.initialOnlineMode = this.onlineMode
                            this.initialGameMode = this.gameMode
                            this.initialAllowCommands = this.allowCommands
                            updateRoomId(Component.literal(it.roomId), true)
                        }
                        result.onFailure {
                            showNotification(
                                Component.translatable("peerlink.roomCreateFailed"),
                                Component.literal(it.message ?: "")
                            )
                        }
                        this.updateApplyChangesActiveState()
                    }
                } else {
                    showNotification(Component.translatable("peerlink.p2p.alreadyHosting"), null)
                    this.updateApplyChangesActiveState()
                }
            } else {
                singleplayerServer.unpublishServer()
                WebRTCHostManager.stopHosting()
                this.initialPeerLinkEnabled = false
                this.initialOnlineMode = this.onlineMode
                this.initialGameMode = this.gameMode
                this.initialAllowCommands = this.allowCommands
                this.updateApplyChangesActiveState()
                updateRoomId(currentRoomId, false)
            }
        }.build()

        this.applyChangesButton?.active = false
        footer.addChild(this.applyChangesButton!!)
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL) { this.onClose() }.build())
        this.layout.visitWidgets(this::addRenderableWidget)
        this.repositionElements()
    }

    private fun updateApplyChangesActiveState() {
        this.applyChangesButton?.active = this.hasSettingsChanges()
    }

    private fun hasSettingsChanges(): Boolean {
        return this.peerLinkEnabled != this.initialPeerLinkEnabled ||
                this.onlineMode != this.initialOnlineMode ||
                this.gameMode != this.initialGameMode ||
                this.allowCommands != this.initialAllowCommands
    }

    override fun repositionElements() {
        this.layout.arrangeElements()
    }

    override fun onClose() {
        this.minecraft.gui.setScreen(this.parent)
    }

    fun updateRoomId(roomIdComponent: Component, enabled: Boolean) {
        roomIdButton.message = roomIdComponent
        roomIdButton.active = enabled
    }
}