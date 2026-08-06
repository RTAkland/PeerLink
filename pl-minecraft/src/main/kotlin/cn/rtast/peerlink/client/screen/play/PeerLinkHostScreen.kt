/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */

package cn.rtast.peerlink.client.screen.play

import cn.rtast.peerlink.client.PeerLink
import cn.rtast.peerlink.client.mixin.MinecraftServerAccessor
import cn.rtast.peerlink.client.util.HostPlayerStorage
import cn.rtast.peerlink.client.util.asTooltip
import cn.rtast.peerlink.client.util.showNotification
import cn.rtast.peerlink.client.util.toTranslatable
import cn.rtast.peerlink.data.play.RoomState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType

class PeerLinkHostScreen(private val parent: Screen) : Screen(Component.translatable("peerlink.openToWebRTC")) {
    private val layout = HeaderAndFooterLayout(this)
    private var peerLinkEnabled = currentRoomState != null
    private var initialPeerLinkEnabled = peerLinkEnabled

    private var onlineMode = true
    private var initialOnlineMode = true
    private var gameMode = GameType.SURVIVAL
    private var initialGameMode = GameType.SURVIVAL
    private var allowCommands = false
    private var initialAllowCommands = false
    private var applyChangesButton: Button? = null
    private val screenScope = CoroutineScope(Dispatchers.IO)

    private val currentRoomIdComponent: Component
        get() = currentRoomState?.roomId?.let { Component.literal(it) } ?: PLACEHOLDER_ROOM_ID

    private val roomIdButton = Button.builder(currentRoomIdComponent) button@{ _ ->
        currentRoomState?.roomId?.also { minecraft?.keyboardHandler?.clipboard = it }
    }.width(210).build()

    companion object {
        private val PEERLINK_ENABLE_LABEL = Component.translatable("peerlink.screen.host.enable")
        private val GAME_MODE_LABEL = Component.translatable("peerlink.mc.gamemode")
        private val ALLOW_COMMANDS_LABEL = Component.translatable("peerlink.mc.allowCommands")
        private val ROOM_ID_HEADER =
            Component.translatable("peerlink.screen.host.sessionIdHeader").withStyle(ChatFormatting.GRAY)
        private val OTHER_PLAYERS_HEADER = Component.translatable("peerlink.mc.othersOptions")
            .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BOLD)
        private val APPLY_CHANGES = Component.translatable("peerlink.mc.applyChanges")
        private val PLACEHOLDER_ROOM_ID = Component.translatable("peerlink.screen.host.sessionIdPlaceholder")
        private val ONLINE_MODE = Component.translatable("peerlink.screen.host.onlineMode")
        var currentRoomState: RoomState? = null
    }

    override fun init() {
        val singleplayerServer = minecraft!!.singleplayerServer
        if (singleplayerServer == null) {
            this.onClose()
            return
        }

        this.onlineMode = singleplayerServer.usesAuthentication()
        this.initialOnlineMode = this.onlineMode

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
        roomIdButton.active = currentRoomState != null
        content.addChild(roomIdButton)
        content.addChild(StringWidget(OTHER_PLAYERS_HEADER, this.font))
        val otherPlayerSettings = content.addChild(LinearLayout.horizontal().spacing(8))
        otherPlayerSettings.defaultCellSetting().alignHorizontallyCenter()

        this.gameMode = singleplayerServer.defaultGameType
        this.initialGameMode = this.gameMode
        val gameModeButton = otherPlayerSettings.addChild(
            CycleButton.builder(GameType::getShortDisplayName)
                .withInitialValue(this.gameMode)
                .withValues(GameType.SURVIVAL, GameType.SPECTATOR, GameType.CREATIVE, GameType.ADVENTURE)
                .create(GAME_MODE_LABEL) { _, value ->
                    this.gameMode = value
                    this.updateApplyChangesActiveState()
                }
        )

        this.initialAllowCommands = this.allowCommands
        val allowCommandsButton = otherPlayerSettings.addChild(
            CycleButton.onOffBuilder(this.allowCommands).create(
                ALLOW_COMMANDS_LABEL
            ) { _, value ->
                this.allowCommands = value
                this.updateApplyChangesActiveState()
            }
        )

        val securitySettings = content.addChild(LinearLayout.horizontal().spacing(8))
        securitySettings.defaultCellSetting().alignHorizontallyCenter()
        securitySettings.addChild(CycleButton.onOffBuilder(this.onlineMode).create(ONLINE_MODE) { _, value ->
            this.onlineMode = value
            this.updateApplyChangesActiveState()
        })

        if (singleplayerServer.isHardcore) {
            gameModeButton.active = false
            gameModeButton.tooltip = "peerlink.mc.gamemodeDisabledHardcore".toTranslatable().asTooltip()
            allowCommandsButton.active = false
            allowCommandsButton.tooltip = "peerlink.mc.allowCommandsDisabledHardcore".toTranslatable().asTooltip()
        }
        val footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8))
        this.applyChangesButton = Button.builder(APPLY_CHANGES) { button ->
            button.active = false
            singleplayerServer.defaultGameType = this.gameMode
            if (this.allowCommands != this.initialAllowCommands) {
                singleplayerServer.playerList.isAllowCommandsForAllPlayers = this.allowCommands
            }
            if (this.onlineMode != this.initialOnlineMode) {
                (singleplayerServer as MinecraftServerAccessor).`peerlink$setOnlineMode`(this.onlineMode)
            }
            val manager = PeerLink.manager
            val rpcClient = PeerLink.rpcClient
            if (this.peerLinkEnabled) {
                if (rpcClient?.isConnected != true) {
                    showNotification(
                        Component.translatable("peerlink.sessionCreateFailed"),
                        Component.translatable("peerlink.signalingServerNotConnected")
                    )
                    this.updateApplyChangesActiveState()
                    return@builder
                }

                if (currentRoomState == null) {
                    showNotification(Component.translatable("peerlink.p2p.creatingSession"), null)
                    screenScope.launch {
                        try {
                            manager!!.host(
                                singleplayerServer, gameMode, allowCommands, onlineMode
                            ) {
                                currentRoomState = it
                                HostPlayerStorage.init()
                                minecraft?.execute {
                                    this@PeerLinkHostScreen.initialPeerLinkEnabled =
                                        this@PeerLinkHostScreen.peerLinkEnabled
                                    this@PeerLinkHostScreen.initialOnlineMode = this@PeerLinkHostScreen.onlineMode
                                    this@PeerLinkHostScreen.initialGameMode = this@PeerLinkHostScreen.gameMode
                                    this@PeerLinkHostScreen.initialAllowCommands = this@PeerLinkHostScreen.allowCommands
                                    updateRoomId(Component.literal(it.roomId), true)
                                    this@PeerLinkHostScreen.updateApplyChangesActiveState()
                                }
                            }
                        } catch (e: Exception) {
                            minecraft?.execute {
                                showNotification(
                                    Component.translatable("peerlink.sessionCreateFailed"),
                                    Component.literal(e.message ?: "Unknown Error")
                                )
                                this@PeerLinkHostScreen.updateApplyChangesActiveState()
                            }
                        }
                    }
                } else {
                    this.initialOnlineMode = this.onlineMode
                    showNotification(Component.translatable("peerlink.p2p.alreadyHosting"), null)
                    this.updateApplyChangesActiveState()
                }
            } else {
                minecraft?.execute {
//                    singleplayerServer.stopServer() // TODO
                    this@PeerLinkHostScreen.initialPeerLinkEnabled = false
                    this@PeerLinkHostScreen.initialOnlineMode = this@PeerLinkHostScreen.onlineMode
                    this@PeerLinkHostScreen.initialGameMode = this@PeerLinkHostScreen.gameMode
                    this@PeerLinkHostScreen.initialAllowCommands = this@PeerLinkHostScreen.allowCommands
                    updateRoomId(PLACEHOLDER_ROOM_ID, false)
                    currentRoomState = null
                    this@PeerLinkHostScreen.updateApplyChangesActiveState()
                }
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
        this.minecraft?.setScreen(this.parent)
    }

    fun updateRoomId(roomIdComponent: Component, enabled: Boolean) {
        roomIdButton.message = roomIdComponent
        roomIdButton.active = enabled
    }
}