package com.mmm.mixin;

import com.mmm.config.Configs;
import com.mmm.feature.BlockEspRenderer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.SortedSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin
{
    @Unique
    private static final Long2ObjectMap<SortedSet<BlockBreakingInfo>> MMM_EMPTY_BREAKING_PROGRESSIONS =
            new Long2ObjectOpenHashMap<>();

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions;

    @Redirect(
            method = "renderBlockDamage",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/render/WorldRenderer;blockBreakingProgressions:Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;"
            )
    )
    private Long2ObjectMap<SortedSet<BlockBreakingInfo>> mmm$replaceVanillaBreakingOverlay(WorldRenderer renderer)
    {
        return Configs.Generic.BREAKING_INDICATORS.getBooleanValue()
                ? MMM_EMPTY_BREAKING_PROGRESSIONS
                : this.blockBreakingProgressions;
    }

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
