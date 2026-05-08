package com.mmm.tweak;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mojang.blaze3d.systems.RenderSystem;

import fi.dy.masa.malilib.util.Color4f;
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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class BlockEspRenderer
{
    private static final float RAINBOW_SATURATION = 0.9F;
    private static final float RAINBOW_BRIGHTNESS = 1.0F;
    private static final float BOX_EXPAND = 0.002F;
    private static final float OUTLINE_WIDTH = 1.0F;

    private BlockEspRenderer()
    {
    }

    public static void refreshConfig()
    {
        Configs.Generic.BLOCK_ESP_HEX_COLOR.setValueFromString(Configs.normalizeBlockEspHexColor(Configs.Generic.BLOCK_ESP_HEX_COLOR.getStringValue()));
    }

    public static void render(MinecraftClient client, MatrixStack matrices, Matrix4f projectionMatrix)
    {
        if (matrices == null || Configs.isBlockEspOutlineOnly())
        {
            return;
        }

        BlockPos targetPos = getTargetBlock(client);
        if (targetPos == null)
        {
            return;
        }

        Color4f baseColor = getCurrentColor(client);
        Color4f fillColor = Color4f.fromColor(baseColor, Configs.getBlockEspOpacity());
        Color4f outlineColor = Color4f.fromColor(baseColor, Math.min(1.0F, Configs.getBlockEspOpacity() + 0.25F));
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        double minX = targetPos.getX() - cameraPos.x - BOX_EXPAND;
        double minY = targetPos.getY() - cameraPos.y - BOX_EXPAND;
        double minZ = targetPos.getZ() - cameraPos.z - BOX_EXPAND;
        double maxX = targetPos.getX() + 1.0D - cameraPos.x + BOX_EXPAND;
        double maxY = targetPos.getY() + 1.0D - cameraPos.y + BOX_EXPAND;
        double maxZ = targetPos.getZ() + 1.0D - cameraPos.z + BOX_EXPAND;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        WorldRenderer.renderFilledBox(matrices, buffer, minX, minY, minZ, maxX, maxY, maxZ, fillColor.r, fillColor.g, fillColor.b, fillColor.a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        WorldRenderer.drawBox(matrices, buffer, minX, minY, minZ, maxX, maxY, maxZ, outlineColor.r, outlineColor.g, outlineColor.b, outlineColor.a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static Color4f getCurrentColor(MinecraftClient client)
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
        if (hex.length() == 9)
        {
            return Color4f.fromColor((int) parsed);
        }

        return Color4f.fromColor((int) (0xFF000000L | parsed));
    }

    public static boolean shouldReplaceVanillaOutline(MinecraftClient client)
    {
        return FeatureToggle.TWEAK_BLOCK_ESP.getBooleanValue()
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
        BlockState state = client.world.getBlockState(targetPos);
        return state.isAir() ? null : targetPos;
    }

    public static int getCurrentOutlineColor(MinecraftClient client)
    {
        Color4f color = Color4f.fromColor(getCurrentColor(client), Configs.getBlockEspOpacity());
        int alpha = Math.max(0, Math.min(255, Math.round(color.a * 255.0F)));
        int red = Math.max(0, Math.min(255, Math.round(color.r * 255.0F)));
        int green = Math.max(0, Math.min(255, Math.round(color.g * 255.0F)));
        int blue = Math.max(0, Math.min(255, Math.round(color.b * 255.0F)));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
