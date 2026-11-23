package com.mmm.liteloader;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

class HudRenderHookTest {
    @Test void rendersBothHudModulesOnceThroughTheLoaderCallback() throws IOException {
        ClassNode mod = read("com/mmm/liteloader/LiteModMMM");
        assertTrue(mod.interfaces.contains("com/mumfrey/liteloader/HUDRenderListener"));
        MethodNode callback = mod.methods.stream().filter(method -> method.name.equals("onPostRenderHUD")
                && method.desc.equals("(II)V")).findFirst().orElseThrow();
        assertEquals(1, calls(callback, "com/mmm/hud/MiningHudRenderer"));
        assertEquals(1, calls(callback, "com/mmm/timer/TimerHudRenderer"));
        for (MethodNode method : mod.methods) {
            if (method != callback) {
                assertEquals(0, calls(method, "com/mmm/hud/MiningHudRenderer"));
                assertEquals(0, calls(method, "com/mmm/timer/TimerHudRenderer"));
            }
        }
    }

    @Test void vanillaMixinCannotDuplicateTheHud() throws IOException {
        for (MethodNode method : read("com/mmm/legacy/mixin/LegacyGameGuiMixin").methods) {
            assertEquals(0, calls(method, "com/mmm/hud/MiningHudRenderer"));
            assertEquals(0, calls(method, "com/mmm/timer/TimerHudRenderer"));
        }
    }

    private static long calls(MethodNode method, String owner) {
        long count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals("render")) count++;
        }
        return count;
    }

    private static ClassNode read(String name) throws IOException {
        try (var stream = HudRenderHookTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
