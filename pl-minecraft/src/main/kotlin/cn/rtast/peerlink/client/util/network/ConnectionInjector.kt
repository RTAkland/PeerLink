/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.util.network

import cn.rtast.peerlink.client.mixin.ClientConnectionAccessor
import io.netty.channel.Channel
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.handler.timeout.ReadTimeoutHandler
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.util.debugchart.LocalSampleLogger
import java.net.InetAddress
import java.net.InetSocketAddress


object ConnectionInjector {
    private val fallbackSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 0)
    private val eventExecutors = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())

    @JvmStatic
    fun fromChannel(channel: Channel, packetFlow: PacketFlow, bandwidthLogger: LocalSampleLogger?): Connection {
        val connection = Connection(packetFlow)
        if (bandwidthLogger != null) connection.setBandwidthLogger(bandwidthLogger)
        @Suppress("CAST_NEVER_SUCCEEDS") // In fact, Cast will always succeed
        val bandwidthMonitor = (connection as ClientConnectionAccessor).`peerlink$getBandwidthDebugMonitor`()
        channel.pipeline().addLast("timeout", ReadTimeoutHandler(30))
        Connection.configureSerialization(channel.pipeline(), packetFlow, false, bandwidthMonitor)
        connection.configurePacketHandler(channel.pipeline())
        val accessor = connection as ClientConnectionAccessor
        accessor.`peerlink$setChannel`(channel)
        accessor.`peerlink$setAddress`(channel.remoteAddress() ?: fallbackSocketAddress)
        eventExecutors.register(channel).syncUninterruptibly()
        return connection
    }
}