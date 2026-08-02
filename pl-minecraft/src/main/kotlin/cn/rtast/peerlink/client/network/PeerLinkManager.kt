/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */

package cn.rtast.peerlink.client.network

import cn.rtast.peerlink.client.data.ConnectResult
import cn.rtast.peerlink.client.data.PendingJoinRequest
import cn.rtast.peerlink.client.gui.HostJoinRequestNotifier
import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.mixin.MinecraftServerAccessor
import cn.rtast.peerlink.client.util._answer
import cn.rtast.peerlink.client.util._offer
import cn.rtast.peerlink.client.webrtc.RtcChannel
import cn.rtast.peerlink.client.webrtc.RtcHandshake
import cn.rtast.peerlink.client.webrtc.deserializeCandidate
import cn.rtast.peerlink.client.webrtc.serializeCandidate
import cn.rtast.peerlink.data.play.JoinResponse
import cn.rtast.peerlink.data.play.RoomState
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import cn.rtast.peerlink.data.webrtc.TurnCredentials
import com.mojang.logging.LogUtils
import dev.kastle.webrtc.PeerConnectionFactory
import dev.kastle.webrtc.RTCConfiguration
import dev.kastle.webrtc.RTCIceCandidate
import dev.kastle.webrtc.RTCIceServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl
import net.minecraft.client.multiplayer.LevelLoadTracker
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.login.LoginProtocols
import net.minecraft.network.protocol.login.ServerboundHelloPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerHandshakePacketListenerImpl
import net.minecraft.world.level.GameType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class PeerLinkManager(
    private val rpcClient: RpcClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {
    private val logger = LogUtils.getLogger()
    private val factory by lazy { PeerConnectionFactory() }
    private val handshakes = ConcurrentHashMap<Uuid, RtcHandshake>()
    private val pendingIceCandidates = ConcurrentHashMap<Uuid, ConcurrentLinkedQueue<RTCIceCandidate>>()
    private val remoteDescriptionReadyMap = ConcurrentHashMap<Uuid, AtomicBoolean>()
    private var eventListenJob: Job? = null
    private val pendingRequests = ConcurrentHashMap<Uuid, PendingJoinRequest>()
    private val _pendingRequestsFlow = MutableStateFlow<List<PendingJoinRequest>>(emptyList())
    val pendingRequestsFlow = _pendingRequestsFlow.asStateFlow()
    private val turnCredentialsMap = ConcurrentHashMap<Uuid, TurnCredentials>()
    private var onConnectResult: (ConnectResult) -> Unit = {}

    suspend fun initialize() {
        eventListenJob?.cancel()
        withContext(Dispatchers.Default) { factory }
        eventListenJob = rpcClient.signalingService!!.observeEvents()
            .onEach { event -> handleSignalingEvent(event) }
            .catch { e -> logger.error("Signaling observeEvents flow failed!", e) }
            .launchIn(scope)
    }

    fun destroy() {
        pendingIceCandidates.values.forEach { it.clear() }
        remoteDescriptionReadyMap.clear()
        handshakes.forEach { it.value.abort("Aborted") }
        pendingRequests.clear()
        factory.dispose()
    }

    private fun handleSignalingEvent(event: SignalEvent) {
        when (event) {
            is SignalEvent.JoinRequested -> {
                val applicantUuid = event.applicantId
                val applicantName = event.applicantName
                HostJoinRequestNotifier.showNotification(applicantName)
                scope.launch {
                    runCatching {
                        val request = PendingJoinRequest(applicantUuid, applicantName)
                        pendingRequests[applicantUuid] = request
                        updateRequestsFlow()
                        val approved = withTimeoutOrNull(30_000.milliseconds) {
                            request.responseDeferred.await()
                        } ?: false
                        pendingRequests.remove(applicantUuid)
                        updateRequestsFlow()
                        if (approved) rpcClient.signalingService?.respondJoinRequest(applicantUuid, true)
                        else rpcClient.signalingService?.respondJoinRequest(applicantUuid, false)
                    }.onFailure {  }
                }
            }

            is SignalEvent.MessageReceived -> {
                val message = event.message
                val senderUuid = message.senderPlayerUuid
                scope.launch {
                    runCatching {
                        when (message.type) {
                            SignalingMessage.SignalingType.Offer -> {
                                val credentials = rpcClient.signalingService?.acquireTurnCredentials()
                                    ?: throw IllegalStateException("Signaling Server Unavailable")
                                val rtcConfig = createRTCConfig(credentials)
                                val handshake = getOrCreateHandshake(senderUuid, isInitiator = false, rtcConfig)
                                val answerSdp = handshake.acceptOffer(message.payload)
                                markRemoteDescriptionReady(senderUuid, handshake)
                                rpcClient.signalingService?.sendSignal(senderUuid, _answer(senderUuid, answerSdp))
                                scope.launch {
                                    runCatching {
                                        val result = handshake.awaitResult()
                                        handshakes.remove(senderUuid, handshake)
                                        startHost(result)
                                    }.onFailure { e ->
                                        handshake.abort("Host connection failed: ${e.message}")
                                    }
                                }
                            }

                            SignalingMessage.SignalingType.Answer -> {
                                val handshake = handshakes[senderUuid]
                                if (handshake != null) {
                                    handshake.applyAnswer(message.payload)
                                    markRemoteDescriptionReady(senderUuid, handshake)
                                } else logger.warn("Received ANSWER from $senderUuid but no handshake found")
                            }

                            SignalingMessage.SignalingType.Ice -> {
                                val candidate = message.payload.deserializeCandidate()
                                val handshake = handshakes[senderUuid]
                                val isReady = remoteDescriptionReadyMap[senderUuid]?.get() == true
                                if (handshake != null && isReady) {
                                    handshake.addRemoteIceCandidate(candidate)
                                } else {
                                    pendingIceCandidates.computeIfAbsent(senderUuid) {
                                        ConcurrentLinkedQueue()
                                    }.add(candidate)
                                }
                            }
                        }
                    }.onFailure { e -> logger.error("Failed to handle signal ${message.type} from $senderUuid: ${e.message}") }
                }
            }

            is SignalEvent.PlayerJoined -> logger.info("Player ${event.player.uuid} joined signaling room")
            is SignalEvent.PlayerLeft -> cleanupPeer(event.playerId)
            is SignalEvent.RoomClosed -> abortAll("Room closed")
            is SignalEvent.TurnCredentialsIssued -> turnCredentialsMap[event.targetPlayerId] = event.credentials
            is SignalEvent.JoinAwaiting -> this.onConnectResult.invoke(ConnectResult.Awaiting(event.host))
            is SignalEvent.PlayerKicked -> {
                val kickedPlayerUuid = event.playerId
                val myUuid = minecraft.user.profileId.toKotlinUuid()
                if (kickedPlayerUuid == myUuid) {
                    logger.warn("You were kicked from the room. Reason: ${event.reason}")
                    abortAll("Kicked by host: ${event.reason}")
                    minecraft.execute {
                        if (minecraft.level != null || minecraft.singleplayerServer != null) {
                            minecraft.disconnectWithProgressScreen(false)
                            minecraft.gui.setScreen(
                                DisconnectedScreen(
                                    TitleScreen(),
                                    Component.translatable("peerlink.disconnected"),
                                    Component.translatable("peerlink.kickedByHost")
                                )
                            )
                        }
                    }
                } else {
                    cleanupPeer(kickedPlayerUuid)
                    val server = minecraft.singleplayerServer
                    if (server != null && server.isPublished) {
                        server.execute {
                            val playerNode = server.playerList.getPlayer(kickedPlayerUuid.toJavaUuid())
                            playerNode?.connection?.disconnect(Component.translatable("peerlink.kickedByHost"))
                        }
                    }
                }
            }
        }
    }

    fun acceptJoinRequest(playerUuid: Uuid) {
        val request = pendingRequests[playerUuid]
        request?.responseDeferred?.complete(true)
    }

    fun rejectJoinRequest(playerUuid: Uuid) {
        val request = pendingRequests[playerUuid]
        request?.responseDeferred?.complete(false)
    }

    private fun updateRequestsFlow() {
        _pendingRequestsFlow.value = pendingRequests.values.toList()
    }

    fun connect(roomId: String, onResult: (ConnectResult) -> Unit) = scope.launch {
        this@PeerLinkManager.onConnectResult = onResult
        val signaling = rpcClient.signalingService
        if (signaling == null) {
            onResult(ConnectResult.SignalingError)
            return@launch
        }

        runCatching {
            when (val response = signaling.requestJoin(roomId)) {
                is JoinResponse.Accepted -> {
                    val hostUuid = response.hostId
                    turnCredentialsMap[hostUuid] = response.credentials
                    val rtcConfig = createRTCConfig(response.credentials)
                    val handshake = getOrCreateHandshake(hostUuid, isInitiator = true, rtcConfig)
                    val offer = handshake.createOffer()
                    signaling.sendSignal(hostUuid, _offer(hostUuid, offer))
                    val result = withTimeout(20.seconds) { handshake.awaitResult() }
                    handshakes.remove(hostUuid, handshake)
                    startJoin(result)
                    onResult(ConnectResult.Accepted)
                }

                is JoinResponse.Rejected -> onResult(ConnectResult.Rejected)
                is JoinResponse.Error -> onResult(ConnectResult.Failed)
                is JoinResponse.InvalidRoom -> onResult(ConnectResult.Invalid)
            }
        }.onFailure { e ->
            logger.error("Failed to connect to room $roomId: ${e.message}")
            onResult(ConnectResult.Failed)
        }
    }

    suspend fun host(
        server: MinecraftServer,
        gameMode: GameType,
        allowCommands: Boolean,
        onlineMode: Boolean,
        onResponse: (RoomState) -> Unit,
    ) {
        if (!server.isPublished) {
            server.publishServer(
                MinecraftServer.MultiplayerScope.LAN,
                gameMode, allowCommands, Random.nextInt(20000, 30000)
            )
            (server as MinecraftServerAccessor).`peerlink$setOnlineMode`(onlineMode)
        }
        val signaling = rpcClient.signalingService
        onResponse(signaling!!.createRoom())
    }

    private suspend fun markRemoteDescriptionReady(targetUuid: Uuid, handshake: RtcHandshake) {
        remoteDescriptionReadyMap.computeIfAbsent(targetUuid) { AtomicBoolean() }.set(true)
        val queue = pendingIceCandidates.remove(targetUuid) ?: return
        while (!queue.isEmpty()) {
            val candidate = queue.poll() ?: break
            runCatching { handshake.addRemoteIceCandidate(candidate) }
        }
    }

    private fun cleanupPeer(targetUuid: Uuid) {
        handshakes.remove(targetUuid)?.abort("Peer disconnected")
        pendingIceCandidates.remove(targetUuid)
        remoteDescriptionReadyMap.remove(targetUuid)
        turnCredentialsMap.remove(targetUuid)
    }

    private fun abortAll(reason: String) {
        handshakes.forEach { (uuid, handshake) ->
            handshake.abort(reason)
            cleanupPeer(uuid)
        }
    }

    private fun startJoin(result: RtcHandshake.HandshakeResult) {
        val rtcChannel = RtcChannel(result)
        if (minecraft.level != null || minecraft.singleplayerServer != null)
            minecraft.disconnectWithProgressScreen(false)
        val connection = createConnection(
            rtcChannel, PacketFlow.CLIENTBOUND,
            minecraft.debugOverlay.bandwidthLogger
        )
        val serverData = ServerData("PeerLink", "peerlink-virtual-host", ServerData.Type.OTHER)
        minecraft.execute {
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
            runCatching {
                Minecraft::class.java.getDeclaredField("pendingConnection").apply {
                    isAccessible = true
                    set(minecraft, connection)
                }
            }
        }
    }

    private fun startHost(result: RtcHandshake.HandshakeResult) {
        val rtcChannel = RtcChannel(result)
        val server = minecraft.singleplayerServer ?: return
        server.execute {
            val connection = createConnection(rtcChannel, PacketFlow.SERVERBOUND, null)
            connection.setListenerForServerboundHandshake(ServerHandshakePacketListenerImpl(server, connection))
            server.connection.connections.add(connection)
        }
    }

    private fun createRTCConfig(credentials: TurnCredentials): RTCConfiguration = RTCConfiguration().apply {
        iceServers.add(RTCIceServer().apply {
            urls.addAll(credentials.stunServers)
        })
        credentials.turnServers.forEach { turn ->
            iceServers.add(RTCIceServer().apply {
                urls.addAll(turn.urls)
                username = turn.username
                password = turn.password
            })
        }
    }

    private fun getOrCreateHandshake(
        targetUuid: Uuid,
        isInitiator: Boolean,
        rtcConfig: RTCConfiguration,
    ): RtcHandshake = handshakes.computeIfAbsent(targetUuid) {
        RtcHandshake(factory, rtcConfig, isInitiator) {
            scope.launch {
                rpcClient.signalingService?.sendSignal(
                    targetUuid, SignalingMessage(
                        minecraft.user.profileId.toKotlinUuid(),
                        targetUuid, SignalingMessage.SignalingType.Ice,
                        it.serializeCandidate()
                    )
                )
            }
        }
    }
}