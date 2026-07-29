/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */

package cn.rtast.peerlink.client.webrtc.guest

import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.client.util.RpcManager
import cn.rtast.peerlink.client.util.deserializeCandidate
import cn.rtast.peerlink.client.util.serializeCandidate
import cn.rtast.peerlink.client.util.showNotification
import cn.rtast.peerlink.data.play.SignalingMessage
import cn.rtast.peerlink.service.MinecraftSignalingService
import cn.rtast.peerlink.service.ServerSignalingService
import dev.kastle.webrtc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.minecraft.network.chat.Component
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.uuid.toKotlinUuid

class WebRTCClient(
    private val scope: CoroutineScope,
    private val signalingService: MinecraftSignalingService,
    private val serverSignalingService: ServerSignalingService,
    private val roomId: String,
    private val onStatusChanged: (RTCDataChannel) -> Unit,
) {
    private var peerFactory: PeerConnectionFactory? = null
    private var peerConnection: RTCPeerConnection? = null
    private var dataChannel: RTCDataChannel? = null

    @Volatile
    private var isRemoteDescriptionSet = false
    private val pendingCandidates = ConcurrentLinkedQueue<RTCIceCandidate>()

    suspend fun startConnect(): Boolean {
        try {
            peerFactory = PeerConnectionFactory()
            val iceConfig = serverSignalingService.acquireICEServerConfig()
            val roomState = signalingService.joinRoom(roomId) ?: return false

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
                    when (state) {
                        RTCIceConnectionState.DISCONNECTED -> {}

                        RTCIceConnectionState.FAILED -> {
                            close()
                            showNotification(
                                Component.translatable("peerlink.p2p.failed"),
                                Component.translatable("peerlink.p2p.icefailed")
                            )
                        }

                        else -> {}
                    }
                }

                override fun onStandardizedIceConnectionChange(newState: RTCIceConnectionState) {}

                override fun onConnectionChange(newState: RTCPeerConnectionState) {
                    RpcManager.rpcLogger.info("PeerConnection 状态变更: $newState")
                    if (newState == RTCPeerConnectionState.FAILED) {
                        showNotification(
                            Component.translatable("peerlink.p2p.failed"),
                            null
                        )
                        close()
                    } else if (newState == RTCPeerConnectionState.DISCONNECTED) {
                        showNotification(
                            Component.translatable("peerlink.p2p.failed"),
                            Component.translatable("peerlink.p2p.disconnected")
                        )
                    }
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
        } catch (e: Exception) {
            RpcManager.rpcLogger.error("WebRTC 启动失败: ${e.message}", e)
            close()
        }
        return true
    }

    fun handleRemoteAnswer(sdpAnswer: String) {
        val pc = peerConnection ?: run {
            RpcManager.rpcLogger.warn("[PeerLink Client] handleRemoteAnswer 失败: PeerConnection 为空")
            return
        }

        if (pc.signalingState != RTCSignalingState.HAVE_LOCAL_OFFER) {
            RpcManager.rpcLogger.warn("[PeerLink Client] 忽略无效状态下的 Answer 包，当前状态: ${pc.signalingState}")
            return
        }

        val sdp = RTCSessionDescription(RTCSdpType.ANSWER, sdpAnswer)
        pc.setRemoteDescription(sdp, object : SetSessionDescriptionObserver {
            override fun onSuccess() {
                RpcManager.rpcLogger.info("[PeerLink Client] 成功设置 Remote Answer SDP")
                isRemoteDescriptionSet = true
                synchronized(pendingCandidates) {
                    while (pendingCandidates.isNotEmpty()) {
                        val candidate = pendingCandidates.poll() ?: break
                        pc.addIceCandidate(candidate)
                    }
                }
            }

            override fun onFailure(error: String) {
                RpcManager.rpcLogger.error("[PeerLink Client] 设置 Remote Answer 失败: $error")
            }
        })
    }

    fun handleRemoteCandidate(candidateJson: String) {
        val candidate = deserializeCandidate(candidateJson)
        if (isRemoteDescriptionSet) {
            peerConnection?.addIceCandidate(candidate)
        } else pendingCandidates.add(candidate)
    }

    fun close() {
        try {
            dataChannel?.close()
            peerConnection?.close()
            peerFactory?.dispose()
            pendingCandidates.clear()
            isRemoteDescriptionSet = false
            RpcManager.rpcLogger.info("WebRTC 连接已清理释放")
        } catch (_: Exception) {
        }
    }

    private fun setupDataChannelObserver(dataChannel: RTCDataChannel) {
        dataChannel.registerObserver(object : RTCDataChannelObserver {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onMessage(buffer: RTCDataChannelBuffer) {}
            override fun onStateChange() {
                RpcManager.rpcLogger.info("DataChannel 状态变更: ${dataChannel.state}")
                when (dataChannel.state) {
                    RTCDataChannelState.OPEN -> {
                        RpcManager.rpcLogger.info("P2P 连接成功")
                        showNotification(
                            Component.translatable("peerlink.p2p.success"),
                            Component.translatable("peerlink.p2p.joining")
                        )
                        onStatusChanged(dataChannel)
                    }

                    RTCDataChannelState.CLOSED -> {
                        RpcManager.rpcLogger.error("DataChannel 已关闭")
                    }

                    else -> {}
                }
            }
        })
    }
}