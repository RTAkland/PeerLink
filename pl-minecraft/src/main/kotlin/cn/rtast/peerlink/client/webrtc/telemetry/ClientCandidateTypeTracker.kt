/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/4
 */


package cn.rtast.peerlink.client.webrtc.telemetry

import dev.kastle.webrtc.RTCPeerConnection
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

object ClientCandidateTypeTracker {
    private var tickCounter = 0
    private val connectionTypes = ConcurrentHashMap<Uuid, IceCandidateType>()
    private val peerConnections = ConcurrentHashMap<Uuid, RTCPeerConnection>()

    private fun onClientTick() = runCatching {
        peerConnections.forEach { (uuid, connection) ->
            IceCandidateType.getActiveCandidateType(connection) { type ->
                connectionTypes[uuid] = type
            }
        }
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player == null) return@register
            tickCounter++
            if (tickCounter >= 50) {
                tickCounter = 0
                onClientTick()
            }
        }
    }

    fun clearAll() {
        connectionTypes.clear()
        peerConnections.clear()
    }

    fun registerPlayer(player: Uuid, peerConnection: RTCPeerConnection) {
        peerConnections[player] = peerConnection
    }

    fun unregisterPlayer(player: Uuid) {
        connectionTypes.remove(player)
        peerConnections.remove(player)
    }

    @JvmStatic
    fun getCandidateType(player: UUID): IceCandidateType =
        connectionTypes[player.toKotlinUuid()] ?: IceCandidateType.UNKNOWN
}