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
import cn.rtast.peerlink.data.play.IntentType
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

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
            signalingService = signalingService,
            roomId = roomId
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

        val isTerminalStateReached = AtomicBoolean(false)
        fun dispatchResult(result: JoinResult) = minecraft.execute { onResult(result) }

        observeJob = RpcManager.scope.launch {
            signalingService.observeEvents().collect { event ->
                when (event) {
                    is SignalEvent.TurnCredentialsIssued -> {
                        isTerminalStateReached.set(true)
                        dispatchResult(JoinResult.Accepted)
                        currentClient?.startP2PConnect(event.targetPlayerId, event.credentials)
                    }

                    is SignalEvent.MessageReceived -> {
                        when (event.message.type) {
                            SignalingMessage.SignalingType.Answer -> currentClient?.handleRemoteAnswer(event.message.payload)
                            SignalingMessage.SignalingType.ICE -> currentClient?.handleRemoteCandidate(event.message.payload)
                            else -> {}
                        }
                    }

                    is SignalEvent.IntentResult -> {
                        when (event.intentType) {
                            IntentType.JOIN_REQUEST -> {
                                if (!event.success) {
                                    isTerminalStateReached.set(true)
                                    dispatchResult(JoinResult.InvalidRoomId)
                                    println("invalid room id")
                                }
                            }

                            IntentType.REJECT_JOIN -> {
                                isTerminalStateReached.set(true)
                                dispatchResult(JoinResult.RejectJoin)
                            }

                            else -> {}
                        }
                    }

                    is SignalEvent.PlayerKicked -> {
                        isTerminalStateReached.set(true)
                        showNotification(
                            Component.translatable("peerlink.p2p.failed"),
                            Component.literal(event.reason ?: "Kicked from room by host.")
                        )
                    }

                    else -> {}
                }
            }
        }

        runBlocking {
            val requestSent = client.requestJoin()
            if (requestSent) {
                if (!isTerminalStateReached.get()) {
                    dispatchResult(JoinResult.PendingJoinRequest)
                }
            } else {
                isTerminalStateReached.set(true)
                dispatchResult(JoinResult.JoinRequestIntentFailed)
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