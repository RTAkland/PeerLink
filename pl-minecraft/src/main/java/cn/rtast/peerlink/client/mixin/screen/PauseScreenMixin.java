/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/29
 */


package cn.rtast.peerlink.client.mixin.screen;

import cn.rtast.peerlink.client.gui.PeerLinkButtons;
import cn.rtast.peerlink.client.screen.PeerLinkHostScreen;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(
            method = "createPauseMenu", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/LinearLayout;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;",
            shift = At.Shift.AFTER,
            ordinal = 3)
    )
    private void peerlink$addPeerLinkHostScreenButton(CallbackInfo ci, @Local(name = "iconButtonRow") LinearLayout iconButtonRow) {
        if (minecraft.isLocalServer()) {
            iconButtonRow.addChild(PeerLinkButtons.peerlinkEntrypointButton(new PeerLinkHostScreen(this), -1, -1));
        }
    }
}
