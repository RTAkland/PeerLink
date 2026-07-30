/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/30
 */


package cn.rtast.peerlink.data.play

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class PeerIntent(
    val type: IntentType,
    val targetRoomId: String? = null,
    val targetPlayerId: Uuid? = null,
    val reason: String? = null
)