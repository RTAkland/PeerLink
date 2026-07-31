/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.client.webrtc.host

import cn.rtast.peerlink.client.data.PendingJoinRequest
import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.mixin.ClientConnectionAccessor
import cn.rtast.peerlink.client.mixin.MinecraftServerAccessor
import cn.rtast.peerlink.client.util.HostPlayerStorage
import cn.rtast.peerlink.client.util.network.ConnectionInjector
import cn.rtast.peerlink.client.util.rpc.RpcManager
import cn.rtast.peerlink.client.webrtc.WebRTCChannel
import cn.rtast.peerlink.data.play.RoomState
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import cn.rtast.peerlink.data.webrtc.TurnCredentials
import cn.rtast.peerlink.service.MinecraftSignalingService
import dev.kastle.webrtc.RTCDataChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.handshake.HandshakeProtocols
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerHandshakePacketListenerImpl
import net.minecraft.world.level.GameType
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

object WebRTCHostManager {
    private val activeSessions = ConcurrentHashMap<Uuid, WebRTCHostSession>()
    private val pendingTurnConfigs = ConcurrentHashMap<Uuid, TurnCredentials>()
    private var signalListenJob: Job? = null

    @JvmStatic
    var currentRoomId: String? = null
        private set
    val pendingJoinRequests = ConcurrentHashMap<Uuid, PendingJoinRequest>()

    fun removeSession(clientUuid: Uuid) {
        activeSessions.remove(clientUuid)?.close()
        pendingTurnConfigs.remove(clientUuid)
        removePendingRequest(clientUuid)
    }

    fun removePendingRequest(clientUuid: Uuid) {
        pendingJoinRequests.remove(clientUuid)?.timeoutJob?.cancel()
    }

    fun startHostingRoom(
        scope: CoroutineScope,
        signalingService: MinecraftSignalingService,
        onResult: (Result<RoomState>) -> Unit,
    ) {
        scope.launch {
            try {
                val roomState = signalingService.createRoom()
                currentRoomId = roomState.roomId
                RpcManager.rpcLogger.info("房间创建成功 RoomId: ${roomState.roomId}")
                signalListenJob?.cancel()
                signalListenJob = launch {
                    signalingService.observeEvents().collect { event ->
                        when (event) {
                            is SignalEvent.TurnCredentialsIssued -> {
                                val targetPlayerUuid = event.targetPlayerId
                                pendingTurnConfigs[targetPlayerUuid] = event.credentials
                                removePendingRequest(targetPlayerUuid)
                                RpcManager.rpcLogger.info("[PeerLink Host] 收到玩家 $targetPlayerUuid 的 TURN 凭证，准备接收 Offer")
                            }

                            is SignalEvent.MessageReceived -> {
                                handleIncomingSignal(
                                    scope = scope,
                                    signalingService = signalingService,
                                    fromPlayerUuid = event.fromPlayerId,
                                    message = event.message
                                )
                            }

                            is SignalEvent.PlayerLeft -> {
                                removeSession(event.playerId)
                                RpcManager.rpcLogger.info("[PeerLink Host] 玩家 ${event.playerId} 已离开/被踢出，已清理 WebRTC 会话")
                            }

                            is SignalEvent.JoinRequested -> {
                                handleJoinRequest(
                                    scope = this,
                                    applicantId = event.applicantId,
                                    applicantName = event.applicantName
                                )
                            }

                            else -> {}
                        }
                    }
                }

                minecraft.execute { onResult.invoke(Result.success(roomState)) }
            } catch (e: Exception) {
                RpcManager.rpcLogger.error("[PeerLink Host] 开启房间失败: ${e.message}", e)
                minecraft.execute { onResult.invoke(Result.failure(e)) }
            }
        }
    }

    private fun handleJoinRequest(
        scope: CoroutineScope,
        applicantId: Uuid,
        applicantName: String,
    ) {
        pendingJoinRequests[applicantId]?.timeoutJob?.cancel()
        val timeoutJob = scope.launch {
            delay(30_000L.milliseconds)
            pendingJoinRequests.remove(applicantId) != null
        }

        val request = PendingJoinRequest(
            applicantId = applicantId,
            applicantName = applicantName,
            timeoutJob = timeoutJob
        )
        pendingJoinRequests[applicantId] = request
    }

    private fun handleIncomingSignal(
        scope: CoroutineScope,
        signalingService: MinecraftSignalingService,
        fromPlayerUuid: Uuid,
        message: SignalingMessage,
    ) {
        when (message.type) {
            SignalingMessage.SignalingType.Offer -> {
                val iceConfig = pendingTurnConfigs[fromPlayerUuid] ?: run {
                    return
                }

                val session = WebRTCHostSession(
                    clientPlayerUuid = fromPlayerUuid,
                    scope = scope,
                    signalingService = signalingService,
                    iceConfig = iceConfig
                ) { dataChannel -> injectClientDataChannelToIntegratedServer(dataChannel) }
                activeSessions[fromPlayerUuid] = session
                session.handleOfferAndCreateAnswer(message.payload)
            }

            SignalingMessage.SignalingType.ICE -> {
                activeSessions[fromPlayerUuid]?.handleRemoteCandidate(message.payload)
            }

            SignalingMessage.SignalingType.Answer -> {}
        }
    }

    private fun injectClientDataChannelToIntegratedServer(dataChannel: RTCDataChannel) {
        val server = minecraft.singleplayerServer ?: return
        server.execute {
            try {
                val rtcChannel = WebRTCChannel(dataChannel)
                val connection = ConnectionInjector.fromChannel(
                    rtcChannel, PacketFlow.SERVERBOUND, null
                )
                (connection as ClientConnectionAccessor).`peerlink$setChannel`(rtcChannel)
                connection.setupInboundProtocol(
                    HandshakeProtocols.SERVERBOUND,
                    ServerHandshakePacketListenerImpl(server, connection)
                )
                server.connection.connections.add(connection)
            } catch (_: Exception) {
            }
        }
    }

    @JvmStatic
    fun stopHosting(terminate: Boolean = false) {
        signalListenJob?.cancel()
        signalListenJob = null
        pendingJoinRequests.values.forEach { it.timeoutJob.cancel() }
        pendingJoinRequests.clear()
        activeSessions.values.forEach { it.close(terminate) }
        activeSessions.clear()
        pendingTurnConfigs.clear()
        currentRoomId = null
    }

    fun openWebRTCRoom(
        coroutineScope: CoroutineScope,
        signalingService: MinecraftSignalingService,
        onlineMode: Boolean,
        allowCommands: Boolean,
        gameMode: GameType,
        onResult: (Result<RoomState>) -> Unit,
    ) {
        HostPlayerStorage.init()
        startHostingRoom(
            scope = coroutineScope,
            signalingService = signalingService,
            onResult = onResult
        )
        val server = minecraft.singleplayerServer ?: return
        if (!server.isPublished) {
            (server as MinecraftServerAccessor).`peerlink$setOnlineMode`(onlineMode)
            val success = server.publishServer(
                MinecraftServer.MultiplayerScope.LAN,
                gameMode,
                allowCommands,
                (20000..40000).random()
            )
            if (!success) return
        }
    }
}