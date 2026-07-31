/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.client.webrtc

import cn.rtast.peerlink.data.play.SignalingMessage
import dev.kastle.webrtc.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

class RtcHandshake(
    private val factory: PeerConnectionFactory,
    private val config: RTCConfiguration,
    private val isInitiator: Boolean,
    private val localPlayerUuid: Uuid,
    private val targetPlayerUuid: Uuid,
    private val sendSignal: suspend (SignalingMessage) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(RtcHandshake::class.java)
    }

    data class HandshakeResult(
        val peerConnection: RTCPeerConnection,
        val dataChannel: RTCDataChannel,
    )

    private val handshakeDeferred = CompletableDeferred<HandshakeResult>()
    private var peerConnection: RTCPeerConnection? = null
    private var dataChannel: RTCDataChannel? = null

    suspend fun start(): HandshakeResult {
        val pc = factory.createPeerConnection(config, object : PeerConnectionObserver {
            override fun onIceCandidate(candidate: RTCIceCandidate) {
                val payloadStr = "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
                val msg = SignalingMessage(
                    senderPlayerUuid = localPlayerUuid,
                    targetPlayerUuid = targetPlayerUuid,
                    type = SignalingMessage.SignalingType.Ice,
                    payload = payloadStr
                )
                scope.launch { sendSignal(msg) }
            }

            override fun onDataChannel(dc: RTCDataChannel) {
                if (!isInitiator) {
                    dataChannel = dc
                    bindDataChannelEvents(dc)
                }
            }
        }) ?: throw IllegalStateException("Failed to create PeerConnection")

        this.peerConnection = pc

        if (isInitiator) {
            val init = RTCDataChannelInit().apply { ordered = true }
            val dc = pc.createDataChannel("minecraft", init)
            this.dataChannel = dc
            bindDataChannelEvents(dc)

            pc.createOffer(RTCOfferOptions(), object : CreateSessionDescriptionObserver {
                override fun onSuccess(sdp: RTCSessionDescription) {
                    pc.setLocalDescription(sdp, SimpleSetSdpObserver())
                    val msg = SignalingMessage(
                        senderPlayerUuid = localPlayerUuid,
                        targetPlayerUuid = targetPlayerUuid,
                        type = SignalingMessage.SignalingType.Offer,
                        payload = sdp.sdp
                    )
                    scope.launch { sendSignal(msg) }
                }

                override fun onFailure(error: String) {
                    LOGGER.error("Failed to create offer: $error")
                }
            })
        }

        return handshakeDeferred.await()
    }

    suspend fun handleIncomingSignal(message: SignalingMessage) {
        val pc = peerConnection ?: return

        when (message.type) {
            SignalingMessage.SignalingType.Offer -> {
                val remoteSdp = RTCSessionDescription(RTCSdpType.OFFER, message.payload)
                pc.setRemoteDescription(remoteSdp, object : SimpleSetSdpObserver() {
                    override fun onSuccess() {
                        pc.createAnswer(RTCAnswerOptions(), object : CreateSessionDescriptionObserver {
                            override fun onSuccess(answerSdp: RTCSessionDescription) {
                                pc.setLocalDescription(answerSdp, SimpleSetSdpObserver())
                                val answerMsg = SignalingMessage(
                                    senderPlayerUuid = localPlayerUuid,
                                    targetPlayerUuid = targetPlayerUuid,
                                    type = SignalingMessage.SignalingType.Answer,
                                    payload = answerSdp.sdp
                                )
                                scope.launch { sendSignal(answerMsg) }
                            }

                            override fun onFailure(error: String?) {
                                LOGGER.error("Failed to create answer: $error")
                            }
                        })
                    }
                })
            }

            SignalingMessage.SignalingType.Answer -> {
                val remoteSdp = RTCSessionDescription(RTCSdpType.ANSWER, message.payload)
                pc.setRemoteDescription(remoteSdp, SimpleSetSdpObserver())
            }

            SignalingMessage.SignalingType.Ice -> {
                val parts = message.payload.split("|", limit = 3)
                if (parts.size == 3) {
                    val sdpMid = parts[0]
                    val sdpMLineIndex = parts[1].toIntOrNull() ?: 0
                    val sdp = parts[2]
                    val candidate = RTCIceCandidate(sdpMid, sdpMLineIndex, sdp)
                    pc.addIceCandidate(candidate)
                }
            }
        }
    }

    private fun bindDataChannelEvents(dc: RTCDataChannel) {
        dc.registerObserver(object : RTCDataChannelObserver {
            override fun onStateChange() {
                if (dc.state == RTCDataChannelState.OPEN) {
                    handshakeDeferred.complete(HandshakeResult(peerConnection!!, dc))
                }
            }

            override fun onMessage(buffer: RTCDataChannelBuffer) {}
            override fun onBufferedAmountChange(previousAmount: Long) {}
        })
    }

    fun dispose() {
        runCatching { dataChannel?.close() }
        runCatching { peerConnection?.close() }
    }

    private open class SimpleSetSdpObserver : SetSessionDescriptionObserver {
        override fun onSuccess() {}
        override fun onFailure(error: String?) {}
    }
}