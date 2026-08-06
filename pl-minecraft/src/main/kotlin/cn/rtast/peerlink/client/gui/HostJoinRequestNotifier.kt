/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/3
 */

package cn.rtast.peerlink.client.gui

import cn.rtast.peerlink.client.minecraft
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

object HostJoinRequestNotifier {
    private var currentText: Component? = null
    private var displayTicksLeft = 0
    private const val MAX_TICKS = 80

    private const val FADE_IN_TICKS = 10
    private const val FADE_OUT_TICKS = 15

    fun showNotification(applicantName: String) {
        this.currentText = Component.translatable("peerlink.hud.join_request", applicantName)
        this.displayTicksLeft = MAX_TICKS
        minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f))
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            if (this.displayTicksLeft > 0) {
                this.displayTicksLeft--
                if (this.displayTicksLeft == 0) {
                    this.currentText = null
                }
            }
        }

        HudRenderCallback.EVENT.register { graphics, _ ->
            if (minecraft.screen == null) render(graphics)
        }

        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            ScreenEvents.afterRender(screen).register { _, extractor, _, _, _ ->
                render(extractor)
            }
        }
    }

    private fun render(graphics: GuiGraphics) {
        if (displayTicksLeft <= 0 || currentText == null) return
        val alphaProgress = when {
            displayTicksLeft > MAX_TICKS - FADE_IN_TICKS -> (MAX_TICKS - displayTicksLeft).toFloat() / FADE_IN_TICKS
            displayTicksLeft < FADE_OUT_TICKS -> displayTicksLeft.toFloat() / FADE_OUT_TICKS
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)
        val window = minecraft.window
        val textWidth = minecraft.font.width(currentText!!)
        val rectWidth = textWidth + 16
        val rectHeight = 18
        val x = (window.guiScaledWidth - rectWidth) / 2
        val y = 10
        val bgAlpha = (0x80 * alphaProgress).toInt()
        val bgColor = (bgAlpha shl 24) or 0x000000
        val fgAlpha = (0xFF * alphaProgress).toInt()
        val accentColor = (fgAlpha shl 24) or 0x55FF55
        val textColor = (fgAlpha shl 24) or 0xFFFFFF
        graphics.fill(x, y, x + rectWidth, y + rectHeight, bgColor)
        graphics.fill(x, y, x + rectWidth, y + 2, accentColor)
        val textY = y + (rectHeight - 8) / 2
        graphics.drawString(minecraft.font, currentText!!, x + 8, textY, textColor, true)
    }
}