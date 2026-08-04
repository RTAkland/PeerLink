/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/4
 */


package cn.rtast.peerlink.client.webrtc.telemetry

import cn.rtast.peerlink.client.util.toTranslatable
import dev.kastle.webrtc.RTCPeerConnection
import dev.kastle.webrtc.RTCStatsReport
import dev.kastle.webrtc.RTCStatsType
import net.minecraft.network.chat.Component

enum class IceCandidateType(val nameComponent: Component, val descriptionComponent: Component) {
    HOST("p2p.candidate.host".toTranslatable(), "p2p.candidate.host.description".toTranslatable()),
    SRFLX("p2p.candidate.srflx".toTranslatable(), "p2p.candidate.srflx.description".toTranslatable()),
    PRFLX("p2p.candidate.prflx".toTranslatable(), "p2p.candidate.prflx.description".toTranslatable()),
    RELAY("p2p.candidate.relay".toTranslatable(), "p2p.candidate.relay.description".toTranslatable()),
    UNKNOWN("p2p.candidate.unknown".toTranslatable(), "p2p.candidate.unknown.description".toTranslatable());

    companion object {
        fun fromString(typeStr: String?): IceCandidateType =
            entries.firstOrNull { it.name.equals(typeStr, ignoreCase = true) } ?: UNKNOWN

        fun getActiveCandidateType(pc: RTCPeerConnection?, callback: (IceCandidateType) -> Unit) {
            if (pc == null) {
                callback(UNKNOWN)
                return
            }

            pc.getStats { report: RTCStatsReport ->
                var activeType = UNKNOWN
                val activePair = report.stats.values.firstOrNull {
                    it.type == RTCStatsType.CANDIDATE_PAIR && (it.attributes["state"] == "succeeded" || it.attributes["nominated"] == true)
                }
                if (activePair != null) {
                    val localCandidateId = activePair.attributes["localCandidateId"] as? String
                    val remoteCandidateId = activePair.attributes["remoteCandidateId"] as? String
                    val localStats = report.stats[localCandidateId]
                    val remoteStats = report.stats[remoteCandidateId]
                    val localTypeStr = localStats?.attributes["candidateType"] as? String
                    val remoteTypeStr = remoteStats?.attributes["candidateType"] as? String
                    val localType = fromString(localTypeStr)
                    val remoteType = fromString(remoteTypeStr)
                    activeType = when {
                        localType == RELAY || remoteType == RELAY -> RELAY
                        localType == PRFLX || remoteType == PRFLX -> PRFLX
                        localType == SRFLX || remoteType == SRFLX -> SRFLX
                        localType == HOST && remoteType == HOST -> HOST
                        else -> remoteType.takeIf { it != UNKNOWN } ?: localType
                    }
                }
                callback(activeType)
            }
        }
    }
}