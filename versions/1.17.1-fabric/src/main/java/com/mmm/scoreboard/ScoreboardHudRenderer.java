package com.mmm.scoreboard;

import java.util.List;

import com.mmm.config.Configs;
import com.mmm.config.Configs.ScoreboardPosition;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import com.mmm.compat.DrawContext;
import com.mmm.compat.ScoreboardCompat.Entry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

public final class ScoreboardHudRenderer
{
    private static final int NORMALIZED_WIDTH = 820;
    private static final int NORMALIZED_HEIGHT = 460;
    private static final long LAYOUT_CACHE_NANOS = 50_000_000L;

    private static ScoreboardObjective cachedObjective;
    private static Layout cachedLayout;
    private static int cachedScreenWidth = -1;
    private static int cachedScreenHeight = -1;
    private static long layoutCacheExpiresAtNanos;

    private ScoreboardHudRenderer()
    {
    }

    public static Bounds render(DrawContext context, MinecraftClient client, ScoreboardObjective objective)
    {
        Layout layout = layout(client, objective);
        TextRenderer renderer = client.textRenderer;
        int bodyBackground = client.options.getTextBackgroundColor(
                (float) Configs.Generic.SCOREBOARD_BODY_OPACITY.getDoubleValue());
        int titleBackground = client.options.getTextBackgroundColor(
                (float) Configs.Generic.SCOREBOARD_TITLE_OPACITY.getDoubleValue());
        int textColor = withAlpha(0xFFFFFF, Configs.Generic.SCOREBOARD_TEXT_OPACITY.getDoubleValue());
        int titleColor = withAlpha(0xFFFFFF, Configs.Generic.SCOREBOARD_TITLE_TEXT_OPACITY.getDoubleValue());

        context.getMatrices().push();
        context.getMatrices().translate(layout.x(), layout.y(), 0.0F);
        context.getMatrices().scale(layout.scale(), layout.scale(), 1.0F);
        context.fill(0, 0, layout.panelWidth(), layout.titleHeight(), titleBackground);
        context.fill(0, layout.titleHeight(), layout.panelWidth(), layout.panelHeight(), bodyBackground);
        context.drawText(renderer, layout.title(),
                (layout.panelWidth() - renderer.getWidth(layout.title())) / 2, 1, titleColor, false);
        for (int index = 0; index < layout.entries().size(); index++)
        {
            ScoreboardService.RenderEntry entry = layout.entries().get(index);
            int rowY = layout.titleHeight() + 1 + index * layout.rowHeight();
            context.drawText(renderer, entry.name(), 2, rowY, textColor, false);
            int scoreWidth = renderer.getWidth(entry.score());
            if (scoreWidth > 0)
            {
                context.drawText(renderer, entry.score(), layout.panelWidth() - scoreWidth - 2, rowY, textColor, false);
            }
        }
        context.getMatrices().pop();
        return layout.bounds();
    }

    public static Bounds getBounds(MinecraftClient client, ScoreboardObjective objective)
    {
        return layout(client, objective).bounds();
    }

    public static void setPosition(MinecraftClient client, ScoreboardObjective objective, int requestedX, int requestedY)
    {
        Bounds bounds = getBounds(client, objective);
        int maxX = Math.max(0, client.getWindow().getScaledWidth() - bounds.width());
        int maxY = Math.max(0, client.getWindow().getScaledHeight() - bounds.height());
        int x = Math.max(0, Math.min(maxX, requestedX));
        int y = Math.max(0, Math.min(maxY, requestedY));
        int storedX = maxX == 0 ? 0 : (int) Math.round(x * (double) NORMALIZED_WIDTH / maxX);
        int storedY = maxY == 0 ? 0 : (int) Math.round(y * (double) NORMALIZED_HEIGHT / maxY);
        Configs.Generic.SCOREBOARD_X.setIntegerValue(Math.max(0, Math.min(NORMALIZED_WIDTH, storedX)));
        Configs.Generic.SCOREBOARD_Y.setIntegerValue(Math.max(0, Math.min(NORMALIZED_HEIGHT, storedY)));
        invalidateLayout();
    }

    public static void resetPosition()
    {
        Configs.Generic.SCOREBOARD_X.resetToDefault();
        Configs.Generic.SCOREBOARD_Y.resetToDefault();
        invalidateLayout();
    }

    private static Layout layout(MinecraftClient client, ScoreboardObjective objective)
    {
        long now = System.nanoTime();
        int currentScreenWidth = client.getWindow().getScaledWidth();
        int currentScreenHeight = client.getWindow().getScaledHeight();
        if (cachedLayout != null
                && cachedObjective == objective
                && cachedScreenWidth == currentScreenWidth
                && cachedScreenHeight == currentScreenHeight
                && now < layoutCacheExpiresAtNanos)
        {
            return cachedLayout;
        }

        List<Entry> sortedEntries = ScoreboardService.getSortedEntries(objective);
        int pageSize = Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue();
        ScoreboardState.clampPage(sortedEntries.size(), Math.max(1, pageSize));
        int from = pageSize <= 0 ? 0 : Math.min(ScoreboardState.getPageOffset(), sortedEntries.size());
        int to = pageSize <= 0 ? 0 : Math.min(sortedEntries.size(), from + pageSize);
        List<ScoreboardService.RenderEntry> entries = ScoreboardService.getRenderEntries(
                objective, sortedEntries, from, to);

        TextRenderer renderer = client.textRenderer;
        Text title = objective.getDisplayName();
        int separatorWidth = renderer.getWidth(": ");
        int contentWidth = renderer.getWidth(title);
        for (ScoreboardService.RenderEntry entry : entries)
        {
            int scoreWidth = renderer.getWidth(entry.score());
            contentWidth = Math.max(contentWidth,
                    renderer.getWidth(entry.name()) + (scoreWidth > 0 ? separatorWidth + scoreWidth : 0));
        }

        int rowHeight = renderer.fontHeight;
        int panelWidth = contentWidth + 6;
        int titleHeight = rowHeight + 2;
        int panelHeight = titleHeight + entries.size() * rowHeight + 2;
        float scale = (float) Math.max(0.5D, Math.min(2.0D, Configs.Generic.SCOREBOARD_SCALE.getDoubleValue()));
        int scaledPanelWidth = Math.round(panelWidth * scale);
        int scaledPanelHeight = Math.round(panelHeight * scale);
        int screenWidth = currentScreenWidth;
        int screenHeight = currentScreenHeight;
        int maxX = Math.max(0, screenWidth - scaledPanelWidth);
        int maxY = Math.max(0, screenHeight - scaledPanelHeight);

        int storedX = Configs.Generic.SCOREBOARD_X.getIntegerValue();
        int storedY = Configs.Generic.SCOREBOARD_Y.getIntegerValue();
        int x;
        int y;
        if (storedX >= 0 && storedY >= 0)
        {
            x = maxX == 0 ? 0 : (int) Math.round(storedX * (double) maxX / NORMALIZED_WIDTH);
            y = maxY == 0 ? 0 : (int) Math.round(storedY * (double) maxY / NORMALIZED_HEIGHT);
        }
        else
        {
            ScoreboardPosition position = (ScoreboardPosition) Configs.Generic.SCOREBOARD_POSITION.getOptionListValue();
            x = position.isLeft() ? 3 : screenWidth - scaledPanelWidth - 3;
            y = switch (position)
            {
                case LEFT_UPPER, RIGHT_UPPER -> 3;
                case LEFT_LOWER, RIGHT_LOWER -> screenHeight - scaledPanelHeight - 3;
                case LEFT, RIGHT -> (screenHeight - scaledPanelHeight) / 2;
            };
            y += Configs.Generic.SCOREBOARD_Y_OFFSET.getIntegerValue();
        }

        x = Math.max(0, Math.min(maxX, x));
        y = Math.max(0, Math.min(maxY, y));
        Layout layout = new Layout(title, List.copyOf(entries), rowHeight, panelWidth, titleHeight, panelHeight, scale, x, y,
                new Bounds(x, y, x + scaledPanelWidth, y + scaledPanelHeight));
        cachedObjective = objective;
        cachedLayout = layout;
        cachedScreenWidth = screenWidth;
        cachedScreenHeight = screenHeight;
        layoutCacheExpiresAtNanos = now + LAYOUT_CACHE_NANOS;
        return layout;
    }

    public static void invalidateLayout()
    {
        cachedObjective = null;
        cachedLayout = null;
        layoutCacheExpiresAtNanos = 0L;
    }
    private static int withAlpha(int rgb, double opacity)
    {
        int alpha = Math.max(0, Math.min(255, (int) Math.round(opacity * 255.0D)));
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public record Bounds(int left, int top, int right, int bottom)
    {
        public int width()
        {
            return this.right - this.left;
        }

        public int height()
        {
            return this.bottom - this.top;
        }

        public boolean contains(double x, double y)
        {
            return x >= this.left && x <= this.right && y >= this.top && y <= this.bottom;
        }
    }

    private record Layout(Text title, List<ScoreboardService.RenderEntry> entries, int rowHeight, int panelWidth,
                          int titleHeight, int panelHeight, float scale, int x, int y, Bounds bounds)
    {
    }
}
