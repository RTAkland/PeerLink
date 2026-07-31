/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.data.webrtc

import kotlinx.serialization.Serializable

@Serializable
data class OriginTurnCredentials(
    val iceServers: List<ICEServers>,
) {
    @Serializable
    data class ICEServers(
        val urls: List<String>,
        val username: String? = null,
        val credential: String? = null,
    )
}

fun OriginTurnCredentials.toTurnCredentials(): TurnCredentials {
    val stunServers = iceServers.flatMap { it.urls }
        .filter { it.startsWith("stun:", ignoreCase = true) || it.startsWith("stuns:", ignoreCase = true) }
    val turnServers = iceServers.flatMap { it.urls }
        .filter { it.startsWith("turn:", ignoreCase = true) || it.startsWith("turns:", ignoreCase = true) }
        .take(2)
    val turnNode = iceServers.firstOrNull { !it.username.isNullOrEmpty() && !it.credential.isNullOrEmpty() }
    val username = turnNode?.username ?: ""
    val password = turnNode?.credential ?: ""
    return TurnCredentials(
        stunServers = stunServers,
        turnServers = turnServers,
        username = username,
        password = password
    )
}

@Serializable
data class TurnCredentials(
    val stunServers: List<String>,
    val turnServers: List<String>,
    val username: String,
    val password: String,
)