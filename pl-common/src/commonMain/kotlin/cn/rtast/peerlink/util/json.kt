/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.util

import kotlinx.serialization.json.Json

val json = Json

inline fun <reified T> String.fromJson(): T = json.decodeFromString(this)

inline fun <reified T> T.encodeJson(): String = json.encodeToString(this)