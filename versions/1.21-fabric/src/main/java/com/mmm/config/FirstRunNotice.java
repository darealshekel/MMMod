package com.mmm.config;

import com.mmm.config.value.ConfigBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class FirstRunNotice
{
    public static final ConfigBoolean SEEN = new ConfigBoolean("gettingStartedSeen", false, "Getting-started message already shown.");
    private FirstRunNotice() {}

    public static void tick(MinecraftClient client)
    {
        if (SEEN.getBooleanValue() || client.world == null || client.player == null || client.currentScreen != null) return;
        client.player.sendMessage(Text.literal("[MMM] Mining, sessions, and collections work without linking. ").formatted(Formatting.GRAY)
                .append(Text.literal("[Settings] ").styled(style -> style.withColor(Formatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mmm"))))
                .append(Text.literal("[Collections]").styled(style -> style.withColor(Formatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mmm collections")))), false);
        SEEN.setBooleanValue(true);
        Configs.requestSave();
    }
}
