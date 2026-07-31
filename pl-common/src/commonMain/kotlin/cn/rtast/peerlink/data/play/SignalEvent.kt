/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.data.play

import cn.rtast.peerlink.data.webrtc.TurnCredentials
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed interface SignalEvent {

    @Serializable
    data class IntentResult(
        val intentType: IntentType,
        val success: Boolean,
        val reason: String? = null,
    ) : SignalEvent

    @Serializable
    data class JoinRequested(
        val applicantId: Uuid,
        val applicantName: String,
    ) : SignalEvent

    @Serializable
    data class TurnCredentialsIssued(
        val targetPlayerId: Uuid,
        val credentials: TurnCredentials,
    ) : SignalEvent

    @Serializable
    data class PlayerKicked(
        val reason: String?,
    ) : SignalEvent

    @Serializable
    data class PlayerLeft(
        val playerId: Uuid,
    ) : SignalEvent

    @Serializable
    data class PlayerJoined(
        val player: PlayerInfo,
    ) : SignalEvent

    @Serializable
    data class RoomClosed(
        val reason: String = "Host left the room or closed it.",
    ) : SignalEvent

    @Serializable
    data class MessageReceived(
        val fromPlayerId: Uuid,
        val message: SignalingMessage,
    ) : SignalEvent
}