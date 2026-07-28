/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.client.webrtc

import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.util.RpcManager
import cn.rtast.peerlink.client.util.deserializeCandidate
import cn.rtast.peerlink.client.util.serializeCandidate
import cn.rtast.peerlink.data.play.SignalingMessage
import cn.rtast.peerlink.service.MinecraftSignalingService
import cn.rtast.peerlink.service.ServerSignalingService
import dev.onvoid.webrtc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.uuid.toKotlinUuid

class WebRTCClient(
    private val scope: CoroutineScope,
    private val signalingService: MinecraftSignalingService,
    private val serverSignalingService: ServerSignalingService,
    private val roomId: String,
    private val onConnectedToGame: (RTCDataChannel) -> Unit,
) {
    private var peerFactory: PeerConnectionFactory? = null
    private var peerConnection: RTCPeerConnection? = null
    private var dataChannel: RTCDataChannel? = null

    @Volatile
    private var isRemoteDescriptionSet = false
    private val pendingCandidates = ConcurrentLinkedQueue<RTCIceCandidate>()

    suspend fun startConnect() {
        peerFactory = PeerConnectionFactory()

        val iceConfig = serverSignalingService.acquireICEServerConfig()
        val roomState = signalingService.joinRoom(roomId)

        val rtcConfig = RTCConfiguration().apply {
            val stunServer = RTCIceServer().apply {
                urls.addAll(iceConfig.stunServers.map { if (it.startsWith("stun:")) it else "stun:$it" })
            }
            val turnServer = RTCIceServer().apply {
                urls.addAll(iceConfig.turnServers.map { if (it.startsWith("turn:")) it else "turn:$it" })
                iceConfig.username.let { username = it }
                iceConfig.password.let { password = it }
            }
            iceServers.add(stunServer)
            iceServers.add(turnServer)
        }
        peerConnection = peerFactory!!.createPeerConnection(rtcConfig, object : PeerConnectionObserver {
            override fun onIceCandidate(candidate: RTCIceCandidate) {
                scope.launch {
                    val candidateJson = serializeCandidate(candidate)
                    signalingService.sendSignal(
                        roomState.hostPlayerUuid,
                        SignalingMessage(
                            senderPlayerUuid = minecraft.gameProfile.id.toKotlinUuid(),
                            targetPlayerUuid = roomState.hostPlayerUuid,
                            type = SignalingMessage.SignalingType.ICE,
                            payload = candidateJson
                        )
                    )
                }
            }

            override fun onIceConnectionChange(state: RTCIceConnectionState) {
                RpcManager.rpcLogger.info("ICE 状态变更: $state")
                if (state == RTCIceConnectionState.DISCONNECTED || state == RTCIceConnectionState.FAILED) {
                    close()
                }
            }

            override fun onStandardizedIceConnectionChange(newState: RTCIceConnectionState) {}
            override fun onConnectionChange(newState: RTCPeerConnectionState) {
                RpcManager.rpcLogger.info("PeerConnection 状态变更: $newState")
            }
        })

        val dataChannelInit = RTCDataChannelInit().apply { ordered = true }
        dataChannel = peerConnection!!.createDataChannel("minecraft-peerlink-channel", dataChannelInit)
        setupDataChannelObserver(dataChannel!!)
        peerConnection!!.createOffer(RTCOfferOptions(), object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) {
                peerConnection?.setLocalDescription(description, object : SetSessionDescriptionObserver {
                    override fun onSuccess() {
                        scope.launch {
                            signalingService.sendSignal(
                                roomState.hostPlayerUuid,
                                SignalingMessage(
                                    senderPlayerUuid = minecraft.gameProfile.id.toKotlinUuid(),
                                    targetPlayerUuid = roomState.hostPlayerUuid,
                                    type = SignalingMessage.SignalingType.Offer,
                                    payload = description.sdp
                                )
                            )
                        }
                    }

                    override fun onFailure(error: String) {
                        RpcManager.rpcLogger.error("SetLocalDescription 失败: $error")
                    }
                })
            }

            override fun onFailure(error: String) {
                RpcManager.rpcLogger.error("CreateOffer 失败: $error")
            }
        })
    }

    fun handleRemoteAnswer(sdpAnswer: String) {
        val sdp = RTCSessionDescription(RTCSdpType.ANSWER, sdpAnswer)
        peerConnection?.setRemoteDescription(sdp, object : SetSessionDescriptionObserver {
            override fun onSuccess() {
                RpcManager.rpcLogger.info("成功设置 Remote Answer SDP")
                isRemoteDescriptionSet = true
                while (pendingCandidates.isNotEmpty()) {
                    pendingCandidates.poll()?.let { candidate ->
                        peerConnection?.addIceCandidate(candidate)
                    }
                }
            }

            override fun onFailure(error: String) {
                RpcManager.rpcLogger.error("设置 Remote Answer 失败: $error")
            }
        })
    }

    fun handleRemoteCandidate(candidateJson: String) {
        val candidate = deserializeCandidate(candidateJson)
        if (isRemoteDescriptionSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingCandidates.add(candidate)
        }
    }

    fun close() {
        dataChannel?.close()
        peerConnection?.close()
        peerFactory?.dispose()
        pendingCandidates.clear()
        isRemoteDescriptionSet = false
        RpcManager.rpcLogger.info("WebRTC 连接已清理释放")
    }

    private fun setupDataChannelObserver(dataChannel: RTCDataChannel) {
        dataChannel.registerObserver(object : RTCDataChannelObserver {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onMessage(buffer: RTCDataChannelBuffer) {}
            override fun onStateChange() {
                RpcManager.rpcLogger.info("DataChannel 状态变更: ${dataChannel.state}")
                if (dataChannel.state == RTCDataChannelState.OPEN) {
                    RpcManager.rpcLogger.info("P2P 连接成功")
                    onConnectedToGame(dataChannel)
                } else if (dataChannel.state == RTCDataChannelState.CLOSED) {
                    RpcManager.rpcLogger.error("DataChannel 已关闭")
                }
            }
        })
    }
}