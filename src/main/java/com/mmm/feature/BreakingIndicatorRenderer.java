package com.mmm.feature;

import com.mmm.config.Configs;
import com.mmm.mixin.ClientPlayerInteractionManagerAccessor;
import com.mmm.render.Color4f;
import com.mmm.render.ShapeOutlineSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
        LevelRenderEvents.END_MAIN.register(context -> {
            PoseStack matrices = context.poseStack();
            if (matrices != null && context.levelState().cameraRenderState != null)
            {
                render(Minecraft.getInstance(), matrices, context.levelState(),
                        context.levelState().cameraRenderState.pos, context.submitNodeCollector());
            }
        });
    }

    private static void render(Minecraft client,
                               PoseStack matrices,
                               LevelRenderState levelRenderState,
                               Vec3 camera,
                               SubmitNodeCollector consumers)
    {
        if (!Configs.Generic.BREAKING_INDICATORS.getBooleanValue()
                || client == null
                || client.level == null
                || client.player == null
                || client.gameMode == null)
        {
            return;
        }

        int indicatorCount = collectIndicators(client, levelRenderState);
        if (indicatorCount == 0)
        {
            return;
        }

        for (int index = 0; index < indicatorCount; index++)
        {
            renderFill(client, matrices, consumers, camera, INDICATOR_POSITIONS[index], INDICATOR_PROGRESS[index]);
        }

        for (int index = 0; index < indicatorCount; index++)
        {
            renderOutline(client, matrices, consumers, camera, INDICATOR_POSITIONS[index], INDICATOR_PROGRESS[index]);
        }
    }

    private static int collectIndicators(Minecraft client, LevelRenderState levelRenderState)
    {
        int indicatorCount = 0;
        ClientPlayerInteractionManagerAccessor interaction =
                (ClientPlayerInteractionManagerAccessor) client.gameMode;
        BlockPos ownPos = interaction.mmm$getCurrentBreakingPos();
        float ownProgress = Math.clamp(interaction.mmm$getCurrentBreakingProgress(), 0.0F, 1.0F);
        boolean breakingBlock = interaction.mmm$isBreakingBlock();

        if (!breakingBlock)
        {
            ownPos = null;
            ownProgress = 0.0F;
        }
        else if ((ownPos == null || ownProgress <= 0.0F)
                && client.options.keyAttack.isDown()
                && client.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK)
        {
            ownPos = hit.getBlockPos();
            ownProgress = (float) MIN_VISIBLE_SCALE;
        }

        if (isRenderable(client, ownPos) && ownProgress > 0.0F)
        {
            INDICATOR_POSITIONS[indicatorCount] = ownPos;
            INDICATOR_PROGRESS[indicatorCount++] = ownProgress;
        }

        for (BlockBreakingRenderState info : levelRenderState.blockBreakingRenderStates)
        {
            if (indicatorCount >= MAX_INDICATORS)
            {
                break;
            }

            BlockPos pos = info.blockPos();
            int stage = info.progress();
            if (pos == null || pos.equals(ownPos) || stage < 0 || stage > 9 || !isRenderable(client, pos))
            {
                continue;
            }

            if (client.player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_RENDER_DISTANCE_SQUARED)
            {
                INDICATOR_POSITIONS[indicatorCount] = pos;
                INDICATOR_PROGRESS[indicatorCount++] = (stage + 1) / 10.0F;
            }
        }
        return indicatorCount;
    }

    private static boolean isRenderable(Minecraft client, BlockPos pos)
    {
        if (pos == null || client.level == null)
        {
            return false;
        }
        BlockState state = client.level.getBlockState(pos);
        return !state.isAir() && !state.getShape(client.level, pos).isEmpty();
    }

    private static void renderFill(Minecraft client,
                                   PoseStack matrices,
                                   SubmitNodeCollector consumers,
                                   Vec3 camera,
                                   BlockPos pos,
                                   float progress)
    {
        AABB bounds = getBounds(client, camera, pos, progress);
        if (bounds == null)
        {
            return;
        }
        Color4f fill = colorForProgress(progress, 0.24F);
        consumers.submitCustomGeometry(matrices, RenderTypes.debugQuads(),
                (pose, buffer) -> renderCameraFacingFaces(pose, buffer, bounds, fill));
    }

    private static void renderCameraFacingFaces(PoseStack.Pose pose,
                                                VertexConsumer buffer,
                                                AABB box,
                                                Color4f color)
    {
        Matrix4f matrix = pose.pose();
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

    private static void quad(VertexConsumer buffer,
                             Matrix4f matrix,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             double x4, double y4, double z4,
                             Color4f color)
    {
        buffer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, (float) x3, (float) y3, (float) z3).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, (float) x4, (float) y4, (float) z4).setColor(color.r, color.g, color.b, color.a);
    }

    private static void renderOutline(Minecraft client,
                                      PoseStack matrices,
                                      SubmitNodeCollector consumers,
                                      Vec3 camera,
                                      BlockPos pos,
                                      float progress)
    {
        AABB bounds = getBounds(client, camera, pos, progress);
        if (bounds == null)
        {
            return;
        }
        Color4f outline = colorForProgress(progress, 0.95F);
        ShapeOutlineSubmitter.submit(consumers, matrices, Shapes.create(bounds), toArgb(outline));
    }

    private static AABB getBounds(Minecraft client, Vec3 camera, BlockPos pos, float progress)
    {
        BlockState state = client.level.getBlockState(pos);
        VoxelShape shape = state.getShape(client.level, pos);
        if (state.isAir() || shape.isEmpty())
        {
            return null;
        }
        return scaledBounds(shape.bounds(), pos, progress, camera.x, camera.y, camera.z);
    }

    private static AABB scaledBounds(AABB local,
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
        double halfX = local.getXsize() * scale * 0.5D;
        double halfY = local.getYsize() * scale * 0.5D;
        double halfZ = local.getZsize() * scale * 0.5D;
        return new AABB(
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

    private static int toArgb(Color4f color)
    {
        int alpha = Math.clamp(Math.round(color.a * 255.0F), 0, 255);
        int red = Math.clamp(Math.round(color.r * 255.0F), 0, 255);
        int green = Math.clamp(Math.round(color.g * 255.0F), 0, 255);
        int blue = Math.clamp(Math.round(color.b * 255.0F), 0, 255);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
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
