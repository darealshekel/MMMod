package com.mmm.legacy.mixin;

import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class LegacyMiningMixin {
    @Unique private BlockState mmm$state;
    @Unique private ClientWorld mmm$world;

    @Inject(method = "finishMiningBlock", at = @At("HEAD"))
    private void mmm$capture(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        mmm$world = client.world;
        mmm$state = mmm$world == null ? null : mmm$world.getBlockState(pos);
    }

    @Inject(method = "finishMiningBlock", at = @At("RETURN"))
    private void mmm$completed(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        BlockState state = mmm$state;
        ClientWorld world = mmm$world;
        mmm$state = null;
        mmm$world = null;
        if (cir.getReturnValueZ() && state != null && world == client.world && client.player != null
                && client.interactionManager.getGameMode() == GameMode.SURVIVAL
                && FeatureToggle.MMM_MINING_TRACKER.getBooleanValue()) {
            MiningStats.recordBlockMined(state.getBlock(), pos, state);
        }
    }
}
