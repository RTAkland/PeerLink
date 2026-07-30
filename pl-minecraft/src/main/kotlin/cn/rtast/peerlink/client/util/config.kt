/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.client.util

import cn.rtast.peerlink.util.encodeJson
import cn.rtast.peerlink.util.fromJson
import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class Config(
    val signalingServer: String,
)

val defaultConfig = Config("wss://peerlink-signaling.rtast.cn")
val configFile: Path = FabricLoader.getInstance().configDir.resolve("peerlink.json")

fun readConfig(): Config {
    if (!configFile.exists()) configFile.writeText(defaultConfig.encodeJson())
    return configFile.readText().fromJson()
}