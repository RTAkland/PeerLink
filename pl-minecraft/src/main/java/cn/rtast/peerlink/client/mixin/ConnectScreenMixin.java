/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.mixin;

import cn.rtast.peerlink.client.webrtc.WebRTCClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {

    @Shadow
    private volatile Connection connection;

    @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    private void overrideConnect(Minecraft minecraft, ServerAddress hostAndPort, ServerData server, TransferState transferState, CallbackInfo ci) {
        String host = hostAndPort.getHost();

        if (host.startsWith("peerlink-") || host.endsWith(".peerlink")) {
            ci.cancel();

            Thread peerLinkThread = new Thread("PeerLink-Server-Connector") {
                @Override
                public void run() {
                    try {
                        WebRTCClientManager.INSTANCE.awaitDataChannelReady(10);
                        Connection customConnection = new Connection(PacketFlow.CLIENTBOUND);
                        ConnectScreenMixin.this.connection = customConnection;
                        WebRTCClientManager.injectToConnection(customConnection);
                        customConnection.initiateServerboundPlayConnection(
                                "peerlink", 0,
                                LoginProtocols.SERVERBOUND,
                                LoginProtocols.CLIENTBOUND,
                                new ClientHandshakePacketListenerImpl(
                                        customConnection,
                                        minecraft,
                                        server,
                                        ((ConnectScreen) (Object) ConnectScreenMixin.this).parent,
                                        false,
                                        null,
                                        _ -> {},
                                        new LevelLoadTracker(),
                                        null
                                ), false
                        );
                        try {
                            Field field = Minecraft.class.getDeclaredField("pendingConnection");
                            field.setAccessible(true);
                            field.set(minecraft, customConnection);
                        } catch (ReflectiveOperationException exception) {
                            throw new IllegalStateException("Failed to set Minecraft pendingConnection", exception);
                        }

                        System.out.println("[PeerLink] 发送 ServerboundHelloPacket 发起登录请求...");
                        customConnection.send(new ServerboundHelloPacket(
                                minecraft.getUser().getName(),
                                minecraft.getUser().getProfileId()
                        ));

                    } catch (Exception e) {
                        e.printStackTrace();
                        minecraft.execute(() -> minecraft.gui.setScreen(
                                new DisconnectedScreen(
                                        ((ConnectScreen) (Object) ConnectScreenMixin.this).parent,
                                        Component.literal("PeerLink 连接失败"),
                                        Component.literal(e.getMessage() != null ? e.getMessage() : e.toString())
                                )
                        ));
                    }
                }
            };
            peerLinkThread.start();
        }
    }
}