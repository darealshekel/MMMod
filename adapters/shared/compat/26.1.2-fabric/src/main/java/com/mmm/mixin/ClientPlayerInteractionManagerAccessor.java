package com.mmm.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface ClientPlayerInteractionManagerAccessor
{
    @Accessor("isDestroying")
    boolean mmm$isBreakingBlock();

    @Accessor("destroyBlockPos")
    BlockPos mmm$getCurrentBreakingPos();

    @Accessor("destroyProgress")
    float mmm$getCurrentBreakingProgress();
}
