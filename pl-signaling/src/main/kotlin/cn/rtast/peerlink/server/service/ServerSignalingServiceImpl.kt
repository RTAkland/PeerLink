/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.server.service

import cn.rtast.peerlink.data.ICEServerConfig
import cn.rtast.peerlink.data.OriginICEServerConfig
import cn.rtast.peerlink.data.ServerInfo
import cn.rtast.peerlink.data.toICEServerConfig
import cn.rtast.peerlink.server.CLOUDFLARE_TURN_TOKEN_ID
import cn.rtast.peerlink.server.CLOUDFLARE_TURN_TOKEN_KEY
import cn.rtast.peerlink.server.SIGNALING_SERVER_VERSION
import cn.rtast.peerlink.server.httpClient
import cn.rtast.peerlink.service.ServerSignalingService
import cn.rtast.peerlink.util.fromJson
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText

class ServerSignalingServiceImpl : ServerSignalingService {
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