/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.signaling.util

val TURN_TYPE = getenv("TURN_TYPE")
val CLOUDFLARE_TURN_TOKEN_ID = getenv("CLOUDFLARE_TURN_TOKEN_ID")
val CLOUDFLARE_TURN_TOKEN_KEY = getenv("CLOUDFLARE_TURN_TOKEN_KEY")

val STUN_SERVERS = getenv("STUN_SERVERS")
val TURN_SERVERS = getenv("TURN_SERVERS")
val TURN_USERNAME = getenv("TURN_USERNAME")
val TURN_PASSWORD = getenv("TURN_PASSWORD")

val CLOUDFLARE_ACCOUNT_ID = getenv("CLOUDFLARE_ACCOUNT_ID")
val CLOUDFLARE_KV = getenv("CLOUDFLARE_KV")
val CLOUDFLARE_TOKEN = getenv("CLOUDFLARE_TOKEN")