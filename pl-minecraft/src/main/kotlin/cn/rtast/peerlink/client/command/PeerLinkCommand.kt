/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.command

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class PeerLinkCommand : CommandRegistrationCallback {
    override fun register(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        buildContext: CommandBuildContext,
        selection: Commands.CommandSelection,
    ) {
//        dispatcher.register(Commands.literal(""))
    }
}