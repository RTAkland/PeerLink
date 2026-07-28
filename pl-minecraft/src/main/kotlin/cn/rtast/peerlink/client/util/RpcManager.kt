/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.util

import cn.rtast.klogging.KLogging
import cn.rtast.klogging.LogLevel
import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.data.play.PlayerInfo
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.service.MinecraftSignalingService
import cn.rtast.peerlink.service.ServerSignalingService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.toKotlinUuid

object RpcManager {
    private val isRunning = AtomicBoolean(false)
    private var ktorClient: HttpClient? = null
    var minecraftSignalingService: MinecraftSignalingService? = null
        private set
    var serverSignalingService: ServerSignalingService? = null
        private set

    val rpcLogger = KLogging.getLogger("PeerLink | RPC").also { it.setLoggingLevel(LogLevel.DEBUG) }
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(url: String) {
        if (!isRunning.compareAndSet(false, true)) return
        scope.launch {
            while (isActive && isRunning.get()) {
                try {
                    rpcLogger.info("正在连接信令服务器")
                    connectAndListen(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    rpcLogger.info("连接断开或异常: ${e.message}")
                }
                minecraftSignalingService = null
                serverSignalingService = null
                ktorClient?.close()
                rpcLogger.info("3秒后尝试自动重连")
                delay(3000.milliseconds)
            }
        }
    }

    private suspend fun connectAndListen(url: String) {
        ktorClient = HttpClient(CIO) {
            install(WebSockets)
            installKrpc {
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
        val minecraftService = ktorClient!!.rpcClient(url).minecraftSignalingService()
        minecraftSignalingService = minecraftService
        serverSignalingService = ktorClient!!.rpcClient(url).serverSignalingService()
        val serverInfo = serverSignalingService!!.serverInfo()
        rpcLogger.info("连接成功 服务端版本 ${serverInfo.version}")

        minecraftService.registerIdentity(
            PlayerInfo(
                minecraft.gameProfile.id.toKotlinUuid(),
                minecraft.gameProfile.name
            )
        )
        awaitCancellation()
    }

    fun stop() {
        isRunning.set(false)
        scope.cancel()
        ktorClient?.close()
    }
}