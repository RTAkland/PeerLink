/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/1
 */


package cn.rtast.peerlink.client.webrtc

import cn.rtast.peerlink.client.data.ICECandidatePayload
import cn.rtast.peerlink.util.encodeJson
import cn.rtast.peerlink.util.fromJson
import dev.kastle.webrtc.RTCIceCandidate

fun RTCIceCandidate.serializeCandidate(): String =
    ICECandidatePayload(this.sdpMid, this.sdpMLineIndex, this.sdp, this.serverUrl).encodeJson()

fun String.deserializeCandidate(): RTCIceCandidate {
    val payload = this.fromJson<ICECandidatePayload>()
    return RTCIceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.sdp, payload.serverUrl)
}