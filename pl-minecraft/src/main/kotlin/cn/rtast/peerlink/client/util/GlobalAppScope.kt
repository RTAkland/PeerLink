/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.client.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

class GlobalAppScope : CoroutineScope {
    private var job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    fun cancelAll() {
        job.cancel()
        job = SupervisorJob()
    }
}