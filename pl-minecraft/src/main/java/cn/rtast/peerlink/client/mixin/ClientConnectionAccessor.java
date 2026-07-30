/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.net.SocketAddress;

@Mixin(Connection.class)
public interface ClientConnectionAccessor {

    @Accessor("channel")
    Channel peerlink$getChannel();

    @Accessor("channel")
    @Mutable
    void peerlink$setChannel(Channel channel);

    @Accessor("address")
    SocketAddress peerlink$getAddress();

    @Accessor("address")
    void peerlink$setAddress(SocketAddress address);

    @Accessor("bandwidthDebugMonitor")
    BandwidthDebugMonitor peerlink$getBandwidthDebugMonitor();

    @Accessor("bandwidthDebugMonitor")
    void peerlink$setBandwidthDebugMonitor(BandwidthDebugMonitor bandwidthDebugMonitor);
}
