/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.network

import dev.onvoid.webrtc.RTCDataChannel
import dev.onvoid.webrtc.RTCDataChannelBuffer
import dev.onvoid.webrtc.RTCDataChannelObserver
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.util.ReferenceCountUtil
import java.nio.ByteBuffer


class WebRTCNettyHandler(
    private val dataChannel: RTCDataChannel,
) : ChannelDuplexHandler() {

    private var boundContext: ChannelHandlerContext? = null

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        this.boundContext = ctx

        dataChannel.registerObserver(object : RTCDataChannelObserver {
            override fun onMessage(buffer: RTCDataChannelBuffer) {
                val currentCtx = boundContext ?: return
                val length = buffer.data.remaining()
                val bytes = ByteArray(length)
                buffer.data.get(bytes)
                val nettyBuf = Unpooled.copiedBuffer(bytes)
                currentCtx.executor().execute {
                    currentCtx.fireChannelRead(nettyBuf)
                    currentCtx.fireChannelReadComplete()
                }
            }

            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                println("[WebRTCNettyHandler] DataChannel 状态变更: ${dataChannel.state}")
            }
        })
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is ByteBuf) {
            try {
                if (msg.readableBytes() > 0) {
                    val bytes = ByteArray(msg.readableBytes())
                    msg.getBytes(msg.readerIndex(), bytes)
                    val directBuffer = ByteBuffer.allocateDirect(bytes.size)
                    directBuffer.put(bytes)
                    directBuffer.flip()
                    val rtcBuffer = RTCDataChannelBuffer(directBuffer, false)
                    dataChannel.send(rtcBuffer)
                }
                promise.setSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                promise.setFailure(e)
            } finally {
                ReferenceCountUtil.release(msg)
            }
        } else {
            super.write(ctx, msg, promise)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        println("[WebRTCNettyHandler] 管道发生异常:")
        cause.printStackTrace()
        ctx.close()
    }
}