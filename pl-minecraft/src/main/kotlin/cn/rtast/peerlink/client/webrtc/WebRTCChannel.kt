/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.webrtc

import dev.kastle.webrtc.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer

class WebRTCChannel(
    private val peerConnection: RTCPeerConnection,
    private val dataChannel: RTCDataChannel,
) : AbstractChannel(null) {
    companion object {
        private val METADATA = ChannelMetadata(false)
        private const val MAX_CHUNK_SIZE = 262144
        private const val HIGH_WATER_MARK = 1048576L
        private const val BACKPRESSURE_FLAG = 1
        private val LOCAL_ADDRESS = InetSocketAddress(InetAddress.getLoopbackAddress(), 0)
        private val REMOTE_ADDRESS = InetSocketAddress(InetAddress.getLoopbackAddress(), 0)
    }

    private val channelConfig: ChannelConfig = DefaultChannelConfig(this)

    @Volatile
    private var closed = false

    @Volatile
    private var activated = false
    private var writeStalled = false

    override fun metadata(): ChannelMetadata = METADATA
    override fun config(): ChannelConfig = channelConfig
    override fun isOpen(): Boolean = !closed
    override fun isActive(): Boolean = activated && !closed
    override fun localAddress0(): SocketAddress = LOCAL_ADDRESS
    override fun remoteAddress0(): SocketAddress = REMOTE_ADDRESS
    override fun isCompatible(loop: EventLoop): Boolean = loop is SingleThreadEventLoop
    override fun newUnsafe(): AbstractUnsafe {
        return object : AbstractUnsafe() {
            override fun connect(remoteAddress: SocketAddress?, localAddress: SocketAddress?, promise: ChannelPromise) {
                promise.setFailure(UnsupportedOperationException("WebRTCChannel is already connected"))
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun doRegister() {
        eventLoop().execute {
            handleStateChange(dataChannel.state)
            dataChannel.registerObserver(Observer())
        }
    }

    override fun doBind(localAddress: SocketAddress?) {
        throw UnsupportedOperationException("WebRTCChannel cannot be bound")
    }

    override fun doDisconnect() {
        closeFromTransport()
    }

    override fun doClose() {
        if (closed) return
        closed = true
        runCatching { dataChannel.unregisterObserver() }
        runCatching { dataChannel.close() }
        runCatching { dataChannel.dispose() }
        runCatching { peerConnection.close() }
    }

    override fun doBeginRead() {}

    override fun doWrite(inbound: ChannelOutboundBuffer) {
        var message: Any?
        while (inbound.current().also { message = it } != null) {
            if (message is ByteBuf) writeByteBuf(message)
            inbound.remove()
            if (dataChannel.bufferedAmount >= HIGH_WATER_MARK) {
                setWriteStalled(true)
                return
            }
        }
    }

    private fun writeByteBuf(byteBuf: ByteBuf) {
        var readableBytes = byteBuf.readableBytes()
        var readerIndex = byteBuf.readerIndex()
        while (readableBytes > 0) {
            val size = minOf(readableBytes, MAX_CHUNK_SIZE)
            val bytes = ByteArray(size)
            byteBuf.getBytes(readerIndex, bytes)
            dataChannel.send(RTCDataChannelBuffer(ByteBuffer.wrap(bytes), true))
            readerIndex += size
            readableBytes -= size
        }
    }

    private fun handleMessage(byteBuf: ByteBuf) {
        if (closed || !activated || !config().isAutoRead) {
            byteBuf.release()
            return
        }
        pipeline().fireChannelRead(byteBuf)
        pipeline().fireChannelReadComplete()
    }

    private fun handleStateChange(state: RTCDataChannelState) {
        if (closed) return

        if (state == RTCDataChannelState.OPEN) {
            if (!activated) {
                activated = true
                pipeline().fireChannelActive()
            }
        } else if (state == RTCDataChannelState.CLOSING || state == RTCDataChannelState.CLOSED) {
            closeFromTransport()
        }
    }

    private fun setWriteStalled(stalled: Boolean) {
        if (closed || writeStalled == stalled) return
        writeStalled = stalled
        val buffer = unsafe().outboundBuffer()
        buffer?.setUserDefinedWritability(BACKPRESSURE_FLAG, !stalled)
    }

    private fun closeFromTransport() {
        if (!closed) unsafe().close(voidPromise())
    }

    private inner class Observer : RTCDataChannelObserver {
        override fun onBufferedAmountChange(previousAmount: Long) {
            if (dataChannel.bufferedAmount < HIGH_WATER_MARK) {
                eventLoop().execute {
                    setWriteStalled(false)
                    flush()
                }
            }
        }

        override fun onStateChange() {
            eventLoop().execute {
                if (closed) return@execute
                try {
                    handleStateChange(dataChannel.state)
                } catch (e: RuntimeException) {
                    closeFromTransport()
                }
            }
        }

        override fun onMessage(buffer: RTCDataChannelBuffer) {
            val data = buffer.data
            val bytes = ByteArray(data.remaining())
            data.get(bytes)
            val byteBuf = Unpooled.wrappedBuffer(bytes)
            eventLoop().execute { handleMessage(byteBuf) }
        }
    }
}