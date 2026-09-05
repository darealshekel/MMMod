package com.mmm.feature;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mmm.config.Configs;
import com.mmm.mixin.ClientPlayerInteractionManagerAccessor;
import com.mmm.mixin.WorldRendererAccessor;
import com.mmm.render.Color4f;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

public final class BreakingIndicatorRenderer
{
    private static final double MAX_RENDER_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final int MAX_INDICATORS = 64;
    private static final double MIN_VISIBLE_SCALE = 0.12D;
    private static final double BOX_EXPAND = 0.003D;
    private static final BlockPos[] INDICATOR_POSITIONS = new BlockPos[MAX_INDICATORS];
    private static final float[] INDICATOR_PROGRESS = new float[MAX_INDICATORS];
    private static boolean initialized;

    private BreakingIndicatorRenderer()
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

    private static void render(MinecraftClient client, MatrixStack matrices, Vec3d camera)
    {
        if (!Configs.Generic.BREAKING_INDICATORS.getBooleanValue()
                || client == null
                || client.world == null
                || client.player == null
                || client.interactionManager == null)
        {
            return;
        }

        int indicatorCount = collectIndicators(client);
        if (indicatorCount == 0)
        {
            return;
        }

        matrices.push();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try
        {
            BufferBuilder fillBuffer = Tessellator.getInstance().begin(
                    VertexFormat.DrawMode.QUADS,
                    VertexFormats.POSITION_COLOR
            );
            for (int index = 0; index < indicatorCount; index++)
            {
                renderFill(client, matrices, fillBuffer, camera, INDICATOR_POSITIONS[index], INDICATOR_PROGRESS[index]);
            }
            RenderLayer.getDebugFilledBox().draw(fillBuffer.end());

            RenderSystem.lineWidth(2.0F);
            BufferBuilder lineBuffer = Tessellator.getInstance().begin(
                    VertexFormat.DrawMode.LINES,
                    VertexFormats.LINES
            );
            for (int index = 0; index < indicatorCount; index++)
            {
                renderOutline(client, matrices, lineBuffer, camera, INDICATOR_POSITIONS[index], INDICATOR_PROGRESS[index]);
            }
            RenderLayer.getLines().draw(lineBuffer.end());
        }
        finally
        {
            RenderSystem.lineWidth(1.0F);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            matrices.pop();
        }
    }

    private static int collectIndicators(MinecraftClient client)
    {
        int indicatorCount = 0;
        ClientPlayerInteractionManagerAccessor interaction =
                (ClientPlayerInteractionManagerAccessor) client.interactionManager;
        BlockPos ownPos = interaction.mmm$getCurrentBreakingPos();
        float ownProgress = Math.clamp(interaction.mmm$getCurrentBreakingProgress(), 0.0F, 1.0F);
        boolean breakingBlock = interaction.mmm$isBreakingBlock();

        if (!breakingBlock)
        {
            ownPos = null;
            ownProgress = 0.0F;
        }
        else if ((ownPos == null || ownProgress <= 0.0F)
                && client.options.attackKey.isPressed()
                && client.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK)
        {
            ownPos = hit.getBlockPos();
            ownProgress = (float) MIN_VISIBLE_SCALE;
        }

        if (isRenderable(client, ownPos) && ownProgress > 0.0F)
        {
            INDICATOR_POSITIONS[indicatorCount] = ownPos.toImmutable();
            INDICATOR_PROGRESS[indicatorCount++] = ownProgress;
        }

        for (BlockBreakingInfo info : ((WorldRendererAccessor) client.worldRenderer).mmm$getBlockBreakingInfos().values())
        {
            if (indicatorCount >= MAX_INDICATORS)
            {
                break;
            }

            BlockPos pos = info.getPos();
            int stage = info.getStage();
            if (pos == null || pos.equals(ownPos) || stage < 0 || stage > 9 || !isRenderable(client, pos))
            {
                continue;
            }

            if (client.player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= MAX_RENDER_DISTANCE_SQUARED)
            {
                INDICATOR_POSITIONS[indicatorCount] = pos.toImmutable();
                INDICATOR_PROGRESS[indicatorCount++] = (stage + 1) / 10.0F;
            }
        }
        return indicatorCount;
    }

    private static boolean isRenderable(MinecraftClient client, BlockPos pos)
    {
        if (pos == null || client.world == null)
        {
            return false;
        }
        BlockState state = client.world.getBlockState(pos);
        return !state.isAir() && !state.getOutlineShape(client.world, pos).isEmpty();
    }

    private static void renderFill(MinecraftClient client,
                                   MatrixStack matrices,
                                   BufferBuilder buffer,
                                   Vec3d camera,
                                   BlockPos pos,
                                   float progress)
    {
        Box bounds = getBounds(client, camera, pos, progress);
        if (bounds == null)
        {
            return;
        }
        Color4f fill = colorForProgress(progress, 0.24F);
        renderCameraFacingFaces(matrices, buffer, bounds, fill);
    }

    private static void renderCameraFacingFaces(MatrixStack matrices,
                                                BufferBuilder buffer,
                                                Box box,
                                                Color4f color)
    {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerY = (box.minY + box.maxY) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;

        if (centerX >= 0.0D)
        {
            quad(buffer, matrix, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ,
                    box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
        }
        else
        {
            quad(buffer, matrix, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
        }

        if (centerY >= 0.0D)
        {
            quad(buffer, matrix, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ,
                    box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, color);
        }
        else
        {
            quad(buffer, matrix, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ,
                    box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, color);
        }

        if (centerZ >= 0.0D)
        {
            quad(buffer, matrix, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ,
                    box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        }
        else
        {
            quad(buffer, matrix, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
                    box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
        }
    }

    private static void quad(BufferBuilder buffer,
                             Matrix4f matrix,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             double x4, double y4, double z4,
                             Color4f color)
    {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(color.r, color.g, color.b, color.a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(color.r, color.g, color.b, color.a);
        buffer.vertex(matrix, (float) x3, (float) y3, (float) z3).color(color.r, color.g, color.b, color.a);
        buffer.vertex(matrix, (float) x4, (float) y4, (float) z4).color(color.r, color.g, color.b, color.a);
    }

    private static void renderOutline(MinecraftClient client,
                                      MatrixStack matrices,
                                      BufferBuilder buffer,
                                      Vec3d camera,
                                      BlockPos pos,
                                   float progress)
    {
        Box bounds = getBounds(client, camera, pos, progress);
        if (bounds == null)
        {
            return;
        }
        Color4f outline = colorForProgress(progress, 0.95F);
        VertexRendering.drawBox(
                matrices,
                buffer,
                bounds.minX,
                bounds.minY,
                bounds.minZ,
                bounds.maxX,
                bounds.maxY,
                bounds.maxZ,
                outline.r,
                outline.g,
                outline.b,
                outline.a
        );
    }

    private static Box getBounds(MinecraftClient client, Vec3d camera, BlockPos pos, float progress)
    {
        BlockState state = client.world.getBlockState(pos);
        VoxelShape shape = state.getOutlineShape(client.world, pos);
        if (state.isAir() || shape.isEmpty())
        {
            return null;
        }
        return scaledBounds(shape.getBoundingBox(), pos, progress, camera.x, camera.y, camera.z);
    }

    private static Box scaledBounds(Box local,
                                    BlockPos pos,
                                    double progress,
                                    double cameraX,
                                    double cameraY,
                                    double cameraZ)
    {
        double scale = Math.max(MIN_VISIBLE_SCALE, Math.clamp(progress, 0.0D, 1.0D));
        double centerX = pos.getX() + (local.minX + local.maxX) * 0.5D - cameraX;
        double centerY = pos.getY() + (local.minY + local.maxY) * 0.5D - cameraY;
        double centerZ = pos.getZ() + (local.minZ + local.maxZ) * 0.5D - cameraZ;
        double halfX = local.getLengthX() * scale * 0.5D;
        double halfY = local.getLengthY() * scale * 0.5D;
        double halfZ = local.getLengthZ() * scale * 0.5D;
        return new Box(
                centerX - halfX - BOX_EXPAND,
                centerY - halfY - BOX_EXPAND,
                centerZ - halfZ - BOX_EXPAND,
                centerX + halfX + BOX_EXPAND,
                centerY + halfY + BOX_EXPAND,
                centerZ + halfZ + BOX_EXPAND
        );
    }

    private static Color4f colorForProgress(float progress, float alphaMultiplier)
    {
        int start = parseColor(
                Configs.Generic.BREAKING_INDICATOR_START_HEX_COLOR.getStringValue(),
                Configs.Generic.DEFAULT_BREAKING_INDICATOR_START_HEX_COLOR
        );
        int end = parseColor(
                Configs.Generic.BREAKING_INDICATOR_END_HEX_COLOR.getStringValue(),
                Configs.Generic.DEFAULT_BREAKING_INDICATOR_END_HEX_COLOR
        );
        float amount = Math.clamp(progress, 0.0F, 1.0F);
        int alpha = interpolate((start >>> 24) & 0xFF, (end >>> 24) & 0xFF, amount);
        int red = interpolate((start >>> 16) & 0xFF, (end >>> 16) & 0xFF, amount);
        int green = interpolate((start >>> 8) & 0xFF, (end >>> 8) & 0xFF, amount);
        int blue = interpolate(start & 0xFF, end & 0xFF, amount);
        alpha = Math.clamp(Math.round(alpha * alphaMultiplier), 0, 255);
        return Color4f.fromColor((alpha << 24) | (red << 16) | (green << 8) | blue);
    }

    private static int parseColor(String value, String fallback)
    {
        String normalized = Configs.normalizeHexColor(value, fallback);
        long parsed = Long.parseLong(normalized.substring(1), 16);
        return normalized.length() == 7
                ? (int) (0xFF000000L | parsed)
                : (int) parsed;
    }

    private static int interpolate(int start, int end, float amount)
    {
        return Math.round(start + (end - start) * amount);
    }

}
