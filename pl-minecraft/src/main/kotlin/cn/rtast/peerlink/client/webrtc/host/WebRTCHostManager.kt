/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.client.webrtc.host

import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.mixin.ClientConnectionAccessor
import cn.rtast.peerlink.client.mixin.MinecraftServerAccessor
import cn.rtast.peerlink.client.util.network.ConnectionUtil
import cn.rtast.peerlink.client.util.rpc.RpcManager
import cn.rtast.peerlink.client.webrtc.WebRTCChannel
import cn.rtast.peerlink.data.webrtc.TurnCredentials
import cn.rtast.peerlink.data.play.RoomState
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import cn.rtast.peerlink.service.MinecraftSignalingService
import dev.kastle.webrtc.RTCDataChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.handshake.HandshakeProtocols
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerHandshakePacketListenerImpl
import net.minecraft.world.level.GameType
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

object WebRTCHostManager {
    private val activeSessions = ConcurrentHashMap<Uuid, WebRTCHostSession>()

    // 保存已准备就绪的玩家 TURN 凭证配置（key: 客户端 Player UUID）
    private val pendingTurnConfigs = ConcurrentHashMap<Uuid, TurnCredentials>()
    private var signalListenJob: Job? = null

    var currentRoomId: String? = null
        private set

    fun removeSession(clientUuid: Uuid) {
        activeSessions.remove(clientUuid)?.close()
        pendingTurnConfigs.remove(clientUuid)
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
                                RpcManager.rpcLogger.info("[PeerLink Host] 收到玩家 ${event.applicantName} (${event.applicantId}) 的加入申请")
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

    private fun handleIncomingSignal(
        scope: CoroutineScope,
        signalingService: MinecraftSignalingService,
        fromPlayerUuid: Uuid,
        message: SignalingMessage,
    ) {
        when (message.type) {
            SignalingMessage.SignalingType.Offer -> {
                val iceConfig = pendingTurnConfigs[fromPlayerUuid] ?: run {
                    RpcManager.rpcLogger.warn("[PeerLink Host] 收到未授权玩家 $fromPlayerUuid 的 Offer，已被安全拒之门外")
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
                val connection = ConnectionUtil.fromChannel(
                    rtcChannel, PacketFlow.SERVERBOUND, null
                )
                (connection as ClientConnectionAccessor).`peerlink$setChannel`(rtcChannel)
                connection.setupInboundProtocol(
                    HandshakeProtocols.SERVERBOUND,
                    ServerHandshakePacketListenerImpl(server, connection)
                )
                server.connection.connections.add(connection)
            } catch (e: Exception) {
                RpcManager.rpcLogger.error("[PeerLink Host] 注入客户端 DataChannel 到网路循环失败: ${e.message}", e)
            }
        }
    }

    @JvmStatic
    fun stopHosting() {
        signalListenJob?.cancel()
        signalListenJob = null
        activeSessions.values.forEach { it.close() }
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