/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.mixin;

import cn.rtast.peerlink.client.webrtc.guest.WebRTCClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftDisconnectedMixin {

    @Inject(method = "setScreenAndShow", at = @At("HEAD"))
    private void peerlink$resetWebRTConSetScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof TitleScreen && Minecraft.getInstance().level != null) {
            WebRTCClientManager.reset();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void peerlink$resetWebRTConDisconnected(Screen screen, boolean keepResourcePacks, CallbackInfo ci) {
        if (screen instanceof TitleScreen || screen == null) {
            WebRTCClientManager.reset();
        }
    }
}
