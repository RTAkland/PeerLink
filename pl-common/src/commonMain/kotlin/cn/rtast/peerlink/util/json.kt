/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.util

import cn.rtast.peerlink.data.play.SignalEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val json = Json {
//    serializersModule = SerializersModule {
//        polymorphic(SignalEvent::class) {
//            subclass(SignalEvent.IntentResult::class)
//            subclass(SignalEvent.JoinRequested::class)
//            subclass(SignalEvent.TurnCredentialsIssued::class)
//            subclass(SignalEvent.PlayerKicked::class)
//            subclass(SignalEvent.PlayerLeft::class)
//            subclass(SignalEvent.PlayerJoined::class)
//            subclass(SignalEvent.RoomClosed::class)
//            subclass(SignalEvent.MessageReceived::class)
//        }
//    }
}

inline fun <reified T> String.fromJson(): T = json.decodeFromString(this)

inline fun <reified T> T.encodeJson(): String = json.encodeToString(this)