package com.mmm.scoreboard;

import com.mmm.config.Configs;
import com.mmm.legacy.LegacyDrawContext;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.scoreboard.ScoreboardObjective;

public final class ScoreboardHudRenderer {
    private static ScoreboardObjective observed;
    private ScoreboardHudRenderer() {}
    public static Bounds render(LegacyDrawContext context, Minecraft client, ScoreboardObjective objective) {
        Layout layout = layout(client, objective);
        Bounds bounds = layout.bounds;
        context.push();
        try {
            context.translate(bounds.left, bounds.top, 0);
            context.scale(layout.scale, layout.scale, 1);
            context.fill(0, 0, layout.width, 11, alpha(Configs.Generic.SCOREBOARD_TITLE_OPACITY.getDoubleValue()));
            context.fill(0, 11, layout.width, layout.height, alpha(Configs.Generic.SCOREBOARD_BODY_OPACITY.getDoubleValue()));
            int titleAlpha = alpha(Configs.Generic.SCOREBOARD_TITLE_TEXT_OPACITY.getDoubleValue());
            int rowAlpha = alpha(Configs.Generic.SCOREBOARD_TEXT_OPACITY.getDoubleValue());
            // 1.12's font treats alpha <= 3 as fully opaque, so skip those draws explicitly.
            if ((titleAlpha >>> 24) > 3)
                context.drawText(client.textRenderer, layout.title, (layout.width - client.textRenderer.getWidth(layout.title)) / 2,
                        1, titleAlpha | 0xFFFFFF, false);
            if ((rowAlpha >>> 24) > 3) {
                for (int i = 0; i < layout.rows.size(); i++) {
                    var row = layout.rows.get(i);
                    int y = 12 + i * 9;
                    context.drawText(client.textRenderer, row.name(), 2, y, rowAlpha | 0xFFFFFF, false);
                    context.drawText(client.textRenderer, row.score(), layout.width - client.textRenderer.getWidth(row.score()) - 2,
                            y, rowAlpha | 0xFFFFFF, false);
                }
            }
        } finally { context.pop(); }
        return bounds;
    }
    public static boolean hasSidebar(Minecraft client) {
        return ScoreboardService.activeSidebar() != null || ScoreboardService.getSidebarObjective(client).isPresent();
    }
    public static Bounds getBounds(Minecraft client, ScoreboardObjective objective) { return layout(client, objective).bounds; }
    public static void setPosition(Minecraft client, ScoreboardObjective objective, int x, int y) {
        Bounds bounds = getBounds(client, objective);
        Window window = new Window(client);
        Configs.Generic.SCOREBOARD_X.setIntegerValue(ScoreboardLayout.normalize(x, window.getWidth() - bounds.width(), ScoreboardLayout.NORMALIZED_WIDTH));
        Configs.Generic.SCOREBOARD_Y.setIntegerValue(ScoreboardLayout.normalize(y, window.getHeight() - bounds.height(), ScoreboardLayout.NORMALIZED_HEIGHT));
    }
    public static void resetPosition() {
        Configs.Generic.SCOREBOARD_X.resetToDefault();
        Configs.Generic.SCOREBOARD_Y.resetToDefault();
    }
    public static void invalidateLayout() { observed = null; }
    private static Layout layout(Minecraft client, ScoreboardObjective objective) {
        if (observed != objective) { ScoreboardState.resetPage(); observed = objective; }
        List<ScoreboardService.RenderEntry> rows;
        String title;
        var provider = ScoreboardService.activeSidebar();
        if (provider != null) { rows = provider.entries(); title = provider.title(); }
        else {
            var entries = ScoreboardService.getSortedEntries(objective);
            int size = Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue();
            ScoreboardState.clampPage(entries.size(), Math.max(1, size));
            int from = size <= 0 ? 0 : Math.min(entries.size(), ScoreboardState.getPageOffset());
            int to = size <= 0 ? 0 : Math.min(entries.size(), from + size);
            rows = ScoreboardService.getRenderEntries(objective, entries, from, to);
            title = objective == null ? "" : objective.getDisplayName();
        }
        int width = client.textRenderer.getWidth(title);
        for (var row : rows)
            width = Math.max(width, client.textRenderer.getWidth(row.name()) + (row.score().isEmpty() ? 0 : 8 + client.textRenderer.getWidth(row.score())));
        width += 6;
        int height = 13 + rows.size() * 9;
        Window window = new Window(client);
        float scale = (float) Math.max(0.5, Math.min(2, Configs.Generic.SCOREBOARD_SCALE.getDoubleValue()));
        scale = Math.min(scale, Math.min(Math.max(1, window.getWidth() - 6) / (float) width,
                Math.max(1, window.getHeight() - 6) / (float) height));
        Bounds bounds = ScoreboardLayout.place(window.getWidth(), window.getHeight(), (int) Math.ceil(width * scale),
                (int) Math.ceil(height * scale), Configs.Generic.SCOREBOARD_X.getIntegerValue(),
                Configs.Generic.SCOREBOARD_Y.getIntegerValue(),
                (Configs.ScoreboardPosition) Configs.Generic.SCOREBOARD_POSITION.getOptionListValue(),
                Configs.Generic.SCOREBOARD_Y_OFFSET.getIntegerValue());
        return new Layout(title, rows, width, height, scale, bounds);
    }
    private static int alpha(double opacity) { return Math.max(0, Math.min(255, (int) Math.round(opacity * 255))) << 24; }
    public record Bounds(int left, int top, int right, int bottom) {
        public int width() { return right - left; }
        public int height() { return bottom - top; }
        public boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
    }
    private record Layout(String title, List<ScoreboardService.RenderEntry> rows, int width, int height, float scale, Bounds bounds) {}
}

