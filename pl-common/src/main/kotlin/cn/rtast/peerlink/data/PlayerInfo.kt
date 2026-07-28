/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.data

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class PlayerInfo(
    val uuid: Uuid,
    val username: String,
)