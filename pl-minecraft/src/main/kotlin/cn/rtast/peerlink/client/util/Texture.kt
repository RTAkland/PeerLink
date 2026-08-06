/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/4
 */


package cn.rtast.peerlink.client.util

import net.minecraft.resources.ResourceLocation


fun String.toSpriteTexture(): ResourceLocation =
    ResourceLocation("peerlink", "icon/$this")