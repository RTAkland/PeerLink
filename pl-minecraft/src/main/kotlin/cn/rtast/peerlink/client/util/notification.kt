/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.client.util

import cn.rtast.peerlink.client.minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component

fun showNotification(title: Component, message: Component?) {
    minecraft.execute {
        minecraft.gui.toastManager().addToast(
            SystemToast(
                SystemToast.SystemToastId(3000L),
                title, message
            )
        )
    }
}