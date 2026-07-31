/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.client.data

import kotlinx.serialization.Serializable

@Serializable
data class PeerLinkClientConfig(
    val signalingServer: String,
)