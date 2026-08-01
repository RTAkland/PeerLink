/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.signaling.kv

import cn.rtast.peerlink.data.play.PlayerInfo
import cn.rtast.peerlink.data.play.RoomState
import cn.rtast.peerlink.signaling.httpClient
import cn.rtast.peerlink.util.encodeJson
import cn.rtast.peerlink.util.fromJson
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlin.uuid.Uuid

class CloudflareKvRepositoryImpl(
    private val accountId: String,
    private val namespaceId: String,
    private val apiToken: String
) : CloudflareKvRepository {

    private val baseUrl = "https://api.cloudflare.com/client/v4/accounts/$accountId/storage/kv/namespaces/$namespaceId"

    private fun hostKey(roomId: String) = "room:$roomId:host"
    private fun playersKey(roomId: String) = "room:$roomId:players"
    private fun playerRoomKey(playerId: Uuid) = "player_room:$playerId"

    private suspend fun kvGet(key: String): String? {
        return try {
            val response = httpClient.get("$baseUrl/values/$key") {
                header(HttpHeaders.Authorization, "Bearer $apiToken")
            }
            if (response.status.isSuccess()) response.bodyAsText() else null
        } catch (e: Throwable) {
            val realCause = e.cause ?: e
            realCause.printStackTrace()
            null
        }
    }

    private suspend fun kvPut(key: String, value: String, ttlSeconds: Long? = 86400) {
        httpClient.put("$baseUrl/values/$key") {
            header(HttpHeaders.Authorization, "Bearer $apiToken")
            header(HttpHeaders.ContentType, "text/plain")
            if (ttlSeconds != null) parameter("expiration_ttl", ttlSeconds)
            setBody(value)
        }
    }

    private suspend fun kvDelete(key: String) {
        httpClient.delete("$baseUrl/values/$key") {
            header(HttpHeaders.Authorization, "Bearer $apiToken")
        }
    }

    override suspend fun getRoomState(roomId: String): RoomState? {
        val hostId = getRoomHost(roomId) ?: return null
        val players = getRoomPlayers(roomId)
        return RoomState(roomId, hostId, players)
    }

    override suspend fun getRoomHost(roomId: String): Uuid? {
        val hostStr = kvGet(hostKey(roomId)) ?: return null
        return runCatching { Uuid.parse(hostStr) }.getOrNull()
    }

    override suspend fun setRoomHost(roomId: String, hostId: Uuid) {
        kvPut(hostKey(roomId), hostId.toString())
    }

    override suspend fun getPlayerRoom(playerId: Uuid): String? {
        return kvGet(playerRoomKey(playerId))
    }

    override suspend fun setPlayerRoom(playerId: Uuid, roomId: String) {
        kvPut(playerRoomKey(playerId), roomId)
    }

    override suspend fun removePlayerRoom(playerId: Uuid) {
        kvDelete(playerRoomKey(playerId))
    }

    override suspend fun addRoomPlayer(roomId: String, player: PlayerInfo) {
        val currentPlayers = getRoomPlayers(roomId).toMutableList()
        currentPlayers.removeAll { it.uuid == player.uuid }
        currentPlayers.add(player)
        kvPut(playersKey(roomId), currentPlayers.encodeJson())
    }

    override suspend fun removeRoomPlayer(roomId: String, playerId: Uuid) {
        val currentPlayers = getRoomPlayers(roomId).toMutableList()
        val changed = currentPlayers.removeAll { it.uuid == playerId }
        if (changed) {
            if (currentPlayers.isEmpty()) {
                kvDelete(playersKey(roomId))
            } else {
                kvPut(playersKey(roomId), currentPlayers.encodeJson())
            }
        }
    }

    override suspend fun getRoomPlayers(roomId: String): List<PlayerInfo> {
        val json = kvGet(playersKey(roomId)) ?: return emptyList()
        return runCatching { json.fromJson<List<PlayerInfo>>() }.getOrDefault(emptyList())
    }

    override suspend fun deleteRoom(roomId: String) {
        kvDelete(hostKey(roomId))
        kvDelete(playersKey(roomId))
    }

    override suspend fun roomExists(roomId: String): Boolean {
        return kvGet(hostKey(roomId)) != null
    }
}