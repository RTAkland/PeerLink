/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/3
 */


package cn.rtast.peerlink.client.util

import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

fun Component.asTooltip(): Tooltip = Tooltip.create(this)