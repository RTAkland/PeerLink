/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.webrtc.guest

import cn.rtast.peerlink.client.util.RpcManager
import dev.kastle.webrtc.RTCDataChannel
import dev.kastle.webrtc.RTCDataChannelBuffer
import dev.kastle.webrtc.RTCDataChannelObserver
import dev.kastle.webrtc.RTCDataChannelState
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


object WebRTCClientManager {

    @Volatile
    private var activeWebRtcConnection: Connection? = null

    @JvmStatic
    @Volatile
    var activeDataChannel: RTCDataChannel? = null

    @Volatile
    private var dataChannelReadyFuture = CompletableFuture<Void>()

    fun setupWebRtcSession(dataChannel: RTCDataChannel) {
        this.activeDataChannel = dataChannel
        this.dataChannelReadyFuture = CompletableFuture()

        dataChannel.registerObserver(object : RTCDataChannelObserver {
            override fun onStateChange() {
                val state = dataChannel.state
                if (state == RTCDataChannelState.OPEN) {
                    dataChannelReadyFuture.complete(null)
                } else if (state == RTCDataChannelState.CLOSED) {
                    dataChannelReadyFuture.completeExceptionally(IllegalStateException("DataChannel 已关闭"))
                }
            }

            override fun onMessage(buffer: RTCDataChannelBuffer) {}
            override fun onBufferedAmountChange(previousAmount: Long) {}
        })

        if (dataChannel.state == RTCDataChannelState.OPEN) {
            dataChannelReadyFuture.complete(null)
        }
    }

    fun awaitDataChannelReady(timeoutSeconds: Long = 10) {
        try {
            dataChannelReadyFuture.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw IllegalStateException("等待 WebRTC DataChannel OPEN 超时或失败: ${e.message}", e)
        }
    }

    @JvmStatic
    fun reset() {
        try {
            activeWebRtcConnection?.disconnect(Component.literal("WebRTC Session Reset"))
        } catch (_: Exception) {
        } finally {
            activeWebRtcConnection = null
            activeDataChannel = null
            dataChannelReadyFuture = CompletableFuture()
            RpcManager.rpcLogger.info("[WebRTCClientManager] 已重置 WebRTC 客户端状态")
        }
    }
}