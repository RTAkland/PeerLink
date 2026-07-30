/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.util.rpc

import cn.rtast.peerlink.data.webrtc.ICECandidatePayload
import cn.rtast.peerlink.util.encodeJson
import cn.rtast.peerlink.util.fromJson
import dev.kastle.webrtc.RTCIceCandidate


fun serializeCandidate(candidate: RTCIceCandidate): String =
    ICECandidatePayload(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp, candidate.serverUrl)
        .encodeJson()

fun deserializeCandidate(candidate: String): RTCIceCandidate {
    val payload = candidate.fromJson<ICECandidatePayload>()
    return RTCIceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.sdp, payload.serverUrl)
}