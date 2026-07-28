/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.data

import kotlinx.serialization.Serializable

@Serializable
data class ServerInfo(
    val version: String
)