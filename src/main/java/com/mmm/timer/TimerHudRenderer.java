package com.mmm.timer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mmm.config.Configs;
import com.mmm.hud.HudModuleId;

import com.mmm.util.UiFormat;

import com.mmm.config.value.IConfigDouble;
import com.mmm.config.value.IConfigInteger;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import com.mmm.legacy.LegacyDrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public final class TimerHudRenderer
{
    private static final int CARD_BORDER = 0xFF1F1F1F;
    private static final int WIDTH_TIMER = 150;
    private static final int HEIGHT_TIMER = 16;
    private static final int TIMER_BAR_WIDTH = 140;
    private static final int TIMER_BAR_HEIGHT = 4;
    private static final int WIDTH_BLOCKS = 190;
    private static final int WIDTH_NOTIFICATION = 172;
    private static final int HEIGHT_NOTIFICATION = 44;
    private static final int BLOCK_STATS_PAGE_SIZE = 8;
    private static final int BLOCK_STATS_STATIC_SIZE = 10;
    private static final long BLOCK_STATS_PAGE_SWITCH_MS = 3_000L;
    private static final Map<String, ItemStack> ICON_CACHE = new LinkedHashMap<>();
    private static final Map<String, String> NAME_CACHE = new LinkedHashMap<>();
    private static int blockStatsPage;
    private static long lastBlockStatsPageSwitchMs;

    private TimerHudRenderer()
    {
    }

    public static void render(LegacyDrawContext context, Minecraft client)
    {
        if (client == null || client.player == null || client.options.hideGui)
        {
            return;
        }

        boolean timerActive = MmmTimerState.isTimerDisplayActive();
        if (timerActive && isPlayerListOpen(client) == false)
        {
            drawModule(context, client, HudModuleId.TIMER, false);
        }
        if (Configs.Generic.BLOCK_STATS_VISIBLE.getBooleanValue())
        {
            drawModule(context, client, HudModuleId.BLOCK_STATS, false);
        }
        if (Configs.Generic.TIMER_NOTIFICATIONS.getBooleanValue() && MmmTimerState.hasNotification(System.currentTimeMillis()))
            drawModule(context, client, HudModuleId.NOTIFICATION, false);
    }

    public static void drawModule(LegacyDrawContext context, Minecraft client, HudModuleId module, boolean preview)
    {
        if (module == HudModuleId.MAIN || client == null)
        {
            return;
        }

        int[] bounds = getBounds(client, module);
        int x = bounds[0];
        int y = bounds[1];
        double scale = getScale(module);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0F);
        context.getMatrices().scale((float) scale, (float) scale, 1.0F);
        switch (module)
        {
            case TIMER -> drawTimer(context, client, preview);
            case BLOCK_STATS -> drawBlockStats(context, client, preview);
            case NOTIFICATION -> drawNotification(context, client, preview);
            default -> {}
        }
        context.getMatrices().pop();
    }

    public static int[] getBounds(Minecraft client, HudModuleId module)
    {
        if (module == HudModuleId.MAIN)
        {
            return com.mmm.hud.MiningHudRenderer.getBounds(client);
        }

        int width = rawWidth(module);
        int height = rawHeight(client, module);
        double scale = getScale(module);
        int scaledW = (int) Math.round(width * scale);
        int scaledH = (int) Math.round(height * scale);
        int x = resolveX(client, xConfig(module), scaledW);
        int y = resolveY(client, yConfig(module), scaledH);
        return new int[] { x, y, x + scaledW, y + scaledH };
    }

    public static boolean isVisible(HudModuleId module)
    {
        return switch (module)
        {
            case MAIN -> com.mmm.config.FeatureToggle.MMM_HUD.getBooleanValue();
            case TIMER -> Configs.Generic.TIMER_HUD_VISIBLE.getBooleanValue();
            case BLOCK_STATS -> Configs.Generic.BLOCK_STATS_VISIBLE.getBooleanValue();
            case NOTIFICATION -> Configs.Generic.TIMER_NOTIFICATIONS.getBooleanValue();
        };
    }

    public static void toggleVisible(HudModuleId module)
    {
        switch (module)
        {
            case MAIN -> com.mmm.config.FeatureToggle.MMM_HUD.toggleBooleanValue();
            case TIMER -> Configs.Generic.TIMER_HUD_VISIBLE.setBooleanValue(!Configs.Generic.TIMER_HUD_VISIBLE.getBooleanValue());
            case BLOCK_STATS -> Configs.Generic.BLOCK_STATS_VISIBLE.setBooleanValue(!Configs.Generic.BLOCK_STATS_VISIBLE.getBooleanValue());
            case NOTIFICATION -> Configs.Generic.TIMER_NOTIFICATIONS.setBooleanValue(!Configs.Generic.TIMER_NOTIFICATIONS.getBooleanValue());
            default -> {}
        }
        Configs.saveToFile();
        MmmTimerState.save();
    }

    public static void setModulePosition(Minecraft client, HudModuleId module, int actualX, int actualY)
    {
        if (module == HudModuleId.MAIN) Configs.Generic.HUD_ALIGNMENT.setOptionListValue(Configs.HudAlignment.TOP_LEFT);

        int[] bounds = getBounds(client, module);
        int width = bounds[2] - bounds[0];
        int height = bounds[3] - bounds[1];
        int maxX = Math.max(1, new net.minecraft.client.render.Window(client).getWidth() - width);
        int maxY = Math.max(1, new net.minecraft.client.render.Window(client).getHeight() - height);
        xConfig(module).setIntegerValue(Math.max(0, Math.min(820, (int) Math.round((Math.max(0, Math.min(maxX, actualX)) / (double) maxX) * 820.0D))));
        yConfig(module).setIntegerValue(Math.max(0, Math.min(460, (int) Math.round((Math.max(0, Math.min(maxY, actualY)) / (double) maxY) * 460.0D))));
    }

    public static void adjustScale(HudModuleId module, double delta)
    {
        IConfigDouble config = scaleConfig(module);
        double next = Math.max(config.getMinDoubleValue(), Math.min(config.getMaxDoubleValue(), config.getDoubleValue() + delta));
        config.setDoubleValue(next);
        Configs.saveToFile();
        MmmTimerState.save();
    }

    public static void resetLayout()
    {
        Configs.Generic.TIMER_HUD_VISIBLE.setBooleanValue(false);
        Configs.Generic.BLOCK_STATS_VISIBLE.setBooleanValue(true);
        Configs.Generic.TIMER_NOTIFICATIONS.setBooleanValue(true);
        Configs.Generic.TIMER_HUD_X.resetToDefault();
        Configs.Generic.TIMER_HUD_Y.resetToDefault();
        Configs.Generic.TIMER_HUD_SCALE.resetToDefault();
        Configs.Generic.BLOCK_STATS_X.resetToDefault();
        Configs.Generic.BLOCK_STATS_Y.resetToDefault();
        Configs.Generic.BLOCK_STATS_SCALE.resetToDefault();
        Configs.Generic.TIMER_NOTIFICATION_X.resetToDefault();
        Configs.Generic.TIMER_NOTIFICATION_Y.resetToDefault();
        Configs.Generic.TIMER_NOTIFICATION_SCALE.resetToDefault();
        Configs.saveToFile();
        MmmTimerState.save();
    }

    public static void resetModuleLayout(HudModuleId module) {
        xConfig(module).resetToDefault();
        yConfig(module).resetToDefault();
        scaleConfig(module).resetToDefault();
        if (module == HudModuleId.MAIN) Configs.Generic.HUD_ALIGNMENT.resetToDefault();
        Configs.saveToFile();
    }

    private static void drawTimer(LegacyDrawContext context, Minecraft client, boolean preview)
    {
        if (preview == false && MmmTimerState.isTimerDisplayActive() == false)
        {
            return;
        }

        long remaining = MmmTimerState.getRemainingMs();
        String timerText = MmmTimerState.formatTime(remaining);
        int textWidth = client.textRenderer.getWidth(timerText);
        double ratio = MmmTimerState.getDurationMs() <= 0L ? 0D : remaining / (double) MmmTimerState.getDurationMs();
        float progress = Math.max(0F, Math.min(1F, (float) ratio));
        int color = MmmTimerState.isExpired() ? 0xFFFF5555 : getTimerColor(progress);

        int barX = (WIDTH_TIMER - TIMER_BAR_WIDTH) / 2;
        int barY = 0;
        context.fill(barX, barY, barX + TIMER_BAR_WIDTH, barY + TIMER_BAR_HEIGHT, 0xFF333333);
        int filledWidth = (int) (TIMER_BAR_WIDTH * progress);
        if (filledWidth > 0)
        {
            context.fill(barX, barY, barX + filledWidth, barY + TIMER_BAR_HEIGHT, color);
        }

        drawTextShadow(context, client.textRenderer, timerText, (WIDTH_TIMER - textWidth) / 2, barY + TIMER_BAR_HEIGHT + 3, color);

        if (MmmTimerState.isExpired())
        {
            long now = System.currentTimeMillis();
            int alpha = (int) (128 + 127 * Math.sin(now / 300.0D));
            int expiredColor = (alpha << 24) | 0x00FF5555;
            String expiredText = "TIME'S UP!";
            int expiredWidth = client.textRenderer.getWidth(expiredText);
            drawTextShadow(context, client.textRenderer, expiredText, (WIDTH_TIMER - expiredWidth) / 2, barY + TIMER_BAR_HEIGHT + 3 + client.textRenderer.fontHeight + 2, expiredColor);
        }
    }

    private static void drawBlockStats(LegacyDrawContext context, Minecraft client, boolean preview)
    {
        List<MmmTimerState.BlockCount> blocks = MmmTimerState.getTopBlocks();
        if (Configs.Generic.BLOCK_STATS_BACKGROUND.getBooleanValue())
            context.fill(0, 0, WIDTH_BLOCKS, blockStatsHeight(blocks.size()), Configs.getHudBackgroundColor());
        if (blocks.isEmpty())
        {
            if (preview)
            {
                String header = "Block Statistics";
                drawTextShadow(context, client.textRenderer, header, WIDTH_BLOCKS - client.textRenderer.getWidth(header), 0, 0xFFDDDDDD);
            }
            return;
        }

        boolean isStatic = Configs.Generic.BLOCK_STATS_STATIC.getBooleanValue();
        boolean showIcons = Configs.Generic.BLOCK_STATS_ICONS.getBooleanValue();
        int maxEntries = isStatic ? Math.min(BLOCK_STATS_STATIC_SIZE, blocks.size()) : BLOCK_STATS_PAGE_SIZE;
        int totalPages = 1;
        int startIndex = 0;

        if (isStatic == false)
        {
            totalPages = (int) Math.ceil(blocks.size() / (double) BLOCK_STATS_PAGE_SIZE);
            if (totalPages > 1)
            {
                long now = System.currentTimeMillis();
                if (now - lastBlockStatsPageSwitchMs > BLOCK_STATS_PAGE_SWITCH_MS)
                {
                    lastBlockStatsPageSwitchMs = now;
                    blockStatsPage = (blockStatsPage + 1) % totalPages;
                }
            }
            else
            {
                blockStatsPage = 0;
            }
            blockStatsPage = Math.floorMod(blockStatsPage, totalPages);
            startIndex = blockStatsPage * BLOCK_STATS_PAGE_SIZE;
        }

        int endIndex = Math.min(startIndex + maxEntries, blocks.size());
        int lineHeight = showIcons ? 18 : client.textRenderer.fontHeight + 2;
        int iconOffset = showIcons ? 18 : 0;
        int line = 0;
        String header = isStatic ? "Block Statistics" : "Block Statistics >>>";
        int headerWidth = client.textRenderer.getWidth(header);
        drawTextShadow(context, client.textRenderer, header, WIDTH_BLOCKS - headerWidth, line * lineHeight, 0xFFDDDDDD);
        line++;

        for (int i = startIndex; i < endIndex; i++)
        {
            MmmTimerState.BlockCount entry = blocks.get(i);
            String count = ": " + UiFormat.formatBlocks(entry.count());
            String displayName = client.textRenderer.trim(blockName(entry.id()), Math.max(0, WIDTH_BLOCKS - iconOffset - client.textRenderer.getWidth(count) - 4));
            String text = displayName + count;
            int textWidth = client.textRenderer.getWidth(text);
            int rowColor = (i % 2 == 0) ? 0xFFAADDFF : 0xFF99BBDD;
            int rowY = line * lineHeight + (showIcons ? 4 : 0);

            if (showIcons)
            {
                try
                {
                    ItemStack stack = getCachedIcon(entry.id());
                    if (stack.isEmpty() == false)
                    {
                        context.drawItem(stack, WIDTH_BLOCKS - textWidth - iconOffset, line * lineHeight);
                    }
                }
                catch (RuntimeException ignored)
                {
                }
            }

            drawTextShadow(context, client.textRenderer, text, WIDTH_BLOCKS - textWidth, rowY, rowColor);
            line++;
        }

        if (isStatic == false && totalPages > 1)
        {
            String pageText = (blockStatsPage + 1) + "/" + totalPages;
            int pageWidth = client.textRenderer.getWidth(pageText);
            drawTextShadow(context, client.textRenderer, pageText, WIDTH_BLOCKS - pageWidth, line * lineHeight, 0xFF888888);
        }
    }

    private static void drawNotification(LegacyDrawContext context, Minecraft client, boolean preview)
    {
        drawCard(context, WIDTH_NOTIFICATION, HEIGHT_NOTIFICATION, "HOUR " + MmmTimerState.getNotificationHour());
        long blocks = MmmTimerState.getNotificationBlocks();
        double bpm = MmmTimerState.getNotificationBlocksPerMinute();
        drawText(context, client.textRenderer, UiFormat.formatCompact(blocks) + " blocks", 12, 23, Configs.getHudNumberColor());
        drawTextRight(context, client.textRenderer, UiFormat.formatBlocksPerMinute(bpm) + "/min", WIDTH_NOTIFICATION - 12, 23, Configs.getHudTextColor());
    }

    private static void drawCard(LegacyDrawContext context, int width, int height, String title)
    {
        context.fill(0, 0, width, height, Configs.getHudBackgroundColor());
        context.drawBorder(0, 0, width, height, CARD_BORDER);
        context.fill(8, 8, 11, 18, 0xFFE00000);
        drawText(context, Minecraft.getInstance().textRenderer, title, 17, 8, Configs.getHudTitleColor());
    }

    private static void drawText(LegacyDrawContext context, TextRenderer renderer, String text, int x, int y, int color)
    {
        context.drawText(renderer, text, x, y, color, Configs.useHudTextShadow());
    }

    private static void drawTextShadow(LegacyDrawContext context, TextRenderer renderer, String text, int x, int y, int color)
    {
        context.drawText(renderer, text, x, y, color, Configs.useHudTextShadow());
    }

    private static void drawTextRight(LegacyDrawContext context, TextRenderer renderer, String text, int rightX, int y, int color)
    {
        context.drawText(renderer, text, rightX - renderer.getWidth(text), y, color, Configs.useHudTextShadow());
    }

    private static int rawWidth(HudModuleId module)
    {
        return switch (module)
        {
            case TIMER -> WIDTH_TIMER;
            case BLOCK_STATS -> WIDTH_BLOCKS;
            case NOTIFICATION -> WIDTH_NOTIFICATION;
            default -> 190;
        };
    }

    private static int rawHeight(Minecraft client, HudModuleId module)
    {
        return switch (module)
        {
            case TIMER -> HEIGHT_TIMER + (MmmTimerState.isExpired() ? client.textRenderer.fontHeight + 2 : 0);
            case BLOCK_STATS -> blockStatsHeight(MmmTimerState.getTopBlocks().size());
            case NOTIFICATION -> HEIGHT_NOTIFICATION;
            default -> 120;
        };
    }

    private static IConfigInteger xConfig(HudModuleId module)
    {
        return switch (module)
        {
            case TIMER -> Configs.Generic.TIMER_HUD_X;
            case BLOCK_STATS -> Configs.Generic.BLOCK_STATS_X;
            case NOTIFICATION -> Configs.Generic.TIMER_NOTIFICATION_X;
            default -> Configs.Generic.HUD_X;
        };
    }

    private static IConfigInteger yConfig(HudModuleId module)
    {
        return switch (module)
        {
            case TIMER -> Configs.Generic.TIMER_HUD_Y;
            case BLOCK_STATS -> Configs.Generic.BLOCK_STATS_Y;
            case NOTIFICATION -> Configs.Generic.TIMER_NOTIFICATION_Y;
            default -> Configs.Generic.HUD_Y;
        };
    }

    private static IConfigDouble scaleConfig(HudModuleId module)
    {
        return switch (module)
        {
            case TIMER -> Configs.Generic.TIMER_HUD_SCALE;
            case BLOCK_STATS -> Configs.Generic.BLOCK_STATS_SCALE;
            case NOTIFICATION -> Configs.Generic.TIMER_NOTIFICATION_SCALE;
            default -> Configs.Generic.HUD_SCALE;
        };
    }

    private static double getScale(HudModuleId module)
    {
        return Math.max(0.25D, Math.min(3.0D, scaleConfig(module).getDoubleValue()));
    }

    private static int resolveX(Minecraft client, IConfigInteger config, int scaledWidth)
    {
        int maxX = Math.max(0, new net.minecraft.client.render.Window(client).getWidth() - scaledWidth);
        return Math.max(0, Math.min(maxX, (int) Math.round(maxX * (Math.max(0, Math.min(820, config.getIntegerValue())) / 820.0D))));
    }

    private static int resolveY(Minecraft client, IConfigInteger config, int scaledHeight)
    {
        int maxY = Math.max(0, new net.minecraft.client.render.Window(client).getHeight() - scaledHeight);
        return Math.max(0, Math.min(maxY, (int) Math.round(maxY * (Math.max(0, Math.min(460, config.getIntegerValue())) / 460.0D))));
    }

    private static int blockStatsHeight(int entries)
    {
        boolean showIcons = Configs.Generic.BLOCK_STATS_ICONS.getBooleanValue();
        boolean isStatic = Configs.Generic.BLOCK_STATS_STATIC.getBooleanValue();
        int lineHeight = showIcons ? 18 : Minecraft.getInstance().textRenderer.fontHeight + 2;
        int visibleEntries = isStatic ? Math.min(BLOCK_STATS_STATIC_SIZE, entries) : Math.min(BLOCK_STATS_PAGE_SIZE, entries);
        int pageLine = isStatic || entries <= BLOCK_STATS_PAGE_SIZE ? 0 : 1;
        return Math.max(lineHeight, lineHeight * (1 + Math.max(0, visibleEntries) + pageLine));
    }

    private static boolean isPlayerListOpen(Minecraft client)
    {
        return client != null && client.options != null && client.options.playerListKey.isPressed();
    }

    private static String blockName(String id)
    {
        return NAME_CACHE.computeIfAbsent(id, com.mmm.legacy.LegacyBlockItems::name);
    }

    private static ItemStack getCachedIcon(String id)
    {
        ItemStack cached = ICON_CACHE.get(id);
        if (cached != null)
        {
            return cached.copy();
        }
        ItemStack stack = com.mmm.legacy.LegacyBlockItems.icon(id);
        ICON_CACHE.put(id, stack.copy());
        return stack;
    }

    private static int getTimerColor(float ratio)
    {
        ratio = Math.max(0F, Math.min(1F, ratio));
        int r;
        int g;
        int b;
        if (ratio > 0.75F)
        {
            float t = (ratio - 0.75F) / 0.25F;
            r = (int) (85 + (1F - t) * 85);
            g = 255;
            b = 85;
        }
        else if (ratio > 0.5F)
        {
            float t = (ratio - 0.5F) / 0.25F;
            r = (int) (170 + (1F - t) * 85);
            g = 255;
            b = (int) (85 - t * 85);
        }
        else if (ratio > 0.25F)
        {
            float t = (ratio - 0.25F) / 0.25F;
            r = 255;
            g = (int) (170 + t * 85);
            b = 0;
        }
        else if (ratio > 0.1F)
        {
            float t = (ratio - 0.1F) / 0.15F;
            r = 255;
            g = (int) (85 + t * 85);
            b = 0;
        }
        else
        {
            float t = ratio / 0.1F;
            r = 255;
            g = (int) (t * 85);
            b = 0;
        }
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
