/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client

import cn.rtast.peerlink.client.data.PeerLinkClientConfig
import cn.rtast.peerlink.client.gui.SignalingStatusIndicator
import cn.rtast.peerlink.client.network.PeerLinkManager
import cn.rtast.peerlink.client.network.RpcClient
import cn.rtast.peerlink.data.play.PlayerInfo
import cn.rtast.peerlink.util.encodeJson
import cn.rtast.peerlink.util.fromJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.uuid.toKotlinUuid


class PeerLinkInitializer : ModInitializer {
    companion object {
        lateinit var instance: PeerLinkInitializer
            private set
        var rpcClient: RpcClient? = null
            private set
        var manager: PeerLinkManager? = null
            private set
        val configFile: Path = FabricLoader.getInstance().configDir.resolve("peerlink.json")
            .apply { if (!this@apply.exists()) this@apply.writeText(defaultConfig.encodeJson()) }
        val config = configFile.readText().fromJson<PeerLinkClientConfig>()
    }

    override fun onInitialize() {
        instance = this
        ClientLifecycleEvents.CLIENT_STOPPING.register { _ -> shutdown() }
        initRpcAndManager(config.signalingServer)
        SignalingStatusIndicator.register()
    }

    fun initRpcAndManager(signalingUrl: String) {
        val rpc = RpcClient(signalingUrl, scope)
        rpcClient = rpc
        val peerLinkManager = PeerLinkManager(rpc, scope = scope)
        manager = peerLinkManager
        rpc.start()
    }

    fun shutdown() {
        scope.cancel()
//        runCatching { manager?.destroy() }
        runCatching { rpcClient?.destroy() }
        manager = null
        rpcClient = null
    }
}