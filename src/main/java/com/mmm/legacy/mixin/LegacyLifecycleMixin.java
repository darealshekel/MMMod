package com.mmm.legacy.mixin;

import com.mmm.legacy.LegacyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class LegacyLifecycleMixin {
    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("HEAD"))
    private void mmm$leave(ClientWorld next, String message, CallbackInfo ci) {
        if (next == null) LegacyClient.beforeWorldExit();
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void mmm$saveBeforeStop(CallbackInfo ci) { LegacyClient.shutdown(); }
}
