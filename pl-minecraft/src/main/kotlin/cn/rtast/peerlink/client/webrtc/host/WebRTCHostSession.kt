/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


@file:OptIn(ExperimentalAtomicApi::class)

package cn.rtast.peerlink.client.webrtc.host

import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.mixin.ClientConnectionAccessor
import cn.rtast.peerlink.client.util.network.ConnectionInjector
import cn.rtast.peerlink.client.util.rpc.RpcManager
import cn.rtast.peerlink.client.util.rpc.deserializeCandidate
import cn.rtast.peerlink.client.util.rpc.serializeCandidate
import cn.rtast.peerlink.client.webrtc.WebRTCChannel
import cn.rtast.peerlink.data.play.SignalingMessage
import cn.rtast.peerlink.data.webrtc.TurnCredentials
import cn.rtast.peerlink.service.MinecraftSignalingService
import dev.kastle.webrtc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.handshake.HandshakeProtocols
import net.minecraft.server.network.ServerHandshakePacketListenerImpl
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class WebRTCHostSession(
    val clientPlayerUuid: Uuid,
    private val scope: CoroutineScope,
    private val signalingService: MinecraftSignalingService,
    private val iceConfig: TurnCredentials,
) {
    companion object {
        val sharedPeerFactory by lazy { PeerConnectionFactory() }
    }

    private var peerConnection: RTCPeerConnection? = null
    private val pendingCandidates = mutableListOf<RTCIceCandidate>()
    private var isRemoteSdpSet = false
    private var dataChannel: RTCDataChannel? = null
    private val isClosed = AtomicBoolean(false)

    fun handleOfferAndCreateAnswer(sdpOffer: String) {
        val rtcConfig = RTCConfiguration().apply {
            val stunServer = RTCIceServer().apply { urls.addAll(iceConfig.stunServers) }
            val turnServer = RTCIceServer().apply {
                urls.addAll(iceConfig.turnServers)
                username = iceConfig.username
                password = iceConfig.password
            }
            iceServers.add(stunServer)
            iceServers.add(turnServer)
        }

        peerConnection = sharedPeerFactory.createPeerConnection(rtcConfig, object : PeerConnectionObserver {
            override fun onIceCandidate(candidate: RTCIceCandidate) {
                scope.launch {
                    runCatching {
                        val candidateJson = serializeCandidate(candidate)
                        signalingService.sendSignal(
                            clientPlayerUuid,
                            SignalingMessage(
                                senderPlayerUuid = minecraft.gameProfile.id.toKotlinUuid(),
                                targetPlayerUuid = clientPlayerUuid,
                                type = SignalingMessage.SignalingType.ICE,
                                payload = candidateJson
                            )
                        )
                    }
                }
            }

            override fun onDataChannel(dataChannel: RTCDataChannel) {
                if (dataChannel.state == RTCDataChannelState.OPEN) {
                    onClientConnected(peerConnection, dataChannel)
                } else {
                    dataChannel.registerObserver(object : RTCDataChannelObserver {
                        override fun onBufferedAmountChange(previousAmount: Long) {}
                        override fun onMessage(buffer: RTCDataChannelBuffer) {}
                        override fun onStateChange() {
                            if (dataChannel.state == RTCDataChannelState.OPEN) {
                                this@WebRTCHostSession.dataChannel = dataChannel
                                onClientConnected(peerConnection, dataChannel)
                            }
                        }
                    })
                }
            }

            override fun onIceConnectionChange(state: RTCIceConnectionState) {
                if (state == RTCIceConnectionState.DISCONNECTED || state == RTCIceConnectionState.FAILED) {
                    WebRTCHostManager.removeSession(clientPlayerUuid)
                }
            }

            override fun onStandardizedIceConnectionChange(newState: RTCIceConnectionState) {}
            override fun onConnectionChange(newState: RTCPeerConnectionState) {}
        })

        val remoteOffer = RTCSessionDescription(RTCSdpType.OFFER, sdpOffer)
        peerConnection?.setRemoteDescription(remoteOffer, object : SetSessionDescriptionObserver {
            override fun onSuccess() {
                isRemoteSdpSet = true
                drainPendingCandidates()
                createAnswer()
            }

            override fun onFailure(error: String) {
                RpcManager.rpcLogger.error("设置 Remote Offer 失败: $error")
            }
        })
    }

    fun handleRemoteCandidate(candidateJson: String) {
        val candidate = deserializeCandidate(candidateJson)
        if (isRemoteSdpSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            synchronized(pendingCandidates) {
                pendingCandidates.add(candidate)
            }
        }
    }

    private fun drainPendingCandidates() {
        synchronized(pendingCandidates) {
            pendingCandidates.forEach { peerConnection?.addIceCandidate(it) }
            pendingCandidates.clear()
        }
    }

    fun close() {
        if (!isClosed.compareAndSet(expectedValue = false, newValue = true)) return
        runCatching {
            dataChannel?.close()
            dataChannel?.dispose()
            dataChannel = null
            peerConnection?.close()
            peerConnection = null
        }
    }

    private fun createAnswer() {
        peerConnection?.createAnswer(RTCAnswerOptions(), object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) {
                peerConnection?.setLocalDescription(description, object : SetSessionDescriptionObserver {
                    override fun onSuccess() {
                        scope.launch {
                            runCatching {
                                signalingService.sendSignal(
                                    clientPlayerUuid,
                                    SignalingMessage(
                                        senderPlayerUuid = minecraft.gameProfile.id.toKotlinUuid(),
                                        targetPlayerUuid = clientPlayerUuid,
                                        type = SignalingMessage.SignalingType.Answer,
                                        payload = description.sdp
                                    )
                                )
                            }
                        }
                    }

                    override fun onFailure(error: String) {
                        RpcManager.rpcLogger.error("SetLocalDescription Answer 失败: $error")
                    }
                })
            }

            override fun onFailure(error: String) {
                RpcManager.rpcLogger.error("CreateAnswer 失败: $error")
            }
        })
    }

    private fun onClientConnected(peerConnection: RTCPeerConnection?, dataChannel: RTCDataChannel) {
        val server = minecraft.singleplayerServer ?: return
        server.execute {
            try {
                val rtcChannel = WebRTCChannel(peerConnection!!, dataChannel)
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
}