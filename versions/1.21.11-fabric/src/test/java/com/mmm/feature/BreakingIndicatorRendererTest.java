package com.mmm.feature;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mmm.render.Color4f;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BreakingIndicatorRendererTest
{
    // Also runnable against cached jars without launching a concurrent Gradle build.
    public static void main(String[] args) throws Exception
    {
        var test = new BreakingIndicatorRendererTest();
        test.fillEmitsAllSixFacesIncludingBackFaces();
        test.outlineEmitsTwelveTwoPixelEdgesWithTheRequestedAlpha();
        test.storedCompletionBoundsUseSharedScalingAndCameraOffset();
        test.nativePipelinesKeepBothPassesTranslucentAndVisibleThroughBlocks();
        test.versionAdapterRetainsLatestCollectionAndLifecycleContract();
        System.out.println("PASS: 5 breaking indicator adapter checks");
    }

    @Test
    void fillEmitsAllSixFacesIncludingBackFaces()
    {
        var consumer = new RecordingConsumer();
        var matrices = new MatrixStack();
        matrices.translate(2, 3, 4);
        var box = new Box(-5, -2, 1, -3, -1, 4);
        BreakingIndicatorRenderer.renderFaces(matrices, consumer, box, Color4f.fromColor(0x407F3F1F));

        assertEquals(24, consumer.vertices.size());
        assertEquals(24, consumer.colors.size());
        assertTrue(consumer.colors.stream().allMatch(color -> color == 0x407F3F1F));
        for (int face = 0; face < 6; face++)
        {
            int axis = face / 2;
            double expected = new double[] {-3, -1, 1, 2, 5, 8}[face];
            for (int vertex = 0; vertex < 4; vertex++)
                assertEquals(expected, consumer.vertices.get(face * 4 + vertex)[axis], 0.00001);
        }
        assertTrue(consumer.widths.isEmpty());
    }

    @Test
    void outlineEmitsTwelveTwoPixelEdgesWithTheRequestedAlpha() throws Exception
    {
        var consumer = new RecordingConsumer();
        var method = BreakingIndicatorRenderer.class.getDeclaredMethod("renderOutline",
                MatrixStack.class, VertexConsumer.class, Box.class, Color4f.class);
        method.setAccessible(true);
        method.invoke(null, new MatrixStack(), consumer, new Box(-2, 4, 8, -1, 4.5, 9),
                Color4f.fromColor(0x807F3F1F));

        assertEquals(24, consumer.vertices.size());
        assertEquals(24, consumer.colors.size());
        assertTrue(consumer.colors.stream().allMatch(color -> color == 0x807F3F1F));
        assertEquals(24, consumer.widths.size());
        assertTrue(consumer.widths.stream().allMatch(width -> width == 2.0F));
        assertEquals(24, consumer.normals.size());
        assertTrue(consumer.normals.stream().allMatch(normal -> Math.abs(normal.lengthSquared() - 1) < 0.00001));
    }

    @Test
    void storedCompletionBoundsUseSharedScalingAndCameraOffset() throws Exception
    {
        var method = BreakingIndicatorRenderer.class.getDeclaredMethod("addIndicator",
                int.class, Box.class, float.class, float.class, Vec3d.class);
        method.setAccessible(true);
        Box[] bounds = (Box[]) field(BreakingIndicatorRenderer.class, "INDICATOR_BOUNDS").get(null);
        float[] progress = (float[]) field(BreakingIndicatorRenderer.class, "INDICATOR_PROGRESS").get(null);
        float[] opacity = (float[]) field(BreakingIndicatorRenderer.class, "INDICATOR_OPACITY").get(null);
        assertEquals(BreakIndicatorState.LIMIT * 2, bounds.length);
        assertEquals(bounds.length, progress.length);
        assertEquals(bounds.length, opacity.length);
        int index = bounds.length - 1;
        Box previousBounds = bounds[index];
        float previousProgress = progress[index], previousOpacity = opacity[index];
        try
        {
            var slab = new Box(10, 64, 30, 11, 64.5, 31);
            var camera = new Vec3d(8, 62, 29);
            method.invoke(null, index, slab, 1.0F, 0.5F, camera);
            assertEquals(BreakIndicatorState.scaledBounds(slab, 1).offset(-8, -62, -29), bounds[index]);
            assertEquals(0.506, bounds[index].getLengthY(), 0.00001);
            assertEquals(1.0F, progress[index]);
            assertEquals(0.5F, opacity[index]);
        }
        finally
        {
            bounds[index] = previousBounds;
            progress[index] = previousProgress;
            opacity[index] = previousOpacity;
        }
    }

    @Test
    void nativePipelinesKeepBothPassesTranslucentAndVisibleThroughBlocks() throws Exception
    {
        Class<?> layers = Class.forName(BreakingIndicatorRenderer.class.getName() + "$Layers");
        for (String name : List.of("FILL", "OUTLINE"))
        {
            RenderLayer layer = (RenderLayer) field(layers, name).get(null);
            var pipeline = layer.getRenderPipeline();
            assertEquals(DepthTestFunction.NO_DEPTH_TEST, pipeline.getDepthTestFunction());
            assertFalse(pipeline.isWriteDepth());
            assertFalse(pipeline.isCull());
            assertEquals(BlendFunction.TRANSLUCENT, pipeline.getBlendFunction().orElseThrow());
            assertEquals(name.equals("FILL") ? VertexFormat.DrawMode.QUADS : VertexFormat.DrawMode.LINES,
                    layer.getDrawMode());
            assertEquals(name.equals("FILL") ? VertexFormats.POSITION_COLOR : VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH,
                    layer.getVertexFormat());
        }
    }

    @Test
    void versionAdapterRetainsLatestCollectionAndLifecycleContract() throws Exception
    {
        String source = Files.readString(Path.of(System.getProperty("mmm.sources", "src/main/java"))
                .resolve("com/mmm/feature/BreakingIndicatorRenderer.java"));
        assertTrue(source.contains("ClientTickEvents.END_CLIENT_TICK.register(BreakingIndicatorRenderer::reconcileWorld)"));
        assertTrue(source.contains("observedWorld != client.world || !Configs.Generic.BREAKING_INDICATORS.getBooleanValue()"));
        assertTrue(source.contains("COMPLETED.clear()"));
        assertTrue(source.contains("Arrays.fill(INDICATOR_BOUNDS, null)"));
        assertTrue(source.contains("COMPLETED.complete(pos, bounds, System.nanoTime())"));
        assertTrue(source.contains("entry.getIntKey() == client.player.getId()"));
        assertTrue(source.contains("PROGRESS.merge(pos.toImmutable(), (stage + 1) / 10.0F, Math::max)"));
        assertTrue(source.contains("PROGRESS.size() >= MAX_INDICATORS"));
        assertTrue(source.contains("completed.containsKey(pos)"));
        assertTrue(source.contains("camera.squaredDistanceTo(Vec3d.ofCenter(pos))"));
        assertTrue(source.contains("entry.getValue().bounds(), 1, entry.getValue().opacity(now)"));
        assertTrue(source.contains("0.25F * INDICATOR_OPACITY[index]"));
        assertTrue(source.contains("WorldRenderEvents.END_MAIN.register"));
        assertFalse(source.contains("client.player.squaredDistanceTo"));
        assertFalse(source.contains("RenderSystem."));
    }

    private static Field field(Class<?> type, String name) throws Exception
    {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class RecordingConsumer implements VertexConsumer
    {
        final List<double[]> vertices = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();
        final List<Float> widths = new ArrayList<>();
        final List<Vec3d> normals = new ArrayList<>();

        public VertexConsumer vertex(float x, float y, float z) { vertices.add(new double[] {x, y, z}); return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return color((a << 24) | (r << 16) | (g << 8) | b); }
        public VertexConsumer color(int argb) { colors.add(argb); return this; }
        public VertexConsumer normal(float x, float y, float z) { normals.add(new Vec3d(x, y, z)); return this; }
        public VertexConsumer lineWidth(float width) { widths.add(width); return this; }
        public VertexConsumer texture(float u, float v) { throw new AssertionError("Unexpected texture"); }
        public VertexConsumer overlay(int u, int v) { throw new AssertionError("Unexpected overlay"); }
        public VertexConsumer light(int u, int v) { throw new AssertionError("Unexpected light"); }
    }
}
