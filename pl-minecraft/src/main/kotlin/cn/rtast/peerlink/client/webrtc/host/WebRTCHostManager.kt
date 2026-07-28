/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.webrtc.host

import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.mixin.ClientConnectionChannelAccessor
import cn.rtast.peerlink.client.mixin.MinecraftServerAccessor
import cn.rtast.peerlink.client.network.WebRTCNettyHandler
import cn.rtast.peerlink.data.ICEServerConfig
import cn.rtast.peerlink.data.play.RoomState
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import cn.rtast.peerlink.service.MinecraftSignalingService
import cn.rtast.peerlink.service.ServerSignalingService
import dev.onvoid.webrtc.RTCDataChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.GameType
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

object WebRTCHostManager {
    private val activeSessions = ConcurrentHashMap<Uuid, WebRTCHostSession>()
    private var signalListenJob: Job? = null
    var currentRoomId: String? = null
        private set

    fun removeSession(clientUuid: Uuid) {
        activeSessions.remove(clientUuid)
    }

    fun startHostingRoom(
        scope: CoroutineScope,
        signalingService: MinecraftSignalingService,
        serverSignalingService: ServerSignalingService,
        onResult: (Result<RoomState>) -> Unit,
    ) {
        scope.launch {
            try {
                val iceConfig = serverSignalingService.acquireICEServerConfig()
                val roomState = signalingService.createRoom()
                currentRoomId = roomState.roomId
                println("[Host] 房间创建成功，RoomId: ${roomState.roomId}")
                signalListenJob = launch {
                    signalingService.observeEvents().collect { event ->
                        if (event is SignalEvent.SignalingReceived) {
                            handleIncomingSignal(
                                scope = scope,
                                signalingService = signalingService,
                                iceConfig = iceConfig,
                                fromPlayerUuid = event.fromPlayerId,
                                message = event.message
                            )
                        }
                    }
                }
                minecraft.execute { onResult.invoke(Result.success(roomState)) }
            } catch (e: Exception) {
                e.printStackTrace()
                minecraft.execute { onResult.invoke(Result.failure(e)) }
            }
        }
    }

    private fun handleIncomingSignal(
        scope: CoroutineScope,
        signalingService: MinecraftSignalingService,
        iceConfig: ICEServerConfig,
        fromPlayerUuid: Uuid,
        message: SignalingMessage,
    ) {
        val roomId = currentRoomId ?: return
        when (message.type) {
            SignalingMessage.SignalingType.Offer -> {
                val session = WebRTCHostSession(
                    clientPlayerUuid = fromPlayerUuid,
                    scope = scope,
                    signalingService = signalingService,
                    roomId = roomId,
                    iceConfig = iceConfig
                ) { dataChannel -> injectClientDataChannelToIntegratedServer(dataChannel) }
                activeSessions[fromPlayerUuid] = session
                session.handleOfferAndCreateAnswer(message.payload)
            }

            SignalingMessage.SignalingType.ICE -> activeSessions[fromPlayerUuid]?.handleRemoteCandidate(message.payload)
            SignalingMessage.SignalingType.Answer -> {}
        }
    }

    private fun injectClientDataChannelToIntegratedServer(dataChannel: RTCDataChannel) {
        val server = minecraft.singleplayerServer ?: return
        server.execute {
            val embeddedChannel = io.netty.channel.embedded.EmbeddedChannel()
            val nettyHandler = WebRTCNettyHandler(dataChannel)
            embeddedChannel.pipeline().addLast("webrtc_bridge", nettyHandler)
            val connection = Connection(PacketFlow.SERVERBOUND)
            (connection as ClientConnectionChannelAccessor).`peerlink$setChannel`(embeddedChannel)
            server.connection.connections.add(connection)
        }
    }

    fun stopHosting() {
        signalListenJob?.cancel()
        activeSessions.values.forEach { it.close() }
        activeSessions.clear()
        currentRoomId = null
    }

    fun openWebRTCRoom(
        coroutineScope: CoroutineScope,
        signalingService: MinecraftSignalingService,
        serverSignalingService: ServerSignalingService,
        onResult: (Result<RoomState>) -> Unit,
    ) {
        val server = minecraft.singleplayerServer
        if (server == null) {
            println("[PeerLink] 只能在单人世界内开启 WebRTC 房间")
            return
        }

        if (!server.isPublished) {
            val lanPort = 37868
            (server as MinecraftServerAccessor).`peerlink$setOnlineMode`(false)
            val success =
                server.publishServer(MinecraftServer.MultiplayerScope.LAN, GameType.DEFAULT_MODE, true, lanPort)
            if (!success) {
                println("[PeerLink] LAN 服务开启失败")
                return
            }
        }
        startHostingRoom(
            scope = coroutineScope,
            signalingService = signalingService,
            serverSignalingService = serverSignalingService,
            onResult = onResult
        )
    }
}