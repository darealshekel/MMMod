package com.mmm.legacy.visual.mixin;

import com.mmm.feature.TranslucentLavaRenderer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.LiquidRenderer;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LiquidRenderer.class)
public abstract class LavaRendererMixin {
    // Capture the render arguments, not mutable renderer fields: chunk builders run concurrently.
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/vertex/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/vertex/BufferBuilder;"))
    private BufferBuilder mmm$lavaAlpha(BufferBuilder buffer, float r, float g, float b, float a,
                                        WorldView world, BlockState state, BlockPos pos, BufferBuilder original) {
        return buffer.color(r, g, b, state.getMaterial() == Material.LAVA ? TranslucentLavaRenderer.vertexAlpha(a) : a);
    }
}

