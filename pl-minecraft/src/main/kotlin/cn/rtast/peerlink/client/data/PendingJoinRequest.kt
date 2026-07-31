/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.client.data

import kotlinx.coroutines.Job
import kotlin.uuid.Uuid

data class PendingJoinRequest(
    val applicantId: Uuid,
    val applicantName: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val timeoutJob: Job
)