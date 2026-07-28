/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.webrtc.host

import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.util.RpcManager
import cn.rtast.peerlink.client.util.deserializeCandidate
import cn.rtast.peerlink.client.util.serializeCandidate
import cn.rtast.peerlink.data.ICEServerConfig
import cn.rtast.peerlink.data.play.SignalingMessage
import cn.rtast.peerlink.service.MinecraftSignalingService
import dev.onvoid.webrtc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class WebRTCHostSession(
    val clientPlayerUuid: Uuid,
    private val scope: CoroutineScope,
    private val signalingService: MinecraftSignalingService,
    private val roomId: String,
    private val iceConfig: ICEServerConfig,
    private val onClientConnected: (RTCDataChannel) -> Unit,
) {
    private val peerFactory: PeerConnectionFactory? = PeerConnectionFactory()
    private var peerConnection: RTCPeerConnection? = null

    private val pendingCandidates = mutableListOf<RTCIceCandidate>()
    private var isRemoteSdpSet = false

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

        peerConnection = peerFactory!!.createPeerConnection(rtcConfig, object : PeerConnectionObserver {
            override fun onIceCandidate(candidate: RTCIceCandidate) {
                scope.launch {
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

            override fun onDataChannel(dataChannel: RTCDataChannel) {
                setupDataChannelObserver(dataChannel)
            }

            override fun onIceConnectionChange(state: RTCIceConnectionState) {
                if (state == RTCIceConnectionState.DISCONNECTED || state == RTCIceConnectionState.FAILED) close()
            }

            override fun onStandardizedIceConnectionChange(newState: RTCIceConnectionState) {}
            override fun onConnectionChange(newState: RTCPeerConnectionState) {}
        })

        val remoteOffer = RTCSessionDescription(RTCSdpType.OFFER, sdpOffer)
        peerConnection!!.setRemoteDescription(remoteOffer, object : SetSessionDescriptionObserver {
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
        if (isRemoteSdpSet) peerConnection?.addIceCandidate(candidate) else synchronized(pendingCandidates) {
            pendingCandidates.add(candidate)
        }
    }

    private fun drainPendingCandidates() {
        synchronized(pendingCandidates) {
            pendingCandidates.forEach { peerConnection?.addIceCandidate(it) }
            pendingCandidates.clear()
        }
    }

    private fun setupDataChannelObserver(dataChannel: RTCDataChannel) {
        dataChannel.registerObserver(object : RTCDataChannelObserver {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onMessage(buffer: RTCDataChannelBuffer) {}
            override fun onStateChange() {
                if (dataChannel.state == RTCDataChannelState.OPEN) onClientConnected(dataChannel)
            }
        })
    }

    fun close() {
        try {
            peerConnection?.close()
            peerFactory?.dispose()
        } catch (_: Exception) {
        } finally {
            WebRTCHostManager.removeSession(clientPlayerUuid)
        }
    }

    private fun createAnswer() {
        peerConnection?.createAnswer(RTCAnswerOptions(), object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) {
                peerConnection?.setLocalDescription(description, object : SetSessionDescriptionObserver {
                    override fun onSuccess() {
                        scope.launch {
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
}