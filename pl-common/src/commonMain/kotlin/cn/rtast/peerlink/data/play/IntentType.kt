/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/30
 */


package cn.rtast.peerlink.data.play

import kotlinx.serialization.Serializable

@Serializable
enum class IntentType {
    /**
     * 请求加入房间
     */
    JOIN_REQUEST,

    /**
     * 离开房间
     * 客户端发送
     */
    LEAVE_ROOM,

    /**
     * 同意加入
     */
    ACCEPT_JOIN,

    /**
     * 拒绝加入
     */
    REJECT_JOIN,

    /**
     * 踢出玩家
     */
    KICK_PLAYER
}