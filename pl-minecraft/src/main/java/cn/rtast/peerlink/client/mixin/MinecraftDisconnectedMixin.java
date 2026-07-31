/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/28
 */


package cn.rtast.peerlink.client.mixin;

import cn.rtast.peerlink.client.webrtc.guest.WebRTCClientManager;
import cn.rtast.peerlink.client.webrtc.host.WebRTCHostManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftDisconnectedMixin {

    @Inject(method = "exitWorldAndClose", at = @At("RETURN"))
    private void peerlink$resetWebRTConSetScreen(CallbackInfo ci) {
        WebRTCClientManager.cancelAll();
        WebRTCHostManager.stopHosting();
    }
}
