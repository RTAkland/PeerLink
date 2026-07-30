/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.data.play

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class SignalEvent {
    @Serializable
    data class PlayerJoined(val player: PlayerInfo) : SignalEvent()
    @Serializable
    data class PlayerLeft(val playerId: Uuid) : SignalEvent()
    @Serializable
    data class SignalingReceived(val fromPlayerId: Uuid, val message: SignalingMessage) : SignalEvent()
    @Serializable
    data class RoomClosed(val reason: String) : SignalEvent()
}