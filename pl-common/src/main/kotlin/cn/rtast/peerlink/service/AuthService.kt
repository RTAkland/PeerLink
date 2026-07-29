/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */

package cn.rtast.peerlink.service

import cn.rtast.peerlink.data.play.PlayerInfo
import kotlinx.rpc.annotations.Rpc

@Rpc
interface AuthService {
    suspend fun registerIdentity(player: PlayerInfo)
}