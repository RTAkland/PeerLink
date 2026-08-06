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
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
        if (this.minecraft.isLocalServer()) {
            LinearLayout topLeftLayout = LinearLayout.horizontal().spacing(4); // 4px 按钮间距
            topLeftLayout.addChild(PeerLinkButtons.peerlinkIconButton(
                    new PeerLinkHostScreen(this),
                    Component.translatable("peerlink.entrypoint.button"),
                    Identifier.fromNamespaceAndPath("peerlink", "icon/webrtc_multiplayer"),
                    -1, -1
            ));

            topLeftLayout.addChild(PeerLinkButtons.peerlinkIconButton(
                    new PendingJoinRequestsScreen(this),
                    Component.translatable("peerlink.pendingJoinRequests"),
                    Identifier.fromNamespaceAndPath("peerlink", "icon/management/pending_join_request"),
                    -1, -1, 15, 15
            ));

            topLeftLayout.addChild(PeerLinkButtons.peerlinkIconButton(
                    new HostManagementScreen(this),
                    Component.translatable("peerlink.hostManagement"),
                    Identifier.fromNamespaceAndPath("peerlink", "icon/management/host_management"),
                    -1, -1, 15, 14
            ));
            topLeftLayout.arrangeElements();
            topLeftLayout.setPosition(5, 5);
            topLeftLayout.visitWidgets(this::addRenderableWidget);
        }
    }
}
