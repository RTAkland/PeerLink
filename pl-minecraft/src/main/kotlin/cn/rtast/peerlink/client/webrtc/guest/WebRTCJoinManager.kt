/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.client.webrtc.guest

import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.util.RpcManager
import cn.rtast.peerlink.client.util.showNotification
import cn.rtast.peerlink.client.webrtc.host.WebRTCHostManager
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
    fun joinRoom(roomId: String, onResult: (Boolean) -> Unit) {
        if (!preCheck()) {
            onResult(false)
            return
        }
        val signalingService = RpcManager.minecraftSignalingService ?: run {
            onResult(false)
            return
        }
        val serverSignalingService = RpcManager.serverSignalingService ?: run {
            onResult(false)
            return
        }
        val parentScreen = minecraft.gui.screen()!!

        observeJob?.cancel()
        observeJob = RpcManager.scope.launch {
            signalingService.observeEvents().collect { event ->
                if (event is SignalEvent.SignalingReceived) {
                    when (event.message.type) {
                        SignalingMessage.SignalingType.Answer -> currentClient?.handleRemoteAnswer(event.message.payload)
                        SignalingMessage.SignalingType.ICE -> currentClient?.handleRemoteCandidate(event.message.payload)
                        else -> {}
                    }
                }
            }
        }

        RpcManager.scope.launch {
            currentClient = WebRTCClient(
                scope = RpcManager.scope,
                signalingService = signalingService,
                serverSignalingService = serverSignalingService,
                roomId = roomId
            ) { channel ->
                WebRTCClientManager.setupWebRtcSession(channel)
                minecraft.execute {
                    ConnectScreen.startConnecting(
                        parentScreen,
                        minecraft,
                        ServerAddress("$roomId.peerlink-vitural-host", 0),
                        ServerData("PeerLink", "peer-link", ServerData.Type.LAN),
                        false, null
                    )
                }
            }

            val success = currentClient?.startConnect() ?: false
            minecraft.execute { onResult(success) }
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