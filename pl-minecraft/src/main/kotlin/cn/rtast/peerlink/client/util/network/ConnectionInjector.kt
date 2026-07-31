/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.util.network

import cn.rtast.peerlink.client.mixin.ClientConnectionAccessor
import io.netty.channel.Channel
import io.netty.handler.timeout.ReadTimeoutHandler
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.server.network.EventLoopGroupHolder
import net.minecraft.util.debugchart.LocalSampleLogger
import java.net.InetAddress
import java.net.InetSocketAddress


object ConnectionInjector {
    @Suppress("DEPRECATION")
    private val fallbackSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 0)

    @JvmStatic
    fun fromChannel(channel: Channel, packetFlow: PacketFlow, bandwidthLogger: LocalSampleLogger?): Connection {
        val connection = Connection(packetFlow)
        if (bandwidthLogger != null) connection.setBandwidthLogger(bandwidthLogger)
        @Suppress("CAST_NEVER_SUCCEEDS")
        val bandwidthMonitor = (connection as ClientConnectionAccessor).`peerlink$getBandwidthDebugMonitor`()
        val accessor = connection as ClientConnectionAccessor
        accessor.`peerlink$setChannel`(channel)
        accessor.`peerlink$setAddress`(channel.remoteAddress() ?: fallbackSocketAddress)
        if (!channel.isRegistered) EventLoopGroupHolder.local().eventLoopGroup().register(channel).syncUninterruptibly()
        channel.config().isAutoRead = true
        channel.pipeline().addLast("timeout", ReadTimeoutHandler(60))
        Connection.configureSerialization(channel.pipeline(), packetFlow, false, bandwidthMonitor)
        connection.configurePacketHandler(channel.pipeline())
        channel.pipeline().fireChannelActive()
        return connection
    }
}