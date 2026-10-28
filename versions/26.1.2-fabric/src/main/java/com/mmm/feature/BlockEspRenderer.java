package com.mmm.feature;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.render.Color4f;
import com.mmm.render.ShapeOutlineSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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
        LevelRenderEvents.END_MAIN.register(context -> {
            PoseStack matrices = context.poseStack();
            if (matrices != null && context.levelState().cameraRenderState != null)
            {
                render(Minecraft.getInstance(), matrices,
                        context.levelState().cameraRenderState.pos, context.submitNodeCollector());
            }
        });
    }

    public static void refreshConfig()
    {
        Configs.Generic.BLOCK_ESP_HEX_COLOR.setValueFromString(Configs.normalizeBlockEspHexColor(Configs.Generic.BLOCK_ESP_HEX_COLOR.getStringValue()));
    }

    private static void render(Minecraft client,
                               PoseStack matrices,
                               Vec3 camera,
                               SubmitNodeCollector consumers)
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
        BlockState state = client.level.getBlockState(targetPos);
        VoxelShape shape = state.getShape(client.level, targetPos);
        if (shape.isEmpty())
        {
            return;
        }

        AABB local = shape.bounds();
        double minX = targetPos.getX() + local.minX - camera.x - BOX_EXPAND;
        double minY = targetPos.getY() + local.minY - camera.y - BOX_EXPAND;
        double minZ = targetPos.getZ() + local.minZ - camera.z - BOX_EXPAND;
        double maxX = targetPos.getX() + local.maxX - camera.x + BOX_EXPAND;
        double maxY = targetPos.getY() + local.maxY - camera.y + BOX_EXPAND;
        double maxZ = targetPos.getZ() + local.maxZ - camera.z + BOX_EXPAND;
        Color4f baseColor = getCurrentColor();
        Color4f fill = Color4f.fromColor(baseColor, Configs.getBlockEspOpacity());
        Color4f outline = Color4f.fromColor(baseColor, Math.min(1.0F, Configs.getBlockEspOpacity() + 0.25F));

        consumers.submitCustomGeometry(matrices, RenderTypes.debugQuads(),
                (pose, fillConsumer) -> drawFilledBox(pose, fillConsumer,
                        minX, minY, minZ, maxX, maxY, maxZ, fill));
        ShapeOutlineSubmitter.submit(consumers, matrices,
                Shapes.create(new AABB(minX, minY, minZ, maxX, maxY, maxZ)),
                toArgb(outline));
    }

    private static void drawFilledBox(PoseStack.Pose pose,
                                      VertexConsumer consumer,
                                      double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ,
                                      Color4f color)
    {
        org.joml.Matrix4f matrix = pose.pose();
        quad(consumer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, color);
        quad(consumer, matrix, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, color);
        quad(consumer, matrix, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, color);
        quad(consumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color);
        quad(consumer, matrix, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, color);
        quad(consumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, color);
    }

    private static void quad(VertexConsumer consumer,
                             org.joml.Matrix4f matrix,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             double x4, double y4, double z4,
                             Color4f color)
    {
        consumer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(color.r, color.g, color.b, color.a);
        consumer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(color.r, color.g, color.b, color.a);
        consumer.addVertex(matrix, (float) x3, (float) y3, (float) z3).setColor(color.r, color.g, color.b, color.a);
        consumer.addVertex(matrix, (float) x4, (float) y4, (float) z4).setColor(color.r, color.g, color.b, color.a);
    }

    private static int toArgb(Color4f color)
    {
        int alpha = Math.clamp(Math.round(color.a * 255.0F), 0, 255);
        int red = Math.clamp(Math.round(color.r * 255.0F), 0, 255);
        int green = Math.clamp(Math.round(color.g * 255.0F), 0, 255);
        int blue = Math.clamp(Math.round(color.b * 255.0F), 0, 255);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
    private static Color4f getCurrentColor()    {
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

    public static boolean shouldReplaceVanillaOutline(Minecraft client)
    {
        return FeatureToggle.MMM_BLOCK_ESP.getBooleanValue()
                && Configs.getBlockEspOpacity() > 0.0F
                && getTargetBlock(client) != null;
    }

    private static BlockPos getTargetBlock(Minecraft client)
    {
        if (client == null || client.player == null || client.level == null || Configs.getBlockEspOpacity() <= 0.0F)
        {
            return null;
        }
        HitResult hitResult = client.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK)
        {
            return null;
        }
        BlockPos targetPos = blockHitResult.getBlockPos();
        return client.level.getBlockState(targetPos).isAir() ? null : targetPos;
    }

    public static int getCurrentOutlineColor(Minecraft client)
    {
        Color4f color = Color4f.fromColor(getCurrentColor(), Configs.getBlockEspOpacity());
        int alpha = Math.clamp(Math.round(color.a * 255.0F), 0, 255);
        int red = Math.clamp(Math.round(color.r * 255.0F), 0, 255);
        int green = Math.clamp(Math.round(color.g * 255.0F), 0, 255);
        int blue = Math.clamp(Math.round(color.b * 255.0F), 0, 255);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
