/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/30
 */


@file:OptIn(ExperimentalForeignApi::class)

package cn.rtast.peerlink.signaling.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

actual fun getenv(key: String): String? = platform.posix.getenv(key)?.toKString()