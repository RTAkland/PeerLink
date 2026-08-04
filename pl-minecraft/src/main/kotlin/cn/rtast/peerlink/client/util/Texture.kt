/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/4
 */


package cn.rtast.peerlink.client.util

import net.minecraft.resources.Identifier

fun String.toSpriteTexture(): Identifier =
    Identifier.fromNamespaceAndPath("peerlink", "icon/$this")