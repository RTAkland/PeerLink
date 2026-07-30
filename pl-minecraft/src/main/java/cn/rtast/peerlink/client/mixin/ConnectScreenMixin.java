/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.mixin;

import cn.rtast.peerlink.client.network.ConnectionFactory;
import cn.rtast.peerlink.client.webrtc.WebRTCChannel;
import cn.rtast.peerlink.client.webrtc.guest.WebRTCClientManager;
import dev.kastle.webrtc.RTCDataChannel;
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
import org.spongepowered.asm.mixin.Unique;
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
        if (host.endsWith(".peerlink-vitural-host")) {
            ci.cancel();
            Thread peerLinkThread = new Thread(() -> {
                try {
                    WebRTCClientManager.INSTANCE.awaitDataChannelReady(10);
                    RTCDataChannel activeDataChannel = WebRTCClientManager.getActiveDataChannel();
                    minecraft.execute(() -> {
                        try {
                            if (minecraft.level != null || minecraft.getSingleplayerServer() != null) {
                                minecraft.disconnectWithProgressScreen(false);
                            }
                            Connection customConnection = ConnectionFactory.fromChannel(
                                    new WebRTCChannel(activeDataChannel),
                                    PacketFlow.CLIENTBOUND,
                                    minecraft.getDebugOverlay().getBandwidthLogger()
                            );

                            ConnectScreenMixin.this.connection = customConnection;
                            ServerData serverData = new ServerData("PeerLink", "peerlink-host", ServerData.Type.LAN);
                            customConnection.initiateServerboundPlayConnection(
                                    "peerlink-host",
                                    0,
                                    LoginProtocols.SERVERBOUND,
                                    LoginProtocols.CLIENTBOUND,
                                    new ClientHandshakePacketListenerImpl(
                                            customConnection,
                                            minecraft,
                                            serverData,
                                            null,
                                            false,
                                            null,
                                            _ -> {
                                            },
                                            new LevelLoadTracker(),
                                            null
                                    ),
                                    false
                            );

                            customConnection.send(new ServerboundHelloPacket(
                                    minecraft.getUser().getName(),
                                    minecraft.getUser().getProfileId()
                            ));
                            Field pendingField = Minecraft.class.getDeclaredField("pendingConnection");
                            fieldSet(pendingField, minecraft, customConnection);
                        } catch (Exception innerEx) {
                            innerEx.printStackTrace();
                            minecraft.gui.setScreen(
                                    new DisconnectedScreen(
                                            ((ConnectScreen) (Object) ConnectScreenMixin.this).parent,
                                            Component.literal("PeerLink 连接初始化失败"),
                                            Component.literal(innerEx.getMessage() != null ? innerEx.getMessage() : innerEx.toString())
                                    )
                            );
                        }
                    });

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
            }, "PeerLink-Server-Connector");
            peerLinkThread.start();
        }
    }

    @Unique
    private static void fieldSet(Field field, Object target, Object value) throws Exception {
        field.setAccessible(true);
        field.set(target, value);
    }
}