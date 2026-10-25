package com.mmm.mixin;

import com.mmm.config.Configs;
import com.mmm.feature.BlockEspRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin
{
    @Inject(method = "submitBlockDestroyAnimation", at = @At("HEAD"), cancellable = true)
    private void mmm$suppressVanillaBreakingOverlay(PoseStack matrices,
                                                    SubmitNodeCollector submitNodeCollector,
                                                    LevelRenderState worldRenderState,
                                                    CallbackInfo ci)
    {
        if (Configs.Generic.BREAKING_INDICATORS.getBooleanValue())
        {
            ci.cancel();
        }
    }

    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void mmm$renderCustomBlockEspOutline(PoseStack matrices,
                                                 SubmitNodeCollector submitNodeCollector,
                                                 LevelRenderState worldRenderState,
                                                 CallbackInfo ci)
    {
        Minecraft client = Minecraft.getInstance();
        if (!BlockEspRenderer.shouldReplaceVanillaOutline(client) || client.level == null)
        {
            return;
        }

        HitResult hitResult = client.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() == HitResult.Type.MISS)
        {
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        WorldBorder border = client.level.getWorldBorder();
        if (!border.isWithinBounds(pos))
        {
            ci.cancel();
            return;
        }

        BlockOutlineRenderState outlineRenderState = worldRenderState == null ? null : worldRenderState.blockOutlineRenderState;
        if (outlineRenderState == null)
        {
            ci.cancel();
            return;
        }

        Vec3 cameraPos = worldRenderState.cameraRenderState != null && worldRenderState.cameraRenderState.pos != null
                ? worldRenderState.cameraRenderState.pos
                : new Vec3(0.0D, 0.0D, 0.0D);
        matrices.pushPose();
        matrices.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
        submitNodeCollector.submitShapeOutline(
                matrices,
                outlineRenderState.shape(),
                RenderTypes.lines(),
                BlockEspRenderer.getCurrentOutlineColor(client),
                1.0F,
                outlineRenderState.isTranslucent()
        );
        matrices.popPose();
        ci.cancel();
    }
}
