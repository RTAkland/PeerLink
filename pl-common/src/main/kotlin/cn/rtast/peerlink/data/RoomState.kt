/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.data

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class RoomState(
    val roomId: String,
    val hostPlayerUuid: Uuid,
    val members: List<PlayerInfo>,
)