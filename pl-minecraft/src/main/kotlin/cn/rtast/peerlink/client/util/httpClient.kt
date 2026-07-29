/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.util

import cn.rtast.peerlink.data.play.SignalEvent
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import kotlinx.rpc.RpcClient
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val httpClient = HttpClient {
    installKrpc()
    install(WebSockets)
}

fun HttpClient.rpcClient(url: String): RpcClient =
    rpc {
        url(url)
        rpcConfig {
            serialization {
                json {
                    serializersModule = SerializersModule {
                        polymorphic(SignalEvent::class) {
                            subclass(SignalEvent.PlayerJoined::class)
                            subclass(SignalEvent.PlayerLeft::class)
                            subclass(SignalEvent.SignalingReceived::class)
                            subclass(SignalEvent.RoomClosed::class)
                        }
                    }
                }
            }
        }
    }