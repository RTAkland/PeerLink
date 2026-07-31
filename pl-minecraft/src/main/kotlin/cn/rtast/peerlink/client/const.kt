/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client

import cn.rtast.klogging.KLogging
import cn.rtast.klogging.Logger
import cn.rtast.peerlink.client.util.GlobalAppScope
import net.minecraft.client.Minecraft

val minecraft = Minecraft.getInstance()
val plScope = GlobalAppScope()

val logger = KLogging.getLogger("PeerLink")