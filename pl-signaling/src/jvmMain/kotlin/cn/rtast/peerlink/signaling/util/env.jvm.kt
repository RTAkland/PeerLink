/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.signaling.util

actual fun getenv(key: String): String? = System.getenv(key)