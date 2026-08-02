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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PlayerPermissionMixin extends Player {

    public PlayerPermissionMixin(Level level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Inject(method = "permissions", at = @At("HEAD"), cancellable = true)
    private void peerlink$injectPermission(CallbackInfoReturnable<PermissionSet> cir) {
        if (PeerLinkHostScreen.Companion.getCurrentRoomState() != null) {
            var nameAndId = this.nameAndId();
            if (HostPlayerStorage.isOp(UuidKt.toKotlinUuid(nameAndId.id()))) {
                cir.setReturnValue(LevelBasedPermissionSet.OWNER);
            }
        }
    }
}
