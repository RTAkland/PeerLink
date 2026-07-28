/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.util

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.rpc.RpcClient
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.serialization.json.json

val httpClient = HttpClient {
    installKrpc()
}

fun HttpClient.rpcClient(url: String): RpcClient =
    rpc {
        url(url)
        rpcConfig { serialization { json() } }
    }