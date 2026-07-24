package com.mmm.mixin;

import com.mmm.feature.BlockEspRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin
{
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void mmm$drawCustomBlockEspOutline(MatrixStack matrices,
                                               VertexConsumer vertexConsumer,
                                               Entity entity,
                                               double cameraX,
                                               double cameraY,
                                               double cameraZ,
                                               BlockPos pos,
                                               BlockState state,
                                               int vanillaColor,
                                               CallbackInfo ci)
    {
        if (!BlockEspRenderer.shouldReplaceVanillaOutline(this.client) || this.client.world == null || state.isAir())
        {
            return;
        }

        int color = BlockEspRenderer.getCurrentOutlineColor(this.client);
        VertexRendering.drawOutline(
                matrices,
                vertexConsumer,
                state.getOutlineShape(this.client.world, pos, ShapeContext.of(entity)),
                pos.getX() - cameraX,
                pos.getY() - cameraY,
                pos.getZ() - cameraZ,
                color
        );
        ci.cancel();
    }
}
