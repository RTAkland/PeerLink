/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.mixin;

import cn.rtast.peerlink.client.util.network.ConnectionInjector;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(ConnectScreen.class)
public class WebRTCJoinGameMixin {

    @Shadow
    private volatile Connection connection;

    @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    private void overrideConnect(Minecraft minecraft, ServerAddress hostAndPort, ServerData server, TransferState transferState, CallbackInfo ci) {
        String host = hostAndPort.getHost();
        if (host.endsWith(".peerlink-virtual-host")) {
            ci.cancel();
            Thread peerLinkThread = new Thread(() -> {
                try {
                    WebRTCClientManager.awaitDataChannelReady(20);
                    RTCDataChannel activeDataChannel = WebRTCClientManager.getActiveDataChannel();
                    minecraft.execute(() -> {
                        try {
                            if (minecraft.level != null || minecraft.getSingleplayerServer() != null) {
                                minecraft.disconnectWithProgressScreen(false);
                            }
                            assert activeDataChannel != null;
                            Connection virtualRtcConnection = ConnectionInjector.fromChannel(
                                    new WebRTCChannel(activeDataChannel),
                                    PacketFlow.CLIENTBOUND,
                                    minecraft.getDebugOverlay().getBandwidthLogger()
                            );

                            WebRTCJoinGameMixin.this.connection = virtualRtcConnection;
                            ServerData serverData = new ServerData("PeerLink", "peerlink-host", ServerData.Type.LAN);
                            virtualRtcConnection.initiateServerboundPlayConnection(
                                    "peerlink-host",
                                    0,
                                    LoginProtocols.SERVERBOUND,
                                    LoginProtocols.CLIENTBOUND,
                                    new ClientHandshakePacketListenerImpl(
                                            virtualRtcConnection,
                                            minecraft,
                                            serverData,
                                            null,
                                            false,
                                            null,
                                            _ -> {
                                            },
                                            new LevelLoadTracker(),
                                            null
                                    ), false
                            );

                            virtualRtcConnection.send(new ServerboundHelloPacket(
                                    minecraft.getUser().getName(),
                                    minecraft.getUser().getProfileId()
                            ));
                            Field pendingField = Minecraft.class.getDeclaredField("pendingConnection");
                            pendingField.setAccessible(true);
                            pendingField.set(minecraft, virtualRtcConnection);
                        } catch (Exception innerEx) {
                            innerEx.printStackTrace();
                            minecraft.gui.setScreen(
                                    new DisconnectedScreen(
                                            ((ConnectScreen) (Object) WebRTCJoinGameMixin.this).parent,
                                            Component.translatable("peerlink.p2p.initialFailed"),
                                            Component.literal(innerEx.getMessage() != null ? innerEx.getMessage() : innerEx.toString())
                                    )
                            );
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    minecraft.execute(() -> minecraft.gui.setScreen(
                            new DisconnectedScreen(
                                    ((ConnectScreen) (Object) WebRTCJoinGameMixin.this).parent,
                                    Component.translatable("peerlink.p2p.failed"),
                                    Component.literal(e.getMessage() != null ? e.getMessage() : e.toString())
                            )
                    ));
                }
            }, "PeerLink-Server-Connector");
            peerLinkThread.start();
        }
    }
}