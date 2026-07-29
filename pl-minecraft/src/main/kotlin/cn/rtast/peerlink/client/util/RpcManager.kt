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
    val isRunning = AtomicBoolean(false)

    @Volatile
    var isConnected = false
        private set

    var minecraftSignalingService: MinecraftSignalingService? = null
        private set
    var serverSignalingService: ServerSignalingService? = null
        private set

    @Volatile
    var latencyMs: Long = -1L
        private set

    val rpcLogger = KLogging.getLogger("PeerLink | RPC").also { it.setLoggingLevel(LogLevel.DEBUG) }

    private var mainJob: Job? = null
    var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(url: String) {
        if (!isRunning.compareAndSet(false, true)) return
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }

        mainJob = scope.launch {
            while (isActive && isRunning.get()) {
                try {
                    connectAndListen(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    rpcLogger.error("RPC 连接断开或发生异常: ${e.message}")
                } finally {
                    isConnected = false
                    minecraftSignalingService = null
                    serverSignalingService = null
                }

                if (isActive && isRunning.get()) {
                    rpcLogger.debug("将在 10 秒后尝试重新连接信令服务器...")
                    delay(10000.milliseconds)
                }
            }
        }
    }

    private suspend fun connectAndListen(url: String) {
        HttpClient {
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
        }.use { client ->
            val rpcSession = client.rpcClient("$url/rpc")
            val minecraftService = rpcSession.minecraftSignalingService()
            val serverService = rpcSession.serverSignalingService()
            val serverInfo = serverService.serverInfo()
            rpcLogger.info("信令服务器连接成功 ${serverInfo.version}")
            minecraftService.registerIdentity(
                PlayerInfo(
                    minecraft.gameProfile.id.toKotlinUuid(),
                    minecraft.gameProfile.name
                )
            )
            minecraftSignalingService = minecraftService
            serverSignalingService = serverService
            isConnected = true
            coroutineScope {
                val heartbeatJob = launch { startHeartbeatLoop(minecraftService) }
                try {
                    awaitCancellation()
                } finally {
                    heartbeatJob.cancel()
                }
            }
        }
    }

    private suspend fun startHeartbeatLoop(service: MinecraftSignalingService) {
        while (currentCoroutineContext().isActive) {
            delay(15.seconds)
            try {
                val startTime = System.currentTimeMillis()
                service.sendHeartbeat(startTime)
                val endTime = System.currentTimeMillis()
                latencyMs = endTime - startTime
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                latencyMs = -1L
                rpcLogger.warn("心跳包发送失败: ${e.message}")
                throw e
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        isConnected = false
        minecraftSignalingService = null
        serverSignalingService = null
        mainJob?.cancel()
        mainJob = null
    }
}