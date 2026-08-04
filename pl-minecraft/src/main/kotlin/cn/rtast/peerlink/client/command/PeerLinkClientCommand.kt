/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/8/4
 */


package cn.rtast.peerlink.client.command

import cn.rtast.peerlink.client.webrtc.telemetry.ClientCandidateTypeTracker
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.commands.CommandBuildContext

class PeerLinkClientCommand : ClientCommandRegistrationCallback {
    override fun register(
        dispatcher: CommandDispatcher<FabricClientCommandSource>,
        buildContext: CommandBuildContext,
    ) {
        dispatcher.register(
            ClientCommands.literal("peerlink").then(
                ClientCommands.literal("ice").executes(::executeGetCandidateType)
            )
        )
    }

    private fun executeGetCandidateType(source: CommandContext<FabricClientCommandSource>): Int {
        source.source.sendFeedback(ClientCandidateTypeTracker.getCandidateType(source.source.player.uuid).descriptionComponent)
        return Command.SINGLE_SUCCESS
    }
}