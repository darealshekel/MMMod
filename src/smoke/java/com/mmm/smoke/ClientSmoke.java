package com.mmm.smoke;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.MixinEnvironment;

public final class ClientSmoke implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            context.runOnClient(client -> {
                MixinEnvironment.getCurrentEnvironment().audit();
                ChatScreen chat = new ChatScreen("");
                client.setScreen(chat);
                if (!(chat.getFocused() instanceof TextFieldWidget input))
                    throw new AssertionError("Chat input is not focused on open");
                chat.charTyped('a', 0);
                if (!input.getText().equals("a")) throw new AssertionError("Vanilla chat typing failed");

                int mmmTabX = input.getX() + client.textRenderer.getWidth("MINECRAFT") + 14;
                chat.mouseClicked(mmmTabX, Math.max(1, input.getY() - 16) + 5, 0);
                Field channel = java.util.Arrays.stream(ChatScreen.class.getDeclaredFields())
                        .filter(field -> field.getName().endsWith("mmm$publicChannelSelected"))
                        .findFirst().orElseThrow();
                channel.setAccessible(true);
                if (!channel.getBoolean(null)) throw new AssertionError("MMM channel did not select");
                chat.charTyped('b', 0);
                if (!input.getText().equals("ab")) throw new AssertionError("MMM chat typing failed");

                client.setScreen(null);
                client.setScreen(new ChatScreen(""));
                if (channel.getBoolean(null)) throw new AssertionError("Channel did not reset on close");
                client.setScreen(new AdvancementsScreen(client.getNetworkHandler().getAdvancementHandler()));
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                client.setScreen(null);
                Files.writeString(Path.of("mmm-smoke-result.txt"), "PASS: mixin audit, chat focus, vanilla/MMM typing, channel reset, advancements");
                System.out.println("MMM_SMOKE_PASS");
            });
        }
        catch (Exception error)
        {
            throw new AssertionError("MMM runtime regression failed", error);
        }
    }
}
