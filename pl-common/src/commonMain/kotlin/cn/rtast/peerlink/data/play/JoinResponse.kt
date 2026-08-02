/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.data.play

import cn.rtast.peerlink.data.webrtc.TurnCredentials
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed interface JoinResponse {
    @Serializable
    data class Accepted(val hostId: Uuid, val credentials: TurnCredentials) : JoinResponse

    @Serializable
    data class Rejected(val reason: String) : JoinResponse

    @Serializable
    data object InvalidRoom : JoinResponse

    @Serializable
    data class Error(val message: String) : JoinResponse
}