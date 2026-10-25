package com.mmm.legacy.visual;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks real Feather bytecode; startup still needs to verify LiteLoader's production remapping. */
class VisualMixinTargetsTest {
    private static ClassNode read(String name) throws IOException {
        try (var stream = VisualMixinTargetsTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            assertNotNull(stream, name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
    private static MethodNode method(String owner, String name, String descriptor) throws IOException {
        return read(owner).methods.stream().filter(m -> m.name.equals(name) && (descriptor == null || m.desc.equals(descriptor)))
                .findFirst().orElseThrow(() -> new AssertionError(owner + "." + name + descriptor));
    }
    private static long count(MethodNode method, Predicate<AbstractInsnNode> predicate) {
        return Arrays.stream(method.instructions.toArray()).filter(predicate).count();
    }
    private static boolean invoke(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor);
    }
    @Test void worldOverlayRunsBeforeHandsWithOriginalCameraMatrices() throws Exception {
        var render = method("net.minecraft.client.render.GameRenderer", "render", "(IFJ)V");
        assertEquals(1, count(render, n -> n instanceof FieldInsnNode f && f.name.equals("newCamPitch") && f.desc.equals("Z")));
        int overlay = -1, hand = -1;
        for (int i = 0; i < render.instructions.size(); i++) {
            var node = render.instructions.get(i);
            if (node instanceof FieldInsnNode f && f.name.equals("newCamPitch")) overlay = i;
            if (node instanceof MethodInsnNode m && m.name.equals("renderItemInHand")) hand = i;
        }
        assertTrue(overlay >= 0 && hand > overlay);
    }
    @Test void ordinaryAndBlockEntityCracksCanBothBeSuppressed() throws Exception {
        String world = "net.minecraft.client.render.world.WorldRenderer";
        assertNotNull(method(world, "renderMiningProgress", null));
        var entities = method(world, "renderEntities", null);
        assertTrue(count(entities, n -> n instanceof FieldInsnNode f && f.name.equals("miningProgress") && f.desc.equals("Ljava/util/Map;")) > 0);
        assertTrue(read(world).fields.stream().anyMatch(f -> f.name.equals("miningProgress") && f.desc.equals("Ljava/util/Map;")));
    }
    @Test void instantBreakHookUsesConfirmedBooleanReturn() throws Exception {
        assertNotNull(method("net.minecraft.client.ClientPlayerInteractionManager", "finishMiningBlock", "(Lnet/minecraft/util/math/BlockPos;)Z"));
    }
    @Test void miningGuardsCoverStartAndContinuationAndKeepTheExistingSneakBypass() throws Exception {
        String manager = "net.minecraft.client.ClientPlayerInteractionManager";
        String descriptor = "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z";
        assertNotNull(method(manager, "startMiningBlock", descriptor));
        assertNotNull(method(manager, "tickBlockMining", descriptor));
        assertNotNull(method(manager, "stopMiningBlock", "()V"));
        for (String handler : List.of("mmm$guardMiningStart", "mmm$guardMiningProgress")) {
            var guard = method("com.mmm.legacy.visual.mixin.MiningGuardMixin", handler, null);
            assertEquals(1, count(guard, n -> invoke(n, "com/mmm/feature/FlatDigger", "shouldBlock", "(Lnet/minecraft/util/math/BlockPos;)Z")));
            assertEquals(1, count(guard, n -> invoke(n, "com/mmm/feature/PerimeterWallDigHelper", "isPositionDisallowed", "(Lnet/minecraft/util/math/BlockPos;)Z")));
            assertEquals(1, count(guard, n -> n instanceof MethodInsnNode call && call.name.equals("stopMiningBlock")));
        }
        for (String feature : List.of("FlatDigger", "PerimeterWallDigHelper")) {
            String name = feature.equals("FlatDigger") ? "shouldBlock" : "isPositionDisallowed";
            assertEquals(1, count(method("com.mmm.feature." + feature, name, null),
                    n -> n instanceof MethodInsnNode call && call.name.equals("isSneaking") && call.desc.equals("()Z")));
        }
    }
    @Test void lavaAlphaUsesTheActualPerVertexFloatColorAndLayerMethod() throws Exception {
        assertNotNull(method("net.minecraft.block.LiquidBlock", "getRenderLayer", "()Lnet/minecraft/client/render/block/BlockLayer;"));
        var render = method("net.minecraft.client.render.block.LiquidRenderer", "render",
                "(Lnet/minecraft/world/WorldView;Lnet/minecraft/block/state/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/vertex/BufferBuilder;)Z");
        assertTrue(count(render, n -> invoke(n, "net/minecraft/client/render/vertex/BufferBuilder", "color",
                "(FFFF)Lnet/minecraft/client/render/vertex/BufferBuilder;")) >= 16);
    }
    @Test void noSwingRedirectCoversBothHandsWithoutUnsupportedVariableArgumentCapture() throws Exception {
        var render = method("net.minecraft.client.render.ItemInHandRenderer", "renderInFirstPerson", "(F)V");
        String desc = "(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/item/ItemStack;F)V";
        assertEquals(2, count(render, n -> invoke(n, "net/minecraft/client/render/ItemInHandRenderer", "renderHandInFirstPerson", desc)));
        var mixin = read("com.mmm.legacy.visual.mixin.NoSwingMixin");
        for (var m : mixin.methods) for (var a : m.visibleAnnotations == null ? List.<AnnotationNode>of() : m.visibleAnnotations)
            assertFalse(a.desc.endsWith("/ModifyVariable;"), "LiteLoader 0.7 cannot capture target arguments in ModifyVariable");
    }
    @Test void itemScalingWrapsVanillaModelAndGlintTogether() throws Exception {
        var render = method("net.minecraft.client.render.entity.ItemRenderer", "renderItemInHand",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;Lnet/minecraft/client/resource/model/ModelTransformations$Type;Z)V");
        assertEquals(1, count(render, n -> invoke(n, "net/minecraft/client/render/entity/ItemRenderer", "renderItem",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;)V")));
    }
    @Test void transparencyTargetsAllFourVanillaTabBackgroundsAndSidebarUsesRealObjective() throws Exception {
        var render = method("net.minecraft.client.gui.overlay.PlayerTabOverlay", "render",
                "(ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V");
        assertEquals(4, count(render, n -> invoke(n, "net/minecraft/client/gui/overlay/PlayerTabOverlay", "fill", "(IIIII)V")));
        assertNotNull(method("net.minecraft.client.gui.GameGui", "renderScoreboardObjective",
                "(Lnet/minecraft/scoreboard/ScoreboardObjective;Lnet/minecraft/client/render/Window;)V"));
    }
}
