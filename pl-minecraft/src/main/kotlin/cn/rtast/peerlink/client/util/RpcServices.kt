/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.util

import cn.rtast.peerlink.service.MinecraftSignalingService
import kotlinx.rpc.RpcClient
import kotlinx.rpc.withService

fun RpcClient.minecraftSignalingService(): MinecraftSignalingService =
    withService()