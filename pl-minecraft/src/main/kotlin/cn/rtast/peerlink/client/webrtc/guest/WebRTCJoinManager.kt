/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.client.webrtc.guest

import cn.rtast.peerlink.client.data.JoinResult
import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.util.rpc.RpcManager
import cn.rtast.peerlink.client.util.showNotification
import cn.rtast.peerlink.client.webrtc.host.WebRTCHostManager
import cn.rtast.peerlink.data.play.JoinResponse
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component

object WebRTCJoinManager {
    private var currentClient: WebRTCClient? = null
    private var observeJob: Job? = null

    fun joinRoom(roomId: String, onResult: (JoinResult) -> Unit) {
        cancelAll()

        if (!preCheck()) {
            onResult(JoinResult.P2PInitializationFailed)
            return
        }
        val signalingService = RpcManager.minecraftSignalingService ?: run {
            onResult(JoinResult.P2PInitializationFailed)
            return
        }
        val parentScreen = minecraft.gui.screen()!!

        val client = WebRTCClient(
            scope = RpcManager.scope,
            signalingService = signalingService
        ) { channel ->
            WebRTCClientManager.setupWebRtcSession(channel)
            minecraft.execute {
                ConnectScreen.startConnecting(
                    parentScreen,
                    minecraft,
                    ServerAddress("$roomId.peerlink-virtual-host", 0),
                    ServerData("PeerLink", "peer-link", ServerData.Type.LAN),
                    false, null
                )
            }
        }
        currentClient = client

        observeJob = RpcManager.scope.launch {
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

    fun cancelAll() {
        observeJob?.cancel()
        observeJob = null
        WebRTCClientManager.reset()
        WebRTCHostManager.stopHosting()
        currentClient?.close()
        currentClient = null
    }

    fun preCheck(): Boolean {
        return if (RpcManager.minecraftSignalingService == null) {
            showNotification(
                Component.translatable("peerlink.joinGameFailed"),
                Component.translatable("peerlink.signalingServerNotConnected")
            )
            false
        } else true
    }
}