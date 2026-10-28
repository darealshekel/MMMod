package com.mmm.legacy.achievement;

import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class AchievementHookContractTest {
    private ClassNode read(String owner) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/" + owner + ".class")) {
            assertNotNull(input, owner);
            ClassNode result = new ClassNode();
            new ClassReader(input).accept(result, 0);
            return result;
        }
    }

    private void method(String owner, String name, String descriptor) throws Exception {
        assertTrue(read(owner).methods.stream().anyMatch(value -> value.name.equals(name) && value.desc.equals(descriptor)),
                owner + "." + name + descriptor);
    }

    @Test void screenAndProgressHooksMatchFeather1122() throws Exception {
        String screen = "net/minecraft/client/gui/screen/menu/advancements/AdvancementsScreen";
        method(screen, "init", "()V");
        method(screen, "render", "(IIF)V");
        method(screen, "onUpdateAdvancementProgress", "(Lnet/minecraft/advancement/Advancement;Lnet/minecraft/advancement/AdvancementProgress;)V");
        assertTrue(read(screen).fields.stream().anyMatch(field -> field.name.equals("currentTab")
                && field.desc.equals("Lnet/minecraft/client/gui/screen/menu/advancements/AdvancementsTab;")));
        ClassNode widget = read("net/minecraft/client/gui/screen/menu/advancements/AdvancementWidget");
        assertTrue(widget.fields.stream().anyMatch(field -> field.name.equals("description") && field.desc.equals("Ljava/util/List;")));
        assertTrue(widget.fields.stream().anyMatch(field -> field.name.equals("width") && field.desc.equals("I")));
        assertTrue(widget.fields.stream().anyMatch(field -> field.name.equals("minecraft") && field.desc.equals("Lnet/minecraft/client/Minecraft;")));
    }

    @Test void hudAndItemAnimationHooksMatchFeather1122() throws Exception {
        method("net/minecraft/client/gui/GameGui", "renderXpBar", "(Lnet/minecraft/client/render/Window;I)V");
        method("net/minecraft/client/render/GameRenderer", "displayItemOverlay", "(Lnet/minecraft/item/ItemStack;)V");
    }

    @Test void viewportRedirectTargetsOnlyTreeRenderingAndTabVisibilityChecks() throws Exception {
        String screen = "net/minecraft/client/gui/screen/menu/advancements/AdvancementsScreen";
        method(screen, "renderTabContents", "(IIII)V");
        method(screen, "renderTabs", "(II)V");
        method(screen, "renderTooltips", "(IIII)V");
        ClassNode node = read(screen);
        var contents = node.methods.stream().filter(value -> value.name.equals("renderTabContents")).findFirst().orElseThrow();
        int treeCalls = 0;
        for (var instruction : contents.instructions)
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/client/gui/screen/menu/advancements/AdvancementsTab")
                    && call.name.equals("renderContents") && call.desc.equals("()V")) treeCalls++;
        assertEquals(1, treeCalls);
        for (String name : new String[]{"renderTabs", "renderTooltips"}) {
            var render = node.methods.stream().filter(value -> value.name.equals(name)).findFirst().orElseThrow();
            int countChecks = 0;
            for (var instruction : render.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.equals("java/util/Map")
                        && call.name.equals("size") && call.desc.equals("()I")) countChecks++;
            assertEquals(1, countChecks, name);
        }
    }

    @Test void customSoundUrlHookIsUsedByNativePlayback() throws Exception {
        String engine = "net/minecraft/client/sound/system/SoundEngine";
        method(engine, "getSoundUrl", "(Lnet/minecraft/resource/Identifier;)Ljava/net/URL;");
        var play = read(engine).methods.stream().filter(value -> value.name.equals("play")).findFirst().orElseThrow();
        boolean resolvesUrl = false;
        for (var instruction : play.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(engine) && call.name.equals("getSoundUrl"))
                resolvesUrl = true;
        assertTrue(resolvesUrl);
    }
}
