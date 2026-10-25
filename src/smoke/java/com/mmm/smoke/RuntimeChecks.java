package com.mmm.smoke;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;

final class RuntimeChecks {
    static void run(MinecraftClient client) {
        org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
        ChatScreen chat = new ChatScreen("");
        client.setScreen(chat);
        if (!(chat.getFocused() instanceof TextFieldWidget input)) throw new AssertionError("Chat input not focused");
        chat.charTyped('h', 0);
        if (!input.getText().equals("h")) throw new AssertionError("Chat cannot type without clicking");
        client.setScreen(new AdvancementsScreen(client.getNetworkHandler().getAdvancementHandler()));
        client.setScreen(new com.mmm.ui.MmmSettingsScreen(null));
        client.setScreen(null);
    }
}
