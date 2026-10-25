package com.mmm.legacy.visual.mixin;

import com.mmm.feature.BlockEspRenderer;
import com.mmm.feature.BreakingIndicatorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class VisualGameRendererMixin {
    // The camera matrices are still world-space here, after translucent terrain and before the hand.
    @Inject(method = "render(IFJ)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/GameRenderer;newCamPitch:Z"))
    private void mmm$worldOverlays(int pass, float partialTick, long deadline, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        BlockEspRenderer.render(client, partialTick);
        BreakingIndicatorRenderer.render(client, partialTick);
    }
}

