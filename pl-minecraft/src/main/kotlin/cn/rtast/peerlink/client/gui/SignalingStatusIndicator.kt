/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.client.gui

import cn.rtast.peerlink.client.PeerLink.Companion.rpcClient
import cn.rtast.peerlink.client.screen.SignalingServerOptionsScreen
import cn.rtast.peerlink.client.screen.play.PeerLinkHostScreen
import cn.rtast.peerlink.client.screen.play.PeerLinkScreen
import cn.rtast.peerlink.client.util.toSpriteTexture
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.sounds.SoundEvents

object SignalingStatusIndicator {
    private const val SCALE = 2
    private const val MARGIN = 10
    private const val BORDER = 0xFF111827.toInt()
    private val pattern = arrayOf(
        "  BBBB  ",
        " BLLGGB ",
        "BLLGGGDB",
        "BGGGGGDB",
        "BGGGGDDB",
        "BGGD3D3B",
        " BDDDDB ",
        "  BBBB  "
    )

    fun register() {
        ScreenEvents.AFTER_INIT.register { client, screen, scaledWidth, _ ->
            if (screen is PauseScreen ||
                screen is TitleScreen ||
                screen is SelectWorldScreen ||
                screen is PeerLinkHostScreen ||
                screen is PeerLinkScreen
            ) {
                ScreenEvents.afterExtract(screen).register { _, guiGraphics, mouseX, mouseY, _ ->
                    renderIndicator(guiGraphics, client, scaledWidth, mouseX, mouseY)
                }

                ScreenMouseEvents.afterMouseClick(screen).register { screen, event, _ ->
                    if (event.buttonInfo.button == 0) {
                        val size = 8 * SCALE
                        val x = scaledWidth - size - MARGIN
                        val y = MARGIN
                        if (event.x in x.toDouble()..(x + size).toDouble() &&
                            event.y in y.toDouble()..(y + size).toDouble()
                        ) {
                            client.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                            client.gui.setScreen(SignalingServerOptionsScreen(screen))
                        }
                    }
                    true
                }
            }
        }
    }

    private fun renderIndicator(
        guiGraphics: GuiGraphicsExtractor,
        client: Minecraft,
        screenWidth: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val size = 8 * SCALE
        val x = screenWidth - size - MARGIN
        val y = MARGIN
        val isConnected = rpcClient?.isConnected ?: false
        val texture = (if (isConnected) "signaling/connected" else "signaling/disconnected").toSpriteTexture()
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, x, y, 8 * SCALE, 8 * SCALE)
        if (mouseX in x..(x + size) && mouseY in y..(y + size)) {
            val statusText = if (rpcClient?.isConnected ?: false) {
                val latency = rpcClient?.latencyMs?.value?.toInt() ?: -1
                val latencyColor = when {
                    latency !in 0..2000 -> 0x555555
                    latency <= 100 -> 0x52C41A
                    latency <= 250 -> 0x1890FF
                    latency <= 800 -> 0xFA8C16
                    else -> 0xF5222D
                }
                val latencyComponent = Component.literal("${latency}ms")
                    .setStyle(Style.EMPTY.withColor(latencyColor))
                Component.translatable("peerlink.signalingConnected", latencyComponent)
                    .setStyle(Style.EMPTY.withColor(0xAAAAAA))
            } else Component.translatable("peerlink.signalingServerNotConnected")
                .setStyle(Style.EMPTY.withColor(0xFF5555))
            val tooltip = ClientTooltipComponent.create(statusText.visualOrderText)
            guiGraphics.tooltip(
                /* font = */ client.font,
                /* lines = */ listOf(tooltip),
                /* xo = */ mouseX,
                /* yo = */ mouseY + 10,
                /* positioner = */ DefaultTooltipPositioner.INSTANCE,
                /* style = */ null
            )
        }
    }
}