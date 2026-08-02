/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/27
 */


package cn.rtast.peerlink.signaling

import cn.rtast.peerlink.signaling.data.SignalingServerConfig
import cn.rtast.peerlink.signaling.kv.CloudflareKvRepositoryImpl
import cn.rtast.peerlink.signaling.routes.registerRpcRouting
import cn.rtast.peerlink.signaling.routes.service.registerIndexRouting
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

lateinit var signalingConfig: SignalingServerConfig
lateinit var cloudflareKvRepository: CloudflareKvRepositoryImpl

fun main(args: Array<String>) {
    val parser = ArgParser("peerlink-signaling-server")
    val port by parser.option(
        ArgType.Int,
        "port", "p",
        "Http server listing port"
    ).default(7879)

    val configPath by parser.option(
        ArgType.String,
        shortName = "c",
        fullName = "config",
        description = "Config file path"
    ).default("config.json")

    parser.parse(args)

    @Suppress("LocalVariableName")
    val _defaultConfigPath = Path("config.json")
    if (!SystemFileSystem.exists(_defaultConfigPath)) {
        SignalingServerConfig.generateDefaultConfigFile(Path("config.json"))
        println("Default config file generated.")
        return
    }

    val path = Path(configPath)
    if (!SystemFileSystem.exists(path)) {
        println("Config file not exists $path")
        return
    }
    signalingConfig = SignalingServerConfig.readConfig(path)
    cloudflareKvRepository = CloudflareKvRepositoryImpl(
        signalingConfig.cloudflareAccountId,
        signalingConfig.cloudflareKvId,
        signalingConfig.cloudflareAccountToken
    )
    println("Listening on port $port.")
    embeddedServer(CIO, port = port, module = Application::module).start(true)
}

fun Application.module() {
    registerRpcRouting(cloudflareKvRepository, signalingConfig)
    registerIndexRouting()
}