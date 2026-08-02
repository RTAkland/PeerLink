/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client

import cn.rtast.peerlink.client.data.PeerLinkClientConfig
import cn.rtast.peerlink.data.play.PlayerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.client.Minecraft
import kotlin.uuid.toKotlinUuid

val minecraft = Minecraft.getInstance()
val scope = CoroutineScope(Dispatchers.IO)

val currentPlayerInfo
    get() = PlayerInfo(minecraft.user.profileId.toKotlinUuid(), minecraft.user.name)

val defaultConfig = PeerLinkClientConfig(
    "wss://peer.7o.ink"  // Thanks to xiaoman1221(github.com/xiaoman1221) for providing the signaling server
)