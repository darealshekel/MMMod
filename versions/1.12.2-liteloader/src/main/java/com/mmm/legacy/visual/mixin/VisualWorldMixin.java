package com.mmm.legacy.visual.mixin;

import com.mmm.config.Configs;
import com.mmm.feature.BlockEspRenderer;
import java.util.Collections;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.world.BlockMiningProgress;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class VisualWorldMixin {
    @Shadow @Final private Map<Integer, BlockMiningProgress> miningProgress;

    @Inject(method = "renderMiningProgress", at = @At("HEAD"), cancellable = true)
    private void mmm$replaceCracks(CallbackInfo ci) {
        if (Configs.Generic.BREAKING_INDICATORS.getBooleanValue()) ci.cancel();
    }
    @Redirect(method = "renderEntities", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/world/WorldRenderer;miningProgress:Ljava/util/Map;"))
    private Map<Integer, BlockMiningProgress> mmm$replaceBlockEntityCracks(WorldRenderer renderer) {
        return Configs.Generic.BREAKING_INDICATORS.getBooleanValue() ? Collections.emptyMap() : miningProgress;
    }
    @Inject(method = "renderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void mmm$replaceOutline(CallbackInfo ci) {
        if (BlockEspRenderer.shouldReplaceVanillaOutline(Minecraft.getInstance())) ci.cancel();
    }
}

