/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.client.webrtc

import dev.kastle.webrtc.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import kotlin.concurrent.Volatile


class RtcChannel(
    private val handshakeResult: RtcHandshake.HandshakeResult,
) : AbstractChannel(null) {

    companion object {
        private val logger = LoggerFactory.getLogger(RtcChannel::class.java)
        private val METADATA = ChannelMetadata(false)

        private const val MAX_CHUNK_SIZE = 262144
        private const val HIGH_WATER_MARK = 1048576L
        private const val LOW_WATER_MARK = 262144L
        private const val BACKPRESSURE_USER_FLAG = 1

        /**
         * dispose webrtc
         */
        fun dispose(handshakeResult: RtcHandshake.HandshakeResult) {
            dispose(handshakeResult.peerConnection, handshakeResult.dataChannel)
        }

        fun dispose(peerConnection: RTCPeerConnection, dataChannel: RTCDataChannel?) {
            dataChannel?.let { dc ->
                runCatching { dc.unregisterObserver() }.onFailure { logger.warn("unregisterObserver threw", it) }
                runCatching { dc.close() }.onFailure { logger.warn("dataChannel.close threw", it) }
                runCatching { dc.dispose() }.onFailure { logger.warn("dataChannel.dispose threw", it) }
            }
            runCatching { peerConnection.close() }.onFailure { logger.warn("peerConnection.close threw", it) }
        }
    }

    private val channelConfig = DefaultChannelConfig(this)

    @Volatile
    private var closed = false

    @Volatile
    private var activated = false
    private var writeStalled = false

    override fun metadata(): ChannelMetadata = METADATA
    override fun config(): ChannelConfig = channelConfig
    override fun newUnsafe(): AbstractUnsafe = object : AbstractUnsafe() {
        override fun connect(remoteAddress: SocketAddress, localAddress: SocketAddress, promise: ChannelPromise) {
            promise.setFailure(UnsupportedOperationException("RtcChannel is already connected by WebRTC"))
        }
    }

    override fun isCompatible(loop: EventLoop): Boolean = loop is SingleThreadEventLoop
    override fun isOpen(): Boolean = !closed
    override fun isActive(): Boolean = activated && !closed
    override fun localAddress0(): SocketAddress = InetSocketAddress("rtc-local", 0)
    override fun remoteAddress0(): SocketAddress = InetSocketAddress("rtc-remote", 0)

    override fun doRegister() {
        val dc = handshakeResult.dataChannel
        val initialState = dc.state
        eventLoop().execute {
            handleStateChange(initialState)
            dc.registerObserver(object : RTCDataChannelObserver {
                override fun onMessage(buffer: RTCDataChannelBuffer) {
                    val data = buffer.data
                    val bytes = ByteArray(data.remaining())
                    data.get(bytes)
                    val byteBuf = Unpooled.wrappedBuffer(bytes)
                    eventLoop().execute { handleMessage(byteBuf) }
                }

                override fun onStateChange() {
                    eventLoop().execute {
                        if (closed) return@execute
                        try {
                            handleStateChange(handshakeResult.dataChannel.state)
                        } catch (_: RuntimeException) {
                            closeFromTransport()
                        }
                    }
                }

                override fun onBufferedAmountChange(previousAmount: Long) {
                    if (handshakeResult.dataChannel.bufferedAmount < HIGH_WATER_MARK) {
                        eventLoop().execute { setWriteStalled(false); flush() }
                    }
                }
            })
        }
    }

    override fun doBind(localAddress: SocketAddress) {
        throw UnsupportedOperationException("RtcChannel cannot be bound")
    }

    override fun doDisconnect() {
        closeFromTransport()
    }

    override fun doClose() {
        if (!closed) {
            closed = true
            dispose(handshakeResult)
        }
    }

    override fun doBeginRead() {
    }

    override fun doWrite(inBuffer: ChannelOutboundBuffer) {
        var msg: Any?
        while (inBuffer.current().also { msg = it } != null) {
            if (msg is ByteBuf) writeByteBuf(msg)
            inBuffer.remove()
            if (handshakeResult.dataChannel.bufferedAmount >= HIGH_WATER_MARK) {
                setWriteStalled(true)
                return
            }
        }
    }

    private fun writeByteBuf(buf: ByteBuf) {
        var remaining = buf.readableBytes()
        var idx = buf.readerIndex()
        while (remaining > 0) {
            val chunk = minOf(remaining, MAX_CHUNK_SIZE)
            val bytes = ByteArray(chunk)
            buf.getBytes(idx, bytes)
            try {
                val rtcBuffer = RTCDataChannelBuffer(ByteBuffer.wrap(bytes), true)
                handshakeResult.dataChannel.send(rtcBuffer)
            } catch (e: Exception) {
                logger.error("Failed to send DataChannel message", e)
                throw e
            }
            idx += chunk
            remaining -= chunk
        }
    }

    private fun setWriteStalled(stalled: Boolean) {
        if (closed || writeStalled == stalled) return
        writeStalled = stalled
        val buffer = unsafe().outboundBuffer()
        if (buffer != null) buffer.setUserDefinedWritability(BACKPRESSURE_USER_FLAG, !stalled)
    }

    private fun handleMessage(buf: ByteBuf) {
        if (closed || !activated) {
            buf.release()
            return
        }
        pipeline().fireChannelRead(buf)
        pipeline().fireChannelReadComplete()
    }

    private fun handleStateChange(state: RTCDataChannelState) {
        if (closed) return
        if (state === RTCDataChannelState.OPEN) {
            if (!activated) {
                activated = true
                pipeline().fireChannelActive()
            }
        } else if (state === RTCDataChannelState.CLOSING || state === RTCDataChannelState.CLOSED) closeFromTransport()
    }

    private fun closeFromTransport() {
        if (!closed) {
            logger.debug("Closing RtcChannel from transport")
            unsafe().close(voidPromise())
        }
    }
}
