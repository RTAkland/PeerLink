/*
 * Copyright © 2026 RTAkland
 * Author: RTAkland
 * Date: 2026/7/31
 */


package cn.rtast.peerlink.client.mixin.permission;

import cn.rtast.peerlink.client.screen.play.PeerLinkHostScreen;
import cn.rtast.peerlink.client.util.HostPlayerStorage;
import com.mojang.authlib.GameProfile;
import kotlin.uuid.UuidKt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PlayerPermissionMixin extends Player {

    public PlayerPermissionMixin(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @Inject(method = "getPermissionLevel", at = @At("HEAD"), cancellable = true)
    private void peerlink$injectPermission(CallbackInfoReturnable<Integer> cir) {
        if (PeerLinkHostScreen.Companion.getCurrentRoomState() != null) {
            if (HostPlayerStorage.isOp(UuidKt.toKotlinUuid(this.getGameProfile().getId()))) {
                cir.setReturnValue(4);
            }
        }
    }
}
