package com.mmm.feature;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.render.Color4f;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public final class BlockEspRenderer
{
    private static final float RAINBOW_SATURATION = 0.9F;
    private static final float RAINBOW_BRIGHTNESS = 1.0F;
    private static final double BOX_EXPAND = 0.002D;
    private static boolean initialized;

    private BlockEspRenderer()
    {
    }

    public static void initialize()
    {
        if (initialized)
        {
            return;
        }
        initialized = true;
        WorldRenderEvents.LAST.register(context -> {
            MatrixStack matrices = context.matrixStack();
            if (matrices != null)
            {
                render(MinecraftClient.getInstance(), matrices, context.camera().getPos());
            }
        });
    }

    public static void refreshConfig()
    {
        Configs.Generic.BLOCK_ESP_HEX_COLOR.setValueFromString(Configs.normalizeBlockEspHexColor(Configs.Generic.BLOCK_ESP_HEX_COLOR.getStringValue()));
    }

    private static void render(MinecraftClient client, MatrixStack matrices, Vec3d camera)
    {
        if (!FeatureToggle.MMM_BLOCK_ESP.getBooleanValue() || Configs.isBlockEspOutlineOnly())
        {
            return;
        }

        BlockPos targetPos = getTargetBlock(client);
        if (targetPos == null)
        {
            return;
        }
        BlockState state = client.world.getBlockState(targetPos);
        VoxelShape shape = state.getOutlineShape(client.world, targetPos);
        if (shape.isEmpty())
        {
            return;
        }

        Box local = shape.getBoundingBox();
        double minX = targetPos.getX() + local.minX - camera.x - BOX_EXPAND;
        double minY = targetPos.getY() + local.minY - camera.y - BOX_EXPAND;
        double minZ = targetPos.getZ() + local.minZ - camera.z - BOX_EXPAND;
        double maxX = targetPos.getX() + local.maxX - camera.x + BOX_EXPAND;
        double maxY = targetPos.getY() + local.maxY - camera.y + BOX_EXPAND;
        double maxZ = targetPos.getZ() + local.maxZ - camera.z + BOX_EXPAND;
        Color4f baseColor = getCurrentColor();
        Color4f fill = Color4f.fromColor(baseColor, Configs.getBlockEspOpacity());
        Color4f outline = Color4f.fromColor(baseColor, Math.min(1.0F, Configs.getBlockEspOpacity() + 0.25F));

        matrices.push();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try
        {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferBuilder fillBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            WorldRenderer.renderFilledBox(matrices, fillBuffer, minX, minY, minZ, maxX, maxY, maxZ, fill.r, fill.g, fill.b, fill.a);
            BufferRenderer.drawWithGlobalProgram(fillBuffer.end());

            RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
            RenderSystem.lineWidth(1.0F);
            BufferBuilder lineBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            WorldRenderer.drawBox(matrices, lineBuffer, minX, minY, minZ, maxX, maxY, maxZ, outline.r, outline.g, outline.b, outline.a);
            BufferRenderer.drawWithGlobalProgram(lineBuffer.end());
        }
        finally
        {
            RenderSystem.lineWidth(1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            matrices.pop();
        }
    }

    private static Color4f getCurrentColor()
    {
        if (Configs.isBlockEspRainbow())
        {
            float cycleLengthMs = Math.max(250.0F, 5000.0F / Math.max(0.1F, Configs.getBlockEspRainbowSpeed()));
            float hue = (System.currentTimeMillis() % (long) cycleLengthMs) / cycleLengthMs;
            int rgb = java.awt.Color.HSBtoRGB(hue - (float) Math.floor(hue), RAINBOW_SATURATION, RAINBOW_BRIGHTNESS);
            return Color4f.fromColor(rgb | 0xFF000000);
        }

        String hex = Configs.normalizeBlockEspHexColor(Configs.Generic.BLOCK_ESP_HEX_COLOR.getStringValue());
        long parsed = Long.parseLong(hex.substring(1), 16);
        return Color4f.fromColor(hex.length() == 9 ? (int) parsed : (int) (0xFF000000L | parsed));
    }

    public static boolean shouldReplaceVanillaOutline(MinecraftClient client)
    {
        return FeatureToggle.MMM_BLOCK_ESP.getBooleanValue()
                && Configs.getBlockEspOpacity() > 0.0F
                && getTargetBlock(client) != null;
    }

    private static BlockPos getTargetBlock(MinecraftClient client)
    {
        if (client == null || client.player == null || client.world == null || Configs.getBlockEspOpacity() <= 0.0F)
        {
            return null;
        }
        HitResult hitResult = client.crosshairTarget;
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK)
        {
            return null;
        }
        BlockPos targetPos = blockHitResult.getBlockPos();
        return client.world.getBlockState(targetPos).isAir() ? null : targetPos;
    }

    public static int getCurrentOutlineColor(MinecraftClient client)
    {
        Color4f color = Color4f.fromColor(getCurrentColor(), Configs.getBlockEspOpacity());
        int alpha = Math.clamp(Math.round(color.a * 255.0F), 0, 255);
        int red = Math.clamp(Math.round(color.r * 255.0F), 0, 255);
        int green = Math.clamp(Math.round(color.g * 255.0F), 0, 255);
        int blue = Math.clamp(Math.round(color.b * 255.0F), 0, 255);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}