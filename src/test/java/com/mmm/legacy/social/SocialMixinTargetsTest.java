package com.mmm.legacy.social;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual Feather dependency without initializing a Minecraft client or OpenGL. */
class SocialMixinTargetsTest {
    @Test void chatHooksMatchLegacyDescriptors() throws Exception {
        ClassNode screen = read("net/minecraft/client/gui/screen/Screen");
        assertMethod(screen, "sendChatMessage", "(Ljava/lang/String;Z)V");
        ClassNode chat = read("net/minecraft/client/gui/screen/game/ChatScreen");
        assertMethod(chat, "init", "()V");
        assertMethod(chat, "removed", "()V");
        assertMethod(chat, "keyPressed", "(CI)V");
        assertMethod(chat, "mouseClicked", "(III)V");
        assertMethod(chat, "render", "(IIF)V");
        assertMethod(chat, "accept", "([Ljava/lang/String;)V");
        assertMethod(read("net/minecraft/client/gui/chat/ChatGui"), "addMessage", "(Lnet/minecraft/text/Text;I)V");
    }

    @Test void tabAndNameHooksMatchLegacyDescriptors() throws Exception {
        ClassNode tab = read("net/minecraft/client/gui/overlay/PlayerTabOverlay");
        assertMethod(tab, "getDisplayName", "(Lnet/minecraft/client/network/PlayerInfo;)Ljava/lang/String;");
        assertMethod(tab, "render", "(ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V");
        assertTrue(tab.fields.stream().anyMatch(field -> field.name.equals("footer") && field.desc.equals("Lnet/minecraft/text/Text;")));
        assertMethod(read("net/minecraft/entity/living/player/PlayerEntity"), "getDisplayName", "()Lnet/minecraft/text/Text;");
    }

    @Test void numericTabFormatterTargetsBothWidthAndDrawingButNotHealth() throws Exception {
        ClassNode tab = read("net/minecraft/client/gui/overlay/PlayerTabOverlay");
        assertMethod(tab, "renderDisplayScore", "(Lnet/minecraft/scoreboard/ScoreboardObjective;ILjava/lang/String;IILnet/minecraft/client/network/PlayerInfo;)V");
        MethodNode width = method(tab, "render");
        MethodNode drawing = method(tab, "renderDisplayScore");
        MethodInsnNode widthAppend = onlyIntAppend(width);
        MethodInsnNode drawingAppend = onlyIntAppend(drawing);

        // Width measurement skips its integer builder when the objective renders hearts.
        JumpInsnNode widthHearts = heartsBranch(width);
        assertEquals(Opcodes.IF_ACMPEQ, widthHearts.getOpcode());
        assertTrue(width.instructions.indexOf(widthHearts) < width.instructions.indexOf(widthAppend));
        assertTrue(width.instructions.indexOf(widthHearts.label) > width.instructions.indexOf(widthAppend));

        // Drawing jumps over the entire heart renderer to reach the integer builder.
        JumpInsnNode drawingHearts = heartsBranch(drawing);
        assertEquals(Opcodes.IF_ACMPNE, drawingHearts.getOpcode());
        assertTrue(drawing.instructions.indexOf(drawingHearts.label) < drawing.instructions.indexOf(drawingAppend));
        int healthTextBuilders = 0;
        for (var instruction : drawing.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/StringBuilder")
                    && call.name.equals("append") && call.desc.equals("(F)Ljava/lang/StringBuilder;")) {
                healthTextBuilders++;
                assertTrue(drawing.instructions.indexOf(call) < drawing.instructions.indexOf(drawingHearts.label));
            }
        }
        assertEquals(1, healthTextBuilders);
    }

    @Test void tabRedirectRequiresBothNumericSitesAndUsesSharedScorePolicy() throws Exception {
        MethodNode hook = method(read("com/mmm/legacy/social/mixin/SocialPlayerTabMixin"), "mmm$formatTabScore");
        var redirect = hook.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;"))
                .findFirst().orElseThrow();
        assertEquals(java.util.List.of("render", "renderDisplayScore"), annotationValue(redirect, "method"));
        assertEquals(2, annotationValue(redirect, "require"));
        var at = (org.objectweb.asm.tree.AnnotationNode) annotationValue(redirect, "at");
        assertEquals("Ljava/lang/StringBuilder;append(I)Ljava/lang/StringBuilder;", annotationValue(at, "target"));
        assertEquals(false, annotationValue(at, "remap"));
        boolean visibility = false, formatting = false;
        for (var instruction : hook.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("com/mmm/scoreboard/ScoreboardService")) {
                visibility |= call.name.equals("shouldShowScores") && call.desc.equals("()Z");
                formatting |= call.name.equals("formatTabScore") && call.desc.equals("(I)Ljava/lang/String;");
            }
        }
        assertTrue(visibility);
        assertTrue(formatting);
    }

    @Test void sidebarDecoratesNameCallsNotScoreValues() throws Exception {
        ClassNode gui = read("net/minecraft/client/gui/GameGui");
        var render = gui.methods.stream().filter(method -> method.name.equals("renderScoreboardObjective")).findFirst().orElseThrow();
        boolean found = false;
        for (var instruction : render.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/scoreboard/team/Team")
                    && call.name.equals("getMemberDisplayName")) {
                assertEquals("(Lnet/minecraft/scoreboard/team/AbstractTeam;Ljava/lang/String;)Ljava/lang/String;", call.desc);
                found = true;
            }
        }
        assertTrue(found);
        for (String mixin : new String[]{"SocialPlayerTabMixin", "SocialSidebarNameMixin"}) {
            for (var method : read("com/mmm/legacy/social/mixin/" + mixin).methods)
                for (var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode call)
                        assertNotEquals("net/minecraft/scoreboard/ScoreboardScore", call.owner);
        }
    }

    @Test void localCommandsAreDispatchedBeforePublicChat() throws Exception {
        var type = read("com/mmm/legacy/social/mixin/SocialChatSendMixin");
        var route = type.methods.stream().filter(method -> method.name.equals("mmm$route")).findFirst().orElseThrow();
        int commands = -1, publicChat = -1, position = 0;
        for (var instruction : route.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("com/mmm/legacy/command/LegacyCommands") && call.name.equals("execute")) commands = position;
                if (call.owner.equals("com/mmm/social/PublicChatClient") && call.name.equals("sendMessage")) publicChat = position;
            }
            position++;
        }
        assertTrue(commands >= 0 && publicChat > commands);
    }

    private static ClassNode read(String name) throws IOException {
        try (var stream = SocialMixinTargetsTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElseThrow();
    }
    private static MethodInsnNode onlyIntAppend(MethodNode method) {
        MethodInsnNode found = null;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/StringBuilder")
                    && call.name.equals("append") && call.desc.equals("(I)Ljava/lang/StringBuilder;")) {
                assertNull(found, "Unexpected additional int builder in " + method.name);
                found = call;
            }
        }
        assertNotNull(found, method.name);
        return found;
    }
    private static JumpInsnNode heartsBranch(MethodNode method) {
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("HEARTS")
                    && field.owner.equals("net/minecraft/scoreboard/criterion/ScoreboardCriterion$RenderType")) {
                AbstractInsnNode next = instruction.getNext();
                while (next != null && next.getOpcode() < 0) next = next.getNext();
                assertInstanceOf(JumpInsnNode.class, next);
                return (JumpInsnNode) next;
            }
        }
        throw new AssertionError("Missing hearts branch in " + method.name);
    }
    private static Object annotationValue(org.objectweb.asm.tree.AnnotationNode annotation, String key) {
        for (int index = 0; index < annotation.values.size(); index += 2)
            if (key.equals(annotation.values.get(index))) return annotation.values.get(index + 1);
        throw new AssertionError("Missing annotation value " + key);
    }
    private static void assertMethod(ClassNode node, String name, String descriptor) {
        assertTrue(node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)), node.name + "." + name + descriptor);
    }
}
