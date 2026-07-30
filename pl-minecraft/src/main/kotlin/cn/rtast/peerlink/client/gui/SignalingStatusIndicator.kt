/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.client.gui

import cn.rtast.peerlink.client.screen.PeerLinkHostScreen
import cn.rtast.peerlink.client.screen.PeerLinkScreen
import cn.rtast.peerlink.client.util.RpcManager
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

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
        val highlight = if (RpcManager.isConnected) 0xFF6EE7B7.toInt() else 0xFFFCA5A5.toInt()
        val mainColor = if (RpcManager.isConnected) 0xFF10B981.toInt() else 0xFFEF4444.toInt()
        val shadow = if (RpcManager.isConnected) 0xFF047857.toInt() else 0xFFB91C1C.toInt()

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val char = pattern[row][col]
                if (char == ' ') continue
                val color = when (char) {
                    'B' -> BORDER
                    'L' -> highlight
                    'G', 'R' -> mainColor
                    'D' -> shadow
                    else -> mainColor
                }
                val px = x + col * SCALE
                val py = y + row * SCALE
                guiGraphics.fill(px, py, px + SCALE, py + SCALE, color)
            }
        }

        if (mouseX in x..(x + size) && mouseY in y..(y + size)) {
            val statusText = if (RpcManager.isConnected) {
                val latency = RpcManager.latencyMs
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