/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.client.mixin.screen;

import cn.rtast.peerlink.client.gui.PeerLinkButtons;
import cn.rtast.peerlink.client.screen.play.PeerLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    protected MultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void peerlink$peerlinkEntrypointButton(CallbackInfo ci) {
        int x = this.width - 30;
        int y = 5;
        this.addRenderableWidget(PeerLinkButtons.peerlinkIconButton(new PeerLinkScreen(this),
                Component.translatable("peerlink.entrypoint.button"),
                new ResourceLocation("peerlink", "icon/webrtc_multiplayer"),
                x, y
        ));
    }
}
