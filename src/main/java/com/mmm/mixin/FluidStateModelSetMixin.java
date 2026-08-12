package com.mmm.mixin;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FluidStateModelSet.class)
public abstract class FluidStateModelSetMixin
{
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Map<Fluid, FluidModel> mmm$useTranslucentLavaLayer(Map<Fluid, FluidModel> models)
    {
        FluidModel lava = models.get(Fluids.LAVA);
        if (lava == null)
        {
            lava = models.get(Fluids.FLOWING_LAVA);
        }
        if (lava == null || lava.layer() == ChunkSectionLayer.TRANSLUCENT)
        {
            return models;
        }

        FluidModel translucentLava = new FluidModel(
                ChunkSectionLayer.TRANSLUCENT,
                lava.stillMaterial(),
                lava.flowingMaterial(),
                lava.overlayMaterial(),
                lava.tintSource()
        );
        Map<Fluid, FluidModel> updated = new HashMap<>(models);
        updated.put(Fluids.LAVA, translucentLava);
        updated.put(Fluids.FLOWING_LAVA, translucentLava);
        return updated;
    }
}
