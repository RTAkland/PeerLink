/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.mixin;

import cn.rtast.peerlink.client.webrtc.WebRTCClientManager;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;

@Mixin(Connection.class)
public class ClientConnectionMixin {

    @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    private static void onConnect(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
//        if (address.getHostName().startsWith("peerlink")) {
//            try {
//                ChannelFuture succeededFuture = WebRTCClientManager.injectToConnection(connection);
//                cir.setReturnValue(succeededFuture);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
    }
}
