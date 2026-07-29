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
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.toKotlinUuid

object RpcManager {
    private var ktorClient: HttpClient? = null
    val isRunning = AtomicBoolean(false)
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
                    connectAndListen(url)
                } catch (e: Exception) {
                    rpcLogger.error("RPC 连接断开或发生异常: ${e.message}")
                }
                minecraftSignalingService = null
                serverSignalingService = null
                try {
                    ktorClient?.close()
                } catch (_: Exception) {
                }
                delay(5000.milliseconds)
            }
        }
    }

    private suspend fun connectAndListen(url: String) {
        val client = HttpClient {
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
        ktorClient = client

        val rpcSession = client.rpcClient(url)
        val minecraftService = rpcSession.minecraftSignalingService()
        val serverService = rpcSession.serverSignalingService()
        minecraftSignalingService = minecraftService
        serverSignalingService = serverService

        val serverInfo = serverService.serverInfo()
        rpcLogger.info("信令服务器连接成功 ${serverInfo.version}")
        minecraftService.registerIdentity(
            PlayerInfo(
                minecraft.gameProfile.id.toKotlinUuid(),
                minecraft.gameProfile.name
            )
        )
        coroutineScope {
            val heartbeatJob = launch { startHeartbeatLoop(minecraftService) }
            try {
                awaitCancellation()
            } finally {
                heartbeatJob.cancel()
            }
        }
    }

    private suspend fun startHeartbeatLoop(service: MinecraftSignalingService) {
        while (currentCoroutineContext().isActive) {
            delay(10.seconds)
            try {
                service.sendHeartbeat()
            } catch (e: Exception) {
                rpcLogger.warn("心跳包发送失败: ${e.message}")
                throw e
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        scope.cancel()
        try {
            ktorClient?.close()
        } catch (_: Exception) {
        }
    }
}