/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.signaling.service

import cn.rtast.peerlink.data.ICEServerConfig
import cn.rtast.peerlink.data.OriginICEServerConfig
import cn.rtast.peerlink.data.ServerInfo
import cn.rtast.peerlink.data.toICEServerConfig
import cn.rtast.peerlink.signaling.CLOUDFLARE_TURN_TOKEN_ID
import cn.rtast.peerlink.signaling.CLOUDFLARE_TURN_TOKEN_KEY
import cn.rtast.peerlink.signaling.SIGNALING_SERVER_VERSION
import cn.rtast.peerlink.signaling.data.ServiceContext
import cn.rtast.peerlink.signaling.httpClient
import cn.rtast.peerlink.service.ServerSignalingService
import cn.rtast.peerlink.util.fromJson
import io.ktor.client.request.*
import io.ktor.client.statement.*

class ServerSignalingServiceImpl(
    private val context: ServiceContext,
) : ServerSignalingService {
    override suspend fun serverInfo(): ServerInfo = ServerInfo(SIGNALING_SERVER_VERSION)

    override suspend fun acquireICEServerConfig(): ICEServerConfig {
        val resp =
            httpClient.post("https://rtc.live.cloudflare.com/v1/turn/keys/$CLOUDFLARE_TURN_TOKEN_ID/credentials/generate-ice-servers") {
                headers {
                    header("Authorization", "Bearer $CLOUDFLARE_TURN_TOKEN_KEY")
                    header("Content-Type", "application/json")
                }
                setBody("{\"ttl\":3600}")
            }.bodyAsText().fromJson<OriginICEServerConfig>().toICEServerConfig()
        return resp
    }
}