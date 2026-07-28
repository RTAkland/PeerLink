/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.webrtc.guest

import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.util.RpcManager
import cn.rtast.peerlink.data.play.SignalEvent
import cn.rtast.peerlink.data.play.SignalingMessage
import kotlinx.coroutines.launch
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress

object WebRTCJoinManager {
    private var currentClient: WebRTCClient? = null

    fun joinRoom(roomId: String) {
        val signalingService = RpcManager.minecraftSignalingService ?: return
        val serverSignalingService = RpcManager.serverSignalingService ?: return

        RpcManager.scope.launch {
            launch {
                signalingService.observeEvents().collect { event ->
                    when (event) {
                        is SignalEvent.SignalingReceived -> {
                            when (event.message.type) {
                                SignalingMessage.SignalingType.Answer -> currentClient?.handleRemoteAnswer(event.message.payload)
                                SignalingMessage.SignalingType.ICE -> currentClient?.handleRemoteCandidate(event.message.payload)
                                else -> {}
                            }
                        }

                        else -> {}
                    }
                }
            }

            currentClient = WebRTCClient(
                scope = RpcManager.scope,
                signalingService = signalingService,
                serverSignalingService = serverSignalingService,
                roomId = roomId
            ) { dataChannel ->
                WebRTCClientManager.setupWebRtcSession(dataChannel)
                minecraft.execute {
                    ConnectScreen.startConnecting(
                        minecraft.gui.screen()!!,
                        minecraft,
                        ServerAddress("peerlink-$roomId", 0),
                        ServerData("PeerLink", "peer-link", ServerData.Type.LAN),
                        false, null
                    )
                }
            }.also { it.startConnect() }
        }
    }
}