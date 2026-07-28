/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface ClientConnectionChannelAccessor {

    @Accessor("channel")
    Channel peerlink$getChannel();

    @Accessor("channel")
    @Mutable
    void peerlink$setChannel(Channel channel);
}
