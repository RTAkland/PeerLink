/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/1
 */


package cn.rtast.peerlink.client.data

enum class ConnectResult {
    Awaiting,
    Rejected,
    Invalid,
    Accepted,
    SignalingError,
    Failed
}