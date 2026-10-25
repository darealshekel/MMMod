package com.mmm.legacy.visual.mixin;

import java.util.Map;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.render.world.BlockMiningProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {
    @Accessor("miningProgress") Map<Integer, BlockMiningProgress> mmm$getMiningProgress();
}

