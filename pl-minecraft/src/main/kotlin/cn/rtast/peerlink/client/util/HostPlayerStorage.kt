/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.client.util

import cn.rtast.peerlink.util.encodeJson
import cn.rtast.peerlink.util.fromJson
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.level.storage.LevelResource
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.uuid.Uuid

object HostPlayerStorage {
    @Serializable
    private data class PeerLinkPlayerData(
        val uuid: Uuid,
        val name: String,
        var isOp: Boolean,
    )

    private val playersMap = ConcurrentHashMap<Uuid, PeerLinkPlayerData>()
    private var storageFile: File? = null

    fun init() {
        playersMap.clear()
        val server = Minecraft.getInstance().singleplayerServer ?: return
        val worldDir = server.getWorldPath(LevelResource.ROOT)
        storageFile = worldDir.resolve("peerlink-ops.json").toFile()
        load()
    }

    fun load() {
        val file = storageFile ?: return
        if (!file.exists()) return
        file.readText().fromJson<List<PeerLinkPlayerData>>().forEach { playersMap[it.uuid] = it }
    }

    fun save() {
        val file = storageFile ?: return
        file.writer(Charsets.UTF_8).use { writer -> writer.write(playersMap.values.toList().encodeJson()) }
    }

    fun setOp(uuid: Uuid, name: String, isOp: Boolean) {
        val player = playersMap.computeIfAbsent(uuid) { PeerLinkPlayerData(uuid, name, isOp) }
        player.isOp = isOp
        save()
    }

    @JvmStatic
    fun isOp(uuid: Uuid): Boolean {
        return playersMap[uuid]?.isOp ?: false
    }
}