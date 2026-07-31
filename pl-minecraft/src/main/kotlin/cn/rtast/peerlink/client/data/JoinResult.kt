/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/30
 */


package cn.rtast.peerlink.client.data

enum class JoinResult {
    InvalidRoomId,
    RejectJoin,
    Accepted,
    PendingJoinRequest,
    JoinRequestIntentFailed,
    SignalingServerNotConnected
}