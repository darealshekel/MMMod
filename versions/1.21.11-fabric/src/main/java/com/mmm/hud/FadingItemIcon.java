package com.mmm.hud;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2f;

/** 1.21.11 queues GUI items without alpha; composite a small item surface instead. */
final class FadingItemIcon implements AutoCloseable
{
    private static final int SIZE = 64;
    private final SimpleFramebuffer surface = new SimpleFramebuffer("MMM collection icon", SIZE, SIZE, true);
    private final ProjectionMatrix2 projection = new ProjectionMatrix2("MMM collection icon", -1000, 1000, true);
    private final OrderedRenderCommandQueueImpl queue = new OrderedRenderCommandQueueImpl();
    private final RenderDispatcher dispatcher;

    FadingItemIcon(MinecraftClient client)
    {
        var buffers = client.getBufferBuilders();
        dispatcher = new RenderDispatcher(queue, client.getBlockRenderManager(), buffers.getEntityVertexConsumers(),
                client.getAtlasManager(), buffers.getOutlineVertexConsumers(), buffers.getEffectVertexConsumers(), client.textRenderer);
    }

    void draw(DrawContext context, MinecraftClient client, ItemStack item, float opacity)
    {
        GuiRenderState state = new GuiRenderState();
        new DrawContext(client, state, 0, 0).drawItem(item, 0, 0);
        var vertices = client.getBufferBuilders().getEntityVertexConsumers();
        vertices.draw();
        var colorTarget = RenderSystem.outputColorTextureOverride;
        var depthTarget = RenderSystem.outputDepthTextureOverride;
        var oldProjection = RenderSystem.getProjectionMatrixBuffer();
        var oldProjectionType = RenderSystem.getProjectionType();
        var lights = RenderSystem.getShaderLights();
        try
        {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    surface.getColorAttachment(), 0, surface.getDepthAttachment(), 1);
            RenderSystem.outputColorTextureOverride = surface.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = surface.getDepthAttachmentView();
            RenderSystem.setProjectionMatrix(projection.set(SIZE, SIZE), ProjectionType.ORTHOGRAPHIC);
            MatrixStack matrices = new MatrixStack();
            matrices.translate(SIZE / 2F, SIZE / 2F, 0);
            matrices.scale(SIZE, -SIZE, SIZE);
            state.forEachItemElement(element -> {
                client.gameRenderer.getDiffuseLighting().setShaderLights(element.state().isSideLit()
                        ? DiffuseLighting.Type.ITEMS_3D : DiffuseLighting.Type.ITEMS_FLAT);
                element.state().render(matrices, queue, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0);
                dispatcher.render();
                vertices.draw();
            });
        }
        finally
        {
            queue.clear();
            RenderSystem.outputColorTextureOverride = colorTarget;
            RenderSystem.outputDepthTextureOverride = depthTarget;
            RenderSystem.setProjectionMatrix(oldProjection, oldProjectionType);
            RenderSystem.setShaderLights(lights);
        }
        int alpha = Math.clamp(Math.round(opacity * 255), 0, 255);
        int tint = alpha << 24 | alpha << 16 | alpha << 8 | alpha;
        context.state.addSimpleElement(new TexturedQuadGuiElementRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.of(surface.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST)),
                new Matrix3x2f(context.getMatrices()), 6, 6, 30, 30, 0, 1, 1, 0, tint,
                context.scissorStack.peekLast()));
    }

    @Override public void close()
    {
        dispatcher.close();
        projection.close();
        surface.delete();
    }
}
