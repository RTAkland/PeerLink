/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.network

import io.netty.channel.Channel
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.handler.timeout.ReadTimeoutHandler
import net.minecraft.network.BandwidthDebugMonitor
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.util.debugchart.LocalSampleLogger
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress


object BackportedConnectionFactory {
    private val FROM_CHANNEL: Method? = findFromChannel()
    private val CHANNEL_FIELD: Field = findField("channel")
    private val ADDRESS_FIELD: Field = findField("address")
    private val BANDWIDTH_FIELD: Field = findField("bandwidthDebugMonitor")
    private val FALLBACK_REMOTE_ADDRESS: SocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 0)

    private val EVENT_LOOP_GROUP = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())

    @JvmStatic
    fun fromChannel(channel: Channel, packetFlow: PacketFlow, bandwidthLogger: LocalSampleLogger?): Connection {
        if (FROM_CHANNEL != null) {
            try {
                return FROM_CHANNEL.invoke(null, channel, packetFlow, bandwidthLogger) as Connection
            } catch (exception: IllegalAccessException) {
                throw IllegalStateException("Failed to call native Connection.fromChannel", exception)
            } catch (exception: InvocationTargetException) {
                throw IllegalStateException("Failed to call native Connection.fromChannel", exception)
            }
        }

        val connection = Connection(packetFlow)
        if (bandwidthLogger != null) {
            connection.setBandwidthLogger(bandwidthLogger)
        }

        val bandwidthMonitor = getBandwidthMonitor(connection)
        channel.pipeline().addLast("timeout", ReadTimeoutHandler(30))
        Connection.configureSerialization(channel.pipeline(), packetFlow, false, bandwidthMonitor)
        connection.configurePacketHandler(channel.pipeline())

        setField(CHANNEL_FIELD, connection, channel)
        val remoteAddress = channel.remoteAddress()
        setField(ADDRESS_FIELD, connection, remoteAddress ?: FALLBACK_REMOTE_ADDRESS)
        EVENT_LOOP_GROUP.register(channel).syncUninterruptibly()
        return connection
    }

    private fun findFromChannel(): Method? {
        return try {
            Connection::class.java.getDeclaredMethod(
                "fromChannel",
                Channel::class.java,
                PacketFlow::class.java,
                LocalSampleLogger::class.java
            )
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    private fun findField(name: String): Field {
        try {
            val field = Connection::class.java.getDeclaredField(name)
            field.setAccessible(true)
            return field
        } catch (exception: NoSuchFieldException) {
            throw ExceptionInInitializerError(exception)
        }
    }

    private fun getBandwidthMonitor(connection: Connection?): BandwidthDebugMonitor? {
        try {
            return BANDWIDTH_FIELD.get(connection) as BandwidthDebugMonitor?
        } catch (exception: IllegalAccessException) {
            throw IllegalStateException("Failed to read Connection bandwidth monitor", exception)
        }
    }

    private fun setField(field: Field, connection: Connection?, value: Any?) {
        try {
            field.set(connection, value)
        } catch (exception: IllegalAccessException) {
            throw IllegalStateException("Failed to set Connection field " + field.name, exception)
        }
    }
}