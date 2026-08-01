/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/1
 */

@file:Suppress("FunctionName")

package cn.rtast.peerlink.client.util

import cn.rtast.peerlink.client.minecraft
import cn.rtast.peerlink.data.play.SignalingMessage
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

fun _offer(target: Uuid, payload: String): SignalingMessage =
    SignalingMessage(
        minecraft.user.profileId.toKotlinUuid(),
        target, SignalingMessage.SignalingType.Offer, payload
    )

fun _answer(target: Uuid, payload: String): SignalingMessage =
    SignalingMessage(
        minecraft.user.profileId.toKotlinUuid(),
        target, SignalingMessage.SignalingType.Answer, payload
    )

fun _ice(target: Uuid, payload: String): SignalingMessage =
    SignalingMessage(
        minecraft.user.profileId.toKotlinUuid(),
        target, SignalingMessage.SignalingType.Ice, payload
    )