package com.mmm.mixin;

import com.mmm.tweak.BlockEspRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.border.WorldBorder;
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

    @Shadow
    protected abstract void drawBlockOutline(MatrixStack matrices, VertexConsumer vertexConsumer,
                                             double cameraX, double cameraY, double cameraZ,
                                             OutlineRenderState outlineRenderState, int color, float tickProgress);

    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), cancellable = true)
    private void mmm$renderCustomBlockEspOutline(VertexConsumerProvider.Immediate vertexConsumers,
                                                 MatrixStack matrices,
                                                 boolean translucent,
                                                 WorldRenderState worldRenderState,
                                                 CallbackInfo ci)
    {
        if (!BlockEspRenderer.shouldReplaceVanillaOutline(this.client))
        {
            return;
        }

        HitResult hitResult = this.client.crosshairTarget;
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() == HitResult.Type.MISS)
        {
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        WorldBorder border = this.client.world.getWorldBorder();
        if (!border.contains(pos))
        {
            ci.cancel();
            return;
        }

        OutlineRenderState outlineRenderState = worldRenderState == null ? null : worldRenderState.outlineRenderState;
        if (outlineRenderState == null)
        {
            ci.cancel();
            return;
        }

        if (outlineRenderState.isTranslucent() != translucent)
        {
            ci.cancel();
            return;
        }

        Vec3d cameraPos = worldRenderState.cameraRenderState != null && worldRenderState.cameraRenderState.pos != null
                ? worldRenderState.cameraRenderState.pos
                : new Vec3d(0.0D, 0.0D, 0.0D);
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayers.lines());
        this.drawBlockOutline(
                matrices,
                vertexConsumer,
                cameraPos.x,
                cameraPos.y,
                cameraPos.z,
                outlineRenderState,
                BlockEspRenderer.getCurrentOutlineColor(this.client),
                0.0F
        );
        ci.cancel();
    }
}
