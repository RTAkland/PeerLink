/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */

package cn.rtast.peerlink.client.network

import cn.rtast.klogging.KLogging
import cn.rtast.peerlink.client.PeerLink
import cn.rtast.peerlink.client.currentPlayerInfo
import cn.rtast.peerlink.service.AuthService
import cn.rtast.peerlink.service.MinecraftSignalingService
import cn.rtast.peerlink.service.ServerSignalingService
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.rpc.krpc.ktor.client.Krpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class RpcClient(
    private val signalingUrl: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    companion object {
        private val logger = KLogging.getLogger("PeerLinkRpcClient")
        private const val HEARTBEAT_INTERVAL_SECONDS = 15L
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISPOSING
    }

    private class ConnectionLostException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    var authService: AuthService? = null
        private set
    var signalingService: MinecraftSignalingService? = null
        private set
    var serverService: ServerSignalingService? = null
        private set

    private var client: HttpClient? = null
    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null

    private val _latencyMs = MutableStateFlow(-1L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    val isConnected: Boolean
        get() = connectionState.value == ConnectionState.CONNECTED

    private val reconnectDelay = 5.seconds

    fun start() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) return
        connectionJob = scope.launch {
            while (isActive && _connectionState.value != ConnectionState.DISPOSING) {
                var currentClient: HttpClient? = null
                try {
                    _connectionState.value = ConnectionState.CONNECTING
                    logger.info("Attempting connection to signaling server...")
                    currentClient = HttpClient {
                        install(WebSockets)
                        install(Krpc) { serialization { json() } }
                    }
                    client = currentClient
                    val rpcClient = currentClient.rpc("$signalingUrl/rpc")
                    authService = rpcClient.withService<AuthService>()
                    serverService = rpcClient.withService<ServerSignalingService>()
                    signalingService = rpcClient.withService<MinecraftSignalingService>()
                    _connectionState.value = ConnectionState.CONNECTED
                    val serverInfo = serverService?.serverInfo()
                    logger.info("Signaling Server Connected -> ${serverInfo?.version}")
                    authService!!.registerIdentity(currentPlayerInfo)
                    PeerLink.manager?.initialize()
                    coroutineScope {
                        startHeartbeatLoop(this@coroutineScope)
                        awaitCancellation()
                    }
                } catch (e: CancellationException) {
                    if (_connectionState.value == ConnectionState.DISPOSING) {
                        throw e
                    }
                } catch (_: Exception) {
                    if (_connectionState.value == ConnectionState.DISPOSING) break
                } finally {
                    stopHeartbeat()
                    authService = null
                    signalingService = null
                    serverService = null
                    _latencyMs.value = -1L
                    runCatching { currentClient?.close() }
                    client = null
                    if (_connectionState.value != ConnectionState.DISPOSING) {
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
                if (_connectionState.value == ConnectionState.DISPOSING) break
                delay(reconnectDelay)
            }
        }
    }

    private fun startHeartbeatLoop(sessionScope: CoroutineScope) {
        stopHeartbeat()
        heartbeatJob = sessionScope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(HEARTBEAT_INTERVAL_SECONDS.seconds)
                try {
                    val sendTime = Clock.System.now().toEpochMilliseconds()
                    signalingService?.sendHeartbeat(sendTime)
                    val receiveTime = Clock.System.now().toEpochMilliseconds()
                    val rtt = (receiveTime - sendTime).coerceAtLeast(0L)
                    _latencyMs.value = rtt
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _latencyMs.value = -1L
                    throw ConnectionLostException("Heartbeat ping failed", e)
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun destroy() {
        logger.info("Disposing RPC Client...")
        _connectionState.value = ConnectionState.DISPOSING
        stopHeartbeat()
        connectionJob?.cancel()
        runCatching { client?.close() }
        client = null
        authService = null
        signalingService = null
        serverService = null
        scope.cancel()
    }
}