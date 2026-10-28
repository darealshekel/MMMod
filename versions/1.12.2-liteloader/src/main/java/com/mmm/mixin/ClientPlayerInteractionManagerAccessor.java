package com.mmm.mixin;

import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor
{
    @Accessor("isMiningBlock")
    boolean mmm$isBreakingBlock();

    @Accessor("target")
    BlockPos mmm$getCurrentBreakingPos();

    @Accessor("miningProgress")
    float mmm$getCurrentBreakingProgress();
}
