package com.mmm.legacy;

import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

class LegacyHookContractTest {
    private ClassNode read(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(in, name);
            ClassNode node = new ClassNode(); new ClassReader(in).accept(node, 0); return node;
        }
    }
    private void hasMethod(String owner, String name, String desc) throws Exception {
        assertTrue(read(owner).methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc)), owner + "." + name + desc);
    }
    @Test void injectionTargetsExistInActualMinecraftBytecode() throws Exception {
        hasMethod("net/minecraft/client/Minecraft", "setWorld", "(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V");
        hasMethod("net/minecraft/client/Minecraft", "stop", "()V");
        hasMethod("net/minecraft/client/gui/GameGui", "render", "(F)V");
        hasMethod("net/minecraft/client/ClientPlayerInteractionManager", "finishMiningBlock", "(Lnet/minecraft/util/math/BlockPos;)Z");
        hasMethod("net/minecraft/client/network/handler/ClientPlayNetworkHandler", "handleStats", "(Lnet/minecraft/network/packet/s2c/play/StatsS2CPacket;)V");
    }
}
