package com.mmm.hud;

import java.util.List;
import com.mmm.config.Configs;
import com.mmm.social.ActiveDiggerManager;
import com.mmm.timer.TimerHudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class ActiveDiggersHudRenderer
{
    public static final int WIDTH = 144;

    private ActiveDiggersHudRenderer() { }

    public static void render(DrawContext context, MinecraftClient client)
    {
        if (client == null || client.player == null || client.world == null || client.options.hudHidden
                || client.currentScreen instanceof HudMoveScreen
                || !Configs.Generic.SHOW_ACTIVE_DIGGERS.getBooleanValue()) return;
        TimerHudRenderer.drawModule(context, client, HudModuleId.ACTIVE_DIGGERS, false);
    }

    private static int rowsPerPage(MinecraftClient client)
    {
        double scale = Configs.Generic.ACTIVE_DIGGERS_SCALE.getDoubleValue();
        return Math.max(1, Math.min(8, (int) (client.getWindow().getScaledHeight() / scale - 12)
                / (client.textRenderer.fontHeight + 3) - 2));
    }

    public static int height(MinecraftClient client)
    {
        int entries = ActiveDiggerManager.visibleRemoteDiggers(List.of()).size();
        int rows = Math.max(1, Math.min(rowsPerPage(client), entries));
        return 8 + (1 + rows + (entries > rows ? 1 : 0)) * (client.textRenderer.fontHeight + 3);
    }

    public static void draw(DrawContext context, MinecraftClient client, boolean preview)
    {
        List<String> names = ActiveDiggerManager.visibleRemoteDiggers(List.of());
        if (names.isEmpty() && !preview) return;
        int lineHeight = client.textRenderer.fontHeight + 3;
        int rows = rowsPerPage(client);
        int pages = Math.max(1, (names.size() + rows - 1) / rows);
        int page = preview ? 0 : (int) ((System.currentTimeMillis() / 5000L) % pages);
        if (Configs.Generic.HUD_TEXT_BACKGROUND.getBooleanValue())
        {
            context.fill(0, 0, WIDTH, height(client), Configs.getHudBackgroundColor());
        }
        context.drawText(client.textRenderer, "Active Diggers", 4, 4, Configs.getHudTitleColor(), Configs.useHudTextShadow());
        int y = 4 + lineHeight;
        if (names.isEmpty())
        {
            context.drawText(client.textRenderer, "No active diggers", 4, y, Configs.getHudTextColor(), Configs.useHudTextShadow());
        }
        for (int index = page * rows; index < Math.min(names.size(), (page + 1) * rows); index++)
        {
            Text name = Text.empty().append(Text.literal("\u26CF ").styled(style -> style.withColor(0xE00000)))
                    .append(Text.literal(names.get(index)));
            context.drawText(client.textRenderer, name, 4, y, Configs.getHudTextColor(), Configs.useHudTextShadow());
            y += lineHeight;
        }
        if (pages > 1)
        {
            context.drawText(client.textRenderer, (page + 1) + "/" + pages, 4, y, Configs.getHudTextColor(), Configs.useHudTextShadow());
        }
    }
}
