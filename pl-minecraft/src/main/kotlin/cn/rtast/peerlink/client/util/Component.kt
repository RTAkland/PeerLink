/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/4
 */


package cn.rtast.peerlink.client.util

import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

fun Component.asTooltip(): Tooltip = Tooltip.create(this)

fun String.toTranslatable(vararg args: Any): Component = Component.translatable("peerlink.$this", *args)

fun String.toTranslatable(): Component = Component.translatable("peerlink.$this")

fun String.toLiteral(): Component  = Component.literal(this)