package com.mmm.legacy.settings;

import com.mmm.hud.HudModuleId;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyUiContractTest {
    private static ClassNode read(String name) throws Exception {
        try (InputStream input = LegacyUiContractTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    @Test void scissorQueryMeetsLwjgl2MinimumBufferSize() throws Exception {
        var constructor = read("com/mmm/legacy/LegacyDrawContext$Clip").methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        MethodInsnNode allocation = Arrays.stream(constructor.instructions.toArray())
                .filter(i -> i instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/BufferUtils") && call.name.equals("createIntBuffer"))
                .map(i -> (MethodInsnNode) i).findFirst().orElseThrow();
        assertInstanceOf(IntInsnNode.class, allocation.getPrevious());
        assertEquals(16, ((IntInsnNode) allocation.getPrevious()).operand);
        assertTrue(Arrays.stream(constructor.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
                && call.owner.equals("net/minecraft/client/render/Window") && call.name.equals("getScale")));
    }
    @Test void savedEfficiencyLoadsOnlyAtConstructionAndNeverWaitsInRender() throws Exception {
        var screen = read("com/mmm/legacy/LegacySessionScreen");
        long calls = 0;
        for (MethodNode method : screen.methods) for (var instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.owner.equals("com/mmm/storage/EfficiencyStore") && call.name.equals("load")) {
                assertEquals("<init>", method.name); calls++;
            }
            if (method.name.equals("render")) assertFalse(call.owner.equals("java/util/concurrent/CompletableFuture")
                    && (call.name.equals("join") || call.name.equals("get")));
        }
        assertEquals(1, calls);
    }
    @Test void escapeKeyCaptureDoesNotWriteBindings() throws Exception {
        var keyMethod = read("com/mmm/legacy/settings/HotkeyEditorScreen").methods.stream()
                .filter(m -> m.name.equals("keyPressed")).findFirst().orElseThrow();
        assertFalse(Arrays.stream(keyMethod.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
                && call.name.equals("setStorageString")));
    }
    @Test void pinnedCollectionIsDrawnInsideMainHudAndMeasured() throws Exception {
        var renderer = read("com/mmm/hud/MiningHudRenderer");
        var draw = renderer.methods.stream().filter(m -> m.name.equals("draw")).findFirst().orElseThrow();
        assertTrue(Arrays.stream(draw.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
                && call.owner.equals("com/mmm/legacy/collection/PinnedCollectionHud") && call.name.equals("render")));
        var frame = renderer.methods.stream().filter(m -> m.name.equals("getHudFrame")).findFirst().orElseThrow();
        assertTrue(Arrays.stream(frame.instructions.toArray()).anyMatch(i -> i instanceof IntInsnNode value && value.operand == 196));
        assertTrue(Arrays.stream(frame.instructions.toArray()).anyMatch(i -> i instanceof IincInsnNode value && value.incr == 59
                || i instanceof IntInsnNode constant && constant.operand == 59));
    }
    @Test void hourlyStatsAreNotAnEditorModule() {
        assertEquals(List.of("MAIN", "TIMER", "BLOCK_STATS", "NOTIFICATION"),
                Arrays.stream(HudModuleId.values()).map(Enum::name).toList());
        var modules = HudModules.modules();
        assertTrue(modules.stream().map(HudModules.Module::id).toList().containsAll(
                List.of("mmm:MAIN", "mmm:TIMER", "mmm:BLOCK_STATS", "mmm:NOTIFICATION")));
        assertTrue(modules.stream().noneMatch(module -> module.id().equals("mmm:HOURLY")
                || module.label().equals("Hourly Stats")));
    }
    @Test void hourlyStatsRemainOnlyInMainHud() throws Exception {
        var timer = read("com/mmm/timer/TimerHudRenderer");
        assertTrue(timer.methods.stream().noneMatch(method -> method.name.equals("drawHourly")));
        for (var method : timer.methods) for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field)
                assertFalse(field.owner.equals("com/mmm/config/Configs$Generic") && field.name.startsWith("HOURLY_STATS_"),
                        method.name + " still uses " + field.name);
        }
        var main = read("com/mmm/hud/MiningHudRenderer").methods.stream()
                .filter(method -> method.name.equals("buildHudLines")).findFirst().orElseThrow();
        var instructions = Arrays.asList(main.instructions.toArray());
        assertTrue(instructions.stream().anyMatch(i -> i instanceof FieldInsnNode field
                && field.owner.equals("com/mmm/config/Configs$Generic") && field.name.equals("HOURLY_STATS_VISIBLE")));
        assertTrue(instructions.stream().anyMatch(i -> i instanceof LdcInsnNode constant && "Hour / Best Hour: ".equals(constant.cst)));
        for (String getter : List.of("getCurrentHourBlocks", "getBestHourBlocks"))
            assertTrue(instructions.stream().anyMatch(i -> i instanceof MethodInsnNode call
                    && call.owner.equals("com/mmm/timer/MmmTimerState") && call.name.equals(getter)), getter);
    }
}
