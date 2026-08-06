/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.client.mixin.screen;

import cn.rtast.peerlink.client.gui.PeerLinkButtons;
import cn.rtast.peerlink.client.screen.play.HostManagementScreen;
import cn.rtast.peerlink.client.screen.play.PeerLinkHostScreen;
import cn.rtast.peerlink.client.screen.play.PendingJoinRequestsScreen;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void peerlink$addPeerLinkHostScreenButton(CallbackInfo ci) {
        if (this.minecraft != null && this.minecraft.isLocalServer()) {
            LinearLayout topLeftLayout = LinearLayout.horizontal().spacing(4);
            topLeftLayout.addChild(PeerLinkButtons.peerlinkIconButton(
                    new PeerLinkHostScreen(this),
                    Component.translatable("peerlink.entrypoint.button"),
                    new ResourceLocation("peerlink", "icon/webrtc_multiplayer"),
                    -1, -1
            ));

            topLeftLayout.addChild(PeerLinkButtons.peerlinkIconButton(
                    new PendingJoinRequestsScreen(this),
                    Component.translatable("peerlink.pendingJoinRequests"),
                    new ResourceLocation("peerlink", "icon/management/pending_join_request"),
                    -1, -1, 15, 15
            ));

            topLeftLayout.addChild(PeerLinkButtons.peerlinkIconButton(
                    new HostManagementScreen(this),
                    Component.translatable("peerlink.hostManagement"),
                    new ResourceLocation("peerlink", "icon/management/host_management"),
                    -1, -1, 15, 14
            ));
            topLeftLayout.arrangeElements();
            topLeftLayout.setPosition(5, 5);
            topLeftLayout.visitWidgets(this::addRenderableWidget);
        }
    }
}
