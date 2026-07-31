/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client

import cn.rtast.peerlink.client.data.PeerLinkClientConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.client.Minecraft

val minecraft = Minecraft.getInstance()
val scope = CoroutineScope(Dispatchers.IO)

val defaultConfig = PeerLinkClientConfig(
    "wss://peer.7o.ink"  // Thanks to xiaoman1221(github.com/xiaoman1221) for providing the signaling server
)