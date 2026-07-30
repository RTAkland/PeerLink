/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/30
 */

package cn.rtast.peerlink.signaling.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CoroutineConcurrentMap<K, V> {
    private val map = mutableMapOf<K, V>()
    private val mutex = Mutex()

    suspend fun put(key: K, value: V): V? = mutex.withLock { map.put(key, value) }

    suspend fun remove(key: K): V? = mutex.withLock { map.remove(key) }

    suspend fun containsKey(key: K): Boolean = mutex.withLock { map.containsKey(key) }

    suspend fun isEmpty(): Boolean = mutex.withLock { map.isEmpty() }

    suspend fun isNotEmpty(): Boolean = mutex.withLock { map.isNotEmpty() }

    suspend fun values(): List<V> = mutex.withLock { map.values.toList() }

    suspend fun keys(): Set<K> = mutex.withLock { map.keys.toSet() }

    suspend fun computeIfAbsent(key: K, mappingFunction: suspend (K) -> V): V {
        return mutex.withLock {
            val existing = map[key]
            if (existing != null) {
                existing
            } else {
                val computed = mappingFunction(key)
                map[key] = computed
                computed
            }
        }
    }

    suspend operator fun get(key: K): V? = mutex.withLock { map[key] }

    suspend operator fun set(key: K, value: V) {
        put(key, value)
    }
}