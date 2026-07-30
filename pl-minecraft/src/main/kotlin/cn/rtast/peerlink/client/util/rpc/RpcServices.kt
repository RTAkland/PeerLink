/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.util.rpc

import cn.rtast.peerlink.service.AuthService
import cn.rtast.peerlink.service.MinecraftSignalingService
import cn.rtast.peerlink.service.ServerSignalingService
import kotlinx.rpc.RpcClient
import kotlinx.rpc.withService

fun RpcClient.minecraftSignalingService(): MinecraftSignalingService = withService()
fun RpcClient.serverSignalingService(): ServerSignalingService = withService()
fun RpcClient.authService(): AuthService = withService()