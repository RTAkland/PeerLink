/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/2
 */


package cn.rtast.peerlink.signaling.data

import cn.rtast.peerlink.data.webrtc.TurnCredentials
import cn.rtast.peerlink.util.encodeJson
import cn.rtast.peerlink.util.fromJson
import io.ktor.utils.io.core.*
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlinx.serialization.Serializable

@Serializable
data class SignalingServerConfig(
    val useCloudflareTurn: Boolean = true,
    val cloudflareTurnTokenId: String = "AFakeCFTurnTokenId",
    val cloudflareTurnTokenKey: String = "AFakeCFTurnTokenKey",
    val cloudflareAccountId: String = "FakeAccountId",
    val cloudflareAccountToken: String = "FakeAccountToken",
    val cloudflareKvId: String = "FakeCloudflareKvId",
    val customStunConfig: TurnCredentials = TurnCredentials(
        listOf("stun:stun.l.google.com:19302", "stun:stun.chat.bilibili.com:3478"),
        listOf(TurnCredentials.TurnServer(listOf("turn:turn.example.com"), "USERNAME", "PASSWORD"))
    ),
) {
    companion object {
        fun generateDefaultConfigFile(path: Path) = SystemFileSystem.sink(path).buffered().use {
            val config = SignalingServerConfig().encodeJson()
            it.writeString(config)
        }

        fun readConfig(path: Path): SignalingServerConfig =
            SystemFileSystem.source(path).buffered().use { it.readText().fromJson() }
    }
}