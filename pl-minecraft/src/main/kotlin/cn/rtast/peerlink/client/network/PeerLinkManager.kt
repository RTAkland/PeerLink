/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.client.network

import cn.rtast.peerlink.client.data.PendingJoinRequest
import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.mixin.ClientConnectionAccessor
import cn.rtast.peerlink.client.webrtc.RtcChannel
import cn.rtast.peerlink.client.webrtc.RtcHandshake
import cn.rtast.peerlink.data.play.JoinResponse
import cn.rtast.peerlink.data.play.PlayerInfo
import cn.rtast.peerlink.data.play.RoomState
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.webrtc.TurnCredentials
import cn.rtast.peerlink.service.AuthService
import cn.rtast.peerlink.service.MinecraftSignalingService
import dev.kastle.webrtc.PeerConnectionFactory
import dev.kastle.webrtc.RTCConfiguration
import dev.kastle.webrtc.RTCIceServer
import io.netty.channel.nio.NioEventLoopGroup
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl
import net.minecraft.client.multiplayer.LevelLoadTracker
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.handshake.HandshakeProtocols
import net.minecraft.network.protocol.login.LoginProtocols
import net.minecraft.network.protocol.login.ServerboundHelloPacket
import net.minecraft.server.network.ServerHandshakePacketListenerImpl
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class PeerLinkManager(
    private val rpcClient: RpcClient,
    private val localPlayer: PlayerInfo,
    @Suppress("DEPRECATION") private val nettyWorkerGroup: NioEventLoopGroup = NioEventLoopGroup(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {
    private val rtcFactory by lazy { PeerConnectionFactory() }
    private var eventListenJob: Job? = null
    var currentRoomState: RoomState? = null
        private set

    private val activeHandshakes = ConcurrentHashMap<Uuid, RtcHandshake>()
    private val activeRtcChannels = ConcurrentHashMap<Uuid, RtcChannel>()
    private var onCreateRoomEvent: (SignalEvent.JoinRequested) -> Unit = {}
    private val authService: AuthService
        get() = rpcClient.authService ?: throw IllegalStateException("RPC client is not connected")

    private val signalingService: MinecraftSignalingService
        get() = rpcClient.signalingService
            ?: throw IllegalStateException("RPC client is not connected")

    val pendingJoinRequests: Map<Uuid, PendingJoinRequest>
        field = ConcurrentHashMap<Uuid, PendingJoinRequest>()

    fun addPendingRequest(event: SignalEvent.JoinRequested, timeoutMs: Long = 30_000L) {
        val applicantId = event.applicantId
        pendingJoinRequests.remove(applicantId)?.timeoutJob?.cancel()
        val timeoutJob = scope.launch {
            delay(timeoutMs.milliseconds)
            runCatching { signalingService.respondJoinRequest(applicantId, false, "Request timed out") }
            pendingJoinRequests.remove(applicantId)
        }
        val request = PendingJoinRequest(applicantId, event.applicantName, System.currentTimeMillis(), timeoutJob)
        pendingJoinRequests[applicantId] = request
    }

    fun removePendingRequest(applicantId: Uuid) {
        pendingJoinRequests.remove(applicantId)?.timeoutJob?.cancel()
    }

    suspend fun initialize() {
        authService.registerIdentity(localPlayer)
        eventListenJob?.cancel()
        eventListenJob = signalingService.observeEvents().onEach { event -> handleSignalEvent(event) }.launchIn(scope)
    }

    suspend fun createRoom(onCreateRoomEvent: (SignalEvent.JoinRequested) -> Unit): RoomState {
        this.onCreateRoomEvent = onCreateRoomEvent
        leaveAndCleanup()
        val roomState = signalingService.createRoom()
        this.currentRoomState = roomState
        return roomState
    }

    suspend fun joinRoom(
        roomId: String,
        onResponse: (JoinResponse) -> Unit,
        onWaitingHostApproval: (SignalEvent.JoinRequested) -> Unit,
    ): Boolean {
        leaveAndCleanup()
        this.onCreateRoomEvent = onWaitingHostApproval
        val response = signalingService.joinRoom(roomId)
        onResponse(response)
        return when (response) {
            is JoinResponse.Accepted -> {
                startHandshake(
                    targetPlayerId = response.hostId,
                    isInitiator = true,
                    credentials = response.credentials
                )
                true
            }

            is JoinResponse.Rejected -> false
            is JoinResponse.Error -> false
            is JoinResponse.InvalidRoom -> false
        }
    }

    private suspend fun handleSignalEvent(event: SignalEvent) {
        when (event) {
            is SignalEvent.JoinRequested -> onCreateRoomEvent(event)
            is SignalEvent.TurnCredentialsIssued -> {
                startHandshake(
                    targetPlayerId = event.targetPlayerId,
                    isInitiator = false,
                    credentials = event.credentials
                )
            }

            is SignalEvent.MessageReceived -> {
                val handshake = activeHandshakes[event.fromPlayerId]
                handshake?.handleIncomingSignal(event.message)
            }

            is SignalEvent.PlayerJoined -> {}
            is SignalEvent.PlayerLeft -> closePeerConnection(event.playerId)
            is SignalEvent.PlayerKicked -> leaveAndCleanup()
            is SignalEvent.RoomClosed -> leaveAndCleanup()
        }
    }

    private fun startHandshake(targetPlayerId: Uuid, isInitiator: Boolean, credentials: TurnCredentials) {
        scope.launch {
            try {
                val rtcConfig = buildRtcConfig(credentials)
                val handshake = RtcHandshake(
                    factory = rtcFactory,
                    config = rtcConfig,
                    isInitiator = isInitiator,
                    localPlayerUuid = localPlayer.uuid,
                    targetPlayerUuid = targetPlayerId,
                    sendSignal = { message -> signalingService.sendSignal(targetPlayerId, message) },
                    scope = scope
                )

                activeHandshakes[targetPlayerId] = handshake
                val handshakeResult = handshake.start()
                bindToNettyPipeline(targetPlayerId, handshakeResult)
            } catch (_: Exception) {
                closePeerConnection(targetPlayerId)
            }
        }
    }

    private fun bindToNettyPipeline(targetPlayerId: Uuid, handshakeResult: RtcHandshake.HandshakeResult) {
        val rtcChannel = RtcChannel(handshakeResult)
        nettyWorkerGroup.register(rtcChannel).addListener { future ->
            if (!future.isSuccess) RtcChannel.dispose(handshakeResult)
            activeRtcChannels[targetPlayerId] = rtcChannel
            if (currentRoomState?.hostPlayerUuid == localPlayer.uuid) {
                val server = minecraft.singleplayerServer ?: return@addListener
                server.execute {
                    try {
                        val connection = createConnection(rtcChannel, PacketFlow.SERVERBOUND, null)
                        (connection as ClientConnectionAccessor).`peerlink$setChannel`(rtcChannel)
                        connection.setupInboundProtocol(
                            HandshakeProtocols.SERVERBOUND,
                            ServerHandshakePacketListenerImpl(server, connection)
                        )
                        server.connection.connections.add(connection)
                    } catch (_: Exception) {
                    }
                }
            } else {
                minecraft.execute {
                    if (minecraft.level != null || minecraft.singleplayerServer != null)
                        minecraft.disconnectWithProgressScreen(false)
                    val connection = createConnection(
                        rtcChannel, PacketFlow.CLIENTBOUND,
                        minecraft.debugOverlay.bandwidthLogger
                    )
                    // 在离线模式下使用模式并且将下方的Type设置为REALM的话断开连接会提示`无效的会话`
                    val serverData = ServerData("PeerLink", "peerlink-virtual-host", ServerData.Type.OTHER)
                    connection.initiateServerboundPlayConnection(
                        "peerlink-virtual-host", 0,
                        LoginProtocols.SERVERBOUND,
                        LoginProtocols.CLIENTBOUND,
                        ClientHandshakePacketListenerImpl(
                            connection, minecraft, serverData,
                            null, false, null, {},
                            LevelLoadTracker(), null,
                        ), false
                    )
                    connection.send(ServerboundHelloPacket(minecraft.user.name, minecraft.user.profileId))
                    Minecraft::class.java.getDeclaredField("pendingConnection").apply {
                        isAccessible = true
                        set(minecraft, connection)
                    }
                }
            }
        }
    }

    private fun buildRtcConfig(credentials: TurnCredentials): RTCConfiguration {
        val config = RTCConfiguration()
        credentials.stunServers.forEach { url ->
            config.iceServers.add(RTCIceServer().apply { urls.add(url) })
        }
        credentials.turnServers.forEach { url ->
            config.iceServers.add(RTCIceServer().apply {
                urls.add(url)
                username = credentials.username
                password = credentials.password
            })
        }
        return config
    }

    fun closePeerConnection(playerId: Uuid) {
        activeHandshakes.remove(playerId)?.dispose()
        activeRtcChannels.remove(playerId)?.close()
    }

    suspend fun leaveAndCleanup() {
        runCatching { signalingService.leaveRoom() }
        activeHandshakes.keys.toList().forEach { closePeerConnection(it) }
        activeHandshakes.clear()
        activeRtcChannels.clear()
        currentRoomState = null
    }

    fun destroy() {
        runBlocking { leaveAndCleanup() }
        eventListenJob?.cancel()
        scope.cancel()
        nettyWorkerGroup.shutdownGracefully()
        rtcFactory.dispose()
    }
}