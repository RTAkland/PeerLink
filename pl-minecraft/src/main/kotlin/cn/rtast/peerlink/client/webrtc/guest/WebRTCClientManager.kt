/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.webrtc.guest

import cn.rtast.peerlink.client.data.JoinResult
import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.plScope
import cn.rtast.peerlink.client.util.network.ConnectionInjector
import cn.rtast.peerlink.client.util.rpc.RpcManager
import cn.rtast.peerlink.client.util.showNotification
import cn.rtast.peerlink.client.webrtc.WebRTCChannel
import cn.rtast.peerlink.data.play.JoinResponse
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import dev.kastle.webrtc.RTCDataChannel
import dev.kastle.webrtc.RTCDataChannelBuffer
import dev.kastle.webrtc.RTCDataChannelObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl
import net.minecraft.client.multiplayer.LevelLoadTracker
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.login.LoginProtocols
import net.minecraft.network.protocol.login.ServerboundHelloPacket


object WebRTCClientManager {

    @Volatile
    private var activeWebRtcConnection: Connection? = null

    @JvmStatic
    @Volatile
    var activeDataChannel: RTCDataChannel? = null

    private var currentClient: WebRTCClient? = null
    private var observeJob: Job? = null


    fun setupWebRtcSession(dataChannel: RTCDataChannel) {
        this.activeDataChannel = dataChannel

        dataChannel.registerObserver(object : RTCDataChannelObserver {
            override fun onStateChange() {
//                val state = dataChannel.state
//                if (state == RTCDataChannelState.OPEN) {
//                    dataChannelReadyFuture.complete(null)
//                } else if (state == RTCDataChannelState.CLOSED) {
//                    dataChannelReadyFuture.completeExceptionally(IllegalStateException("DataChannel 已关闭"))
//                }
            }

            override fun onMessage(buffer: RTCDataChannelBuffer) {}
            override fun onBufferedAmountChange(previousAmount: Long) {}
        })
    }

    fun joinRoom(roomId: String, onResult: (JoinResult) -> Unit) {
        cancelAll()
        val signalingService = RpcManager.minecraftSignalingService ?: run {
            onResult(JoinResult.SignalingServerNotConnected)
            return
        }
        val client = WebRTCClient(
            scope = plScope,
            signalingService = signalingService
        ) { peerConnection, channel ->
            setupWebRtcSession(channel)
            minecraft.execute {
                if (minecraft.level != null || minecraft.singleplayerServer != null)
                    minecraft.disconnectWithProgressScreen(false)
                val connection = ConnectionInjector.fromChannel(
                    WebRTCChannel(peerConnection, channel),
                    PacketFlow.CLIENTBOUND,
                    minecraft.debugOverlay.bandwidthLogger
                )
                this.activeWebRtcConnection = connection
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
        currentClient = client

        observeJob = plScope.launch {
            launch {
                signalingService.observeEvents().collect { event ->
                    when (event) {
                        is SignalEvent.MessageReceived -> {
                            when (event.message.type) {
                                SignalingMessage.SignalingType.Answer -> currentClient?.handleRemoteAnswer(event.message.payload)
                                SignalingMessage.SignalingType.ICE -> currentClient?.handleRemoteCandidate(event.message.payload)
                                else -> {}
                            }
                        }

                        is SignalEvent.PlayerKicked -> {
                            showNotification(
                                Component.translatable("peerlink.p2p.failed"),
                                Component.literal(event.reason ?: "Kicked from room by host.")
                            )
                            cancelAll()
                        }

                        else -> {}
                    }
                }
            }
            minecraft.execute { onResult(JoinResult.PendingJoinRequest) }
            val response = try {
                signalingService.joinRoom(roomId)
            } catch (e: Exception) {
                JoinResponse.Error(e.message ?: "RPC network error")
            }
            minecraft.execute {
                when (response) {
                    is JoinResponse.Accepted -> {
                        onResult(JoinResult.Accepted)
                        currentClient?.startP2PConnect(response.hostId, response.credentials)
                    }

                    is JoinResponse.Rejected -> {
                        onResult(JoinResult.RejectJoin)
                        cancelAll()
                    }

                    is JoinResponse.Error -> {
                        onResult(JoinResult.InvalidRoomId)
                        cancelAll()
                    }

                    JoinResponse.InvalidRoom -> {
                        onResult(JoinResult.InvalidRoomId)
                        cancelAll()
                    }
                }
            }
        }
    }

    @JvmStatic
    fun cancelAll() {
        observeJob?.cancel()
        observeJob = null
        currentClient?.close()
        currentClient = null
        try {
            activeWebRtcConnection?.disconnect(Component.literal("WebRTC Session Reset"))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activeWebRtcConnection = null
            RpcManager.rpcLogger.info("[WebRTCClientManager] 已重置 WebRTC 客户端状态")
        }
    }
}