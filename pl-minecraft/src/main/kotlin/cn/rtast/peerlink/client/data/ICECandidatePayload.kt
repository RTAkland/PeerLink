/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/1
 */


package cn.rtast.peerlink.client.data

import kotlinx.serialization.Serializable

@Serializable
data class ICECandidatePayload(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val sdp: String,
    val serverUrl: String?,
)