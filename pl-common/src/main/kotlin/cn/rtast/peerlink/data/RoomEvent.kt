/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.data

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class RoomEvent {
    @Serializable
    data class PlayerJoined(val player: PlayerInfo) : RoomEvent()

    @Serializable
    data class PlayerLeft(val playerId: Uuid) : RoomEvent()

    @Serializable
    data class SignalReceived(val message: SignalingMessage) : RoomEvent()
}