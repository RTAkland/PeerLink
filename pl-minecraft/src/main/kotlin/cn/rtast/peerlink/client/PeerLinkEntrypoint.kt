/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client

import cn.rtast.peerlink.client.util.RpcManager
import cn.rtast.peerlink.client.util.readConfig
import cn.rtast.peerlink.client.webrtc.guest.WebRTCClientManager
import cn.rtast.peerlink.client.webrtc.host.WebRTCHostManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents


class PeerLinkEntrypoint : ModInitializer {
    override fun onInitialize() {
        RpcManager.start(readConfig().signalingServer)
        ClientLifecycleEvents.CLIENT_STOPPING.register { _ ->
            RpcManager.stop()
            WebRTCHostManager.stopHosting()
            WebRTCClientManager.reset()
        }
    }
}