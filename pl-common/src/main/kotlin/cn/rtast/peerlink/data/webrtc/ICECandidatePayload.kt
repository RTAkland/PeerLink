/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.data.webrtc

import kotlinx.serialization.Serializable

@Serializable
data class ICECandidatePayload(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val sdp: String,
    val serverUrl: String?
)