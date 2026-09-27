package com.mmm.feature;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mmm.config.Configs;
import com.mmm.mixin.ClientPlayerInteractionManagerAccessor;
import com.mmm.mixin.WorldRendererAccessor;
import com.mmm.render.Color4f;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.BlockBreakingInfo;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BreakingIndicatorRenderer
{
    private static final double MAX_RENDER_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final int MAX_INDICATORS = BreakIndicatorState.LIMIT;
    private static final double MIN_VISIBLE_SCALE = 0.12D;
    private static final BreakIndicatorState COMPLETED = new BreakIndicatorState();
    private static final Map<BlockPos, Float> PROGRESS = new LinkedHashMap<>();
    private static final Box[] INDICATOR_BOUNDS = new Box[MAX_INDICATORS * 2];
    private static final float[] INDICATOR_PROGRESS = new float[MAX_INDICATORS * 2];
    private static final float[] INDICATOR_OPACITY = new float[MAX_INDICATORS * 2];
    private static ClientWorld observedWorld;
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
        ClientTickEvents.END_CLIENT_TICK.register(BreakingIndicatorRenderer::reconcileWorld);
        WorldRenderEvents.LAST.register(context -> {
            MatrixStack matrices = context.matrixStack();
            if (matrices != null)
            {
                render(MinecraftClient.getInstance(), matrices, context.camera().getPos());
            }
        });
    }

    public static void completed(MinecraftClient client, BlockPos pos, Box bounds)
    {
        reconcileWorld(client);
        if (client.world != null && Configs.Generic.BREAKING_INDICATORS.getBooleanValue())
            COMPLETED.complete(pos, bounds, System.nanoTime());
    }

    private static void reconcileWorld(MinecraftClient client)
    {
        if (observedWorld != client.world || !Configs.Generic.BREAKING_INDICATORS.getBooleanValue())
        {
            COMPLETED.clear();
            PROGRESS.clear();
            java.util.Arrays.fill(INDICATOR_BOUNDS, null);
            observedWorld = client.world;
        }
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

        reconcileWorld(client);
        int indicatorCount = collectIndicators(client, camera);
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
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferBuilder fillBuffer = Tessellator.getInstance().getBuffer();
            fillBuffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (int index = 0; index < indicatorCount; index++)
            {
                renderFaces(matrices, fillBuffer, INDICATOR_BOUNDS[index],
                        colorForProgress(INDICATOR_PROGRESS[index], 0.25F * INDICATOR_OPACITY[index]));
            }
            BufferRenderer.drawWithGlobalProgram(fillBuffer.end());

            RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
            RenderSystem.lineWidth(2.0F);
            BufferBuilder lineBuffer = Tessellator.getInstance().getBuffer();
            lineBuffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            for (int index = 0; index < indicatorCount; index++)
            {
                renderOutline(matrices, lineBuffer, INDICATOR_BOUNDS[index],
                        colorForProgress(INDICATOR_PROGRESS[index], INDICATOR_OPACITY[index]));
            }
            BufferRenderer.drawWithGlobalProgram(lineBuffer.end());
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

    private static int collectIndicators(MinecraftClient client, Vec3d camera)
    {
        PROGRESS.clear();
        ClientPlayerInteractionManagerAccessor interaction =
                (ClientPlayerInteractionManagerAccessor) client.interactionManager;
        BlockPos ownPos = interaction.mmm$getCurrentBreakingPos();
        float ownProgress = net.minecraft.util.math.MathHelper.clamp(interaction.mmm$getCurrentBreakingProgress(), 0.0F, 1.0F);
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

        if (ownPos != null && ownProgress > 0.0F)
        {
            PROGRESS.put(ownPos.toImmutable(), ownProgress);
        }

        for (var entry : ((WorldRendererAccessor) client.worldRenderer).mmm$getBlockBreakingInfos().int2ObjectEntrySet())
        {
            if (PROGRESS.size() >= MAX_INDICATORS)
            {
                break;
            }

            BlockBreakingInfo info = entry.getValue();
            BlockPos pos = info.getPos();
            int stage = info.getStage();
            if (entry.getIntKey() == client.player.getId() || pos == null || stage < 0 || stage > 9)
            {
                continue;
            }

            PROGRESS.merge(pos.toImmutable(), (stage + 1) / 10.0F, Math::max);
        }
        int indicatorCount = 0;
        long now = System.nanoTime();
        var completed = COMPLETED.active(now);
        for (var entry : PROGRESS.entrySet())
        {
            BlockPos pos = entry.getKey();
            if (completed.containsKey(pos) || camera.squaredDistanceTo(Vec3d.ofCenter(pos)) > MAX_RENDER_DISTANCE_SQUARED)
                continue;
            BlockState state = client.world.getBlockState(pos);
            VoxelShape shape = state.getOutlineShape(client.world, pos);
            if (!state.isAir() && !shape.isEmpty())
                addIndicator(indicatorCount++, shape.getBoundingBox().offset(pos), entry.getValue(), 1, camera);
        }
        for (var entry : completed.entrySet())
            if (camera.squaredDistanceTo(Vec3d.ofCenter(entry.getKey())) <= MAX_RENDER_DISTANCE_SQUARED)
                addIndicator(indicatorCount++, entry.getValue().bounds(), 1, entry.getValue().opacity(now), camera);
        PROGRESS.clear();
        return indicatorCount;
    }

    private static void addIndicator(int index, Box box, float progress, float opacity, Vec3d camera)
    {
        INDICATOR_BOUNDS[index] = BreakIndicatorState.scaledBounds(box, progress).offset(-camera.x, -camera.y, -camera.z);
        INDICATOR_PROGRESS[index] = progress;
        INDICATOR_OPACITY[index] = opacity;
    }

    private static void renderFaces(MatrixStack matrices,
                                    BufferBuilder buffer,
                                    Box box,
                                    Color4f color)
    {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        // All six faces are translucent, matching the legacy filled box (including back faces).
        quad(buffer, matrix, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ,
                box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
        quad(buffer, matrix, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ,
                box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
        quad(buffer, matrix, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ,
                box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, color);
        quad(buffer, matrix, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ,
                box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, color);
        quad(buffer, matrix, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ,
                box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        quad(buffer, matrix, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
                box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
    }

    private static void quad(BufferBuilder buffer,
                             Matrix4f matrix,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             double x4, double y4, double z4,
                             Color4f color)
    {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(color.r, color.g, color.b, color.a).next();
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(color.r, color.g, color.b, color.a).next();
        buffer.vertex(matrix, (float) x3, (float) y3, (float) z3).color(color.r, color.g, color.b, color.a).next();
        buffer.vertex(matrix, (float) x4, (float) y4, (float) z4).color(color.r, color.g, color.b, color.a).next();
    }

    private static void renderOutline(MatrixStack matrices,
                                      BufferBuilder buffer,
                                      Box bounds,
                                      Color4f outline)
    {
        WorldRenderer.drawBox(
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
        float amount = net.minecraft.util.math.MathHelper.clamp(progress, 0.0F, 1.0F);
        int alpha = interpolate((start >>> 24) & 0xFF, (end >>> 24) & 0xFF, amount);
        int red = interpolate((start >>> 16) & 0xFF, (end >>> 16) & 0xFF, amount);
        int green = interpolate((start >>> 8) & 0xFF, (end >>> 8) & 0xFF, amount);
        int blue = interpolate(start & 0xFF, end & 0xFF, amount);
        alpha = net.minecraft.util.math.MathHelper.clamp(Math.round(alpha * alphaMultiplier), 0, 255);
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
