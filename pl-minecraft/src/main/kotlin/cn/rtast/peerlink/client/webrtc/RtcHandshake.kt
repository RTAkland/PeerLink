/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.client.webrtc

import com.mojang.logging.LogUtils
import dev.kastle.webrtc.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RtcHandshake(
    factory: PeerConnectionFactory,
    rtcConfig: RTCConfiguration,
    val isInitiator: Boolean,
    private val onLocalCandidate: (RTCIceCandidate) -> Unit,
) {
    data class HandshakeResult(val peerConnection: RTCPeerConnection, val dataChannel: RTCDataChannel)

    private val logger = LogUtils.getLogger()
    private val resultDeferred = CompletableDeferred<HandshakeResult>()
    private val handedOff = AtomicBoolean()

    @Volatile
    private var dataChannel: RTCDataChannel? = null
    private val peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnectionObserver {
        override fun onIceCandidate(candidate: RTCIceCandidate) = onLocalCandidate(candidate)
        override fun onDataChannel(dataChannel: RTCDataChannel) = writeDataChannel(dataChannel)
        override fun onConnectionChange(state: RTCPeerConnectionState) {
            if (state === RTCPeerConnectionState.FAILED || state === RTCPeerConnectionState.CLOSED || state === RTCPeerConnectionState.DISCONNECTED) {
                resultDeferred.completeExceptionally(IllegalStateException("Peer connection $state"))
            }
        }
    })

    init {
        if (isInitiator) {
            val dataChannelInit = RTCDataChannelInit().apply { ordered = true }
            writeDataChannel(peerConnection.createDataChannel("pl-minecraft-channel", dataChannelInit))
        }
    }

    suspend fun awaitResult(): HandshakeResult = resultDeferred.await()

    fun abort(reason: String) {
        resultDeferred.completeExceptionally(IllegalStateException(reason))
        RtcChannel.dispose(peerConnection, dataChannel)
    }

    suspend fun createOffer(): String {
        val sdp = this.createOfferSdp()
        setLocalDescription(sdp)
        return sdp.sdp
    }

    suspend fun createOfferSdp(): RTCSessionDescription = suspendCancellableCoroutine {
        peerConnection.createOffer(RTCOfferOptions(), object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) = it.resume(description)
            override fun onFailure(error: String) = it.resumeWithException(IllegalStateException(error))
        })
    }

    suspend fun createAnswerSdp(): RTCSessionDescription = suspendCancellableCoroutine {
        peerConnection.createAnswer(RTCAnswerOptions(), object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) = it.resume(description)
            override fun onFailure(error: String) = it.resumeWithException(IllegalStateException(error))
        })
    }

    suspend fun acceptOffer(sdp: String): String {
        val offer = RTCSessionDescription(RTCSdpType.OFFER, sdp)
        setRemoteDescription(offer)
        val answer = createAnswerSdp()
        setLocalDescription(answer)
        return answer.sdp
    }

    suspend fun setRemoteDescription(description: RTCSessionDescription): Unit = suspendCancellableCoroutine {
        peerConnection.setRemoteDescription(description, object : SetSessionDescriptionObserver {
            override fun onSuccess() = it.resume(Unit)
            override fun onFailure(error: String) = it.resumeWithException(IllegalStateException(error))
        })
    }

    suspend fun setLocalDescription(description: RTCSessionDescription): Unit = suspendCancellableCoroutine {
        peerConnection.setLocalDescription(description, object : SetSessionDescriptionObserver {
            override fun onSuccess() = it.resume(Unit)
            override fun onFailure(error: String) = it.resumeWithException(IllegalStateException(error))
        })
    }

    suspend fun addRemoteIceCandidate(candidate: RTCIceCandidate): Unit = suspendCancellableCoroutine {
        try {
            peerConnection.addIceCandidate(candidate)
            it.resume(Unit)
        } catch (e: Exception) {
            it.resumeWithException(e)
        }
    }

    suspend fun applyAnswer(sdp: String) =
        setRemoteDescription(RTCSessionDescription(RTCSdpType.ANSWER, sdp))

    private fun tryComplete() {
        val channel = this.dataChannel
        if (channel != null && channel.state == RTCDataChannelState.OPEN && handedOff.compareAndSet(false, true)) {
            channel.unregisterObserver()
            resultDeferred.complete(HandshakeResult(peerConnection, channel))
        }
    }

    private fun writeDataChannel(dataChannel: RTCDataChannel) {
        this.dataChannel = dataChannel
        dataChannel.registerObserver(object : RTCDataChannelObserver {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() = tryComplete()
            override fun onMessage(buffer: RTCDataChannelBuffer) {}
        })
    }
}