package com.mmm.mixin;

import com.mmm.feature.FlatDigger;
import com.mmm.feature.BreakingIndicatorRenderer;
import com.mmm.config.Configs;
import com.mmm.feature.PerimeterWallDigHelper;
import com.mmm.timer.MmmBlockBreakDetector;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin
{
    @Unique private Box mmm$indicatorBounds;
    @Unique private ClientWorld mmm$indicatorWorld;

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void mmm$captureIndicator(BlockPos pos, CallbackInfoReturnable<Boolean> cir)
    {
        mmm$indicatorBounds = null;
        mmm$indicatorWorld = null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!Configs.Generic.BREAKING_INDICATORS.getBooleanValue() || client.world == null) return;
        var state = client.world.getBlockState(pos);
        var shape = state.getOutlineShape(client.world, pos);
        if (!state.isAir() && !shape.isEmpty())
        {
            mmm$indicatorWorld = client.world;
            mmm$indicatorBounds = shape.getBoundingBox().offset(pos);
        }
    }

    @Inject(method = "breakBlock", at = @At("RETURN"))
    private void mmm$completeIndicator(BlockPos pos, CallbackInfoReturnable<Boolean> cir)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (cir.getReturnValueZ() && mmm$indicatorBounds != null && client.world == mmm$indicatorWorld)
            BreakingIndicatorRenderer.completed(client, pos, mmm$indicatorBounds);
        mmm$indicatorBounds = null;
        mmm$indicatorWorld = null;
    }

    @Inject(method = "attackBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void mmm$blockAttackBelowFeet(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir)
    {
        if (FlatDigger.shouldBlock(pos) || PerimeterWallDigHelper.isPositionDisallowed(pos))
        {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        MmmBlockBreakDetector.trackAttack(pos);
    }

    @Inject(method = "updateBlockBreakingProgress(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void mmm$blockProgressBelowFeet(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir)
    {
        if (FlatDigger.shouldBlock(pos) || PerimeterWallDigHelper.isPositionDisallowed(pos))
        {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
