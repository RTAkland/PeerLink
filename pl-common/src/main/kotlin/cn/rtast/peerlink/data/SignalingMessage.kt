/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.data

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SignalingMessage(
    val senderPlayerUuid: Uuid,
    val targetPlayerUuid: Uuid,
    val type: SignalingType,
    val payload: String,
) {
    enum class SignalingType {
        Offer, Answer, ICE
    }
}