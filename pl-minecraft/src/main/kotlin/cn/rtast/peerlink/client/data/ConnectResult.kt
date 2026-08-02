/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/1
 */


package cn.rtast.peerlink.client.data

import kotlin.uuid.Uuid

sealed interface ConnectResult {
    data class Awaiting(val host: Uuid) : ConnectResult
    data object Rejected : ConnectResult
    data object Invalid : ConnectResult
    data object Accepted : ConnectResult
    data object SignalingError : ConnectResult
    data object Failed : ConnectResult
}