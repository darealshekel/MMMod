package com.mmm.hud;

import java.util.ArrayList;
import java.util.List;

import com.mmm.config.Configs;
import com.mmm.config.Configs.HudAlignment;
import com.mmm.config.FeatureToggle;
import com.mmm.sync.CloudSyncManager;
import com.mmm.sync.DigsSyncManager;
import com.mmm.tracker.GoalNotificationManager;
import com.mmm.tracker.MiningStats;
import com.mmm.ui.MmmUi;
import com.mmm.util.UiFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class MiningHudRenderer
{
    private static final int LINE_BOX_COLOR = MmmUi.INSET;
    private static final int SYNC_OK_COLOR = MmmUi.SUCCESS;
    private static final int SYNC_FAIL_COLOR = MmmUi.ERROR;
    private static final int BBOX_FILL_COLOR = MmmUi.PANEL;
    private static final int HUD_NEUTRAL_BORDER_COLOR = 0x66090909;
    private static final int GOAL_BAR_BG = MmmUi.INSET;
    private static final int GOAL_BAR_BORDER = HUD_NEUTRAL_BORDER_COLOR;
    private static final String ZERO_CLOCK = "00:00:00";

    private MiningHudRenderer()
    {
    }

    public static void render(DrawContext context, MinecraftClient client)
    {
        if (FeatureToggle.TWEAK_MINING_TRACKER.getBooleanValue() == false ||
            FeatureToggle.TWEAK_HUD.getBooleanValue() == false ||
            client.player == null ||
            client.options.hudHidden)
        {
            GoalNotificationManager.render(context, client);
            return;
        }

        List<HudLine> lines = new ArrayList<>();
        lines.add(HudLine.text("MMM", hudTitleColor()));

        boolean sessionPaused = MiningStats.isSessionPaused();
        boolean sessionInactive = MiningStats.isSessionActive() == false || sessionPaused;
        if (FeatureToggle.TWEAK_HUD_PROJECT.getBooleanValue())
        {
            MiningStats.ProjectProgress project = MiningStats.getActiveProjectProgress();
            lines.add(HudLine.blocksMined("Project: " + UiFormat.truncate(project.name(), 18) + " | ", project.blocksMined()));
        }
        if (FeatureToggle.TWEAK_HUD_TOTAL_MINED.getBooleanValue())
        {
            long globalTotal = MiningStats.getGlobalTotalMinedForDisplay();
            long worldTotal = MiningStats.getCurrentSourceTotalMined();
            long sessionTotal = MiningStats.getSessionBlocksMined();
            long dailyBlocks = MiningStats.getDailyBlocksMined();
            long weeklyBlocks = MiningStats.getWeeklyBlocksMined();
            long dailyRecord = MiningStats.getPersonalRecordDailyBlocks();
            long weeklyRecord = MiningStats.getPersonalRecordWeeklyBlocks();
            lines.add(HudLine.blocksMined("Global Total: ", globalTotal));
            lines.add(HudLine.blocksMined("World Total: ", worldTotal));
            lines.add(HudLine.blocksMined("Session Total: ", sessionTotal, sessionInactive));
            lines.add(HudLine.dualBlocksMined("Today / Week: ", dailyBlocks, weeklyBlocks));
            lines.add(HudLine.dualBlocksMined("PR Day / Week: ", dailyRecord, weeklyRecord));
            String fastest100k = MiningStats.getFastest100kClock();
            lines.add(HudLine.text("Fastest 100k: " + fastest100k, inactiveTextColor(fastest100k, false)));
        }
        if (FeatureToggle.TWEAK_HUD_BLOCKS_PER_HOUR.getBooleanValue())
        {
            lines.add(HudLine.bphBps(MiningStats.getDisplayedBlocksPerHour(), MiningStats.getDisplayedBlocksPerSecond(), false));
        }
        String sessionClock = MiningStats.getSessionDurationClock();
        lines.add(HudLine.text("Session Time: " + sessionClock, inactiveTextColor(sessionClock, sessionPaused)));
        if (FeatureToggle.TWEAK_DAILY_GOAL.getBooleanValue())
        {
            lines.add(HudLine.text("Daily Reset In: " + MiningStats.getDailyResetCountdownClock(), hudTextColor()));
        }
        if (FeatureToggle.TWEAK_HUD_ETA.getBooleanValue() && FeatureToggle.TWEAK_DAILY_GOAL.getBooleanValue())
        {
            String eta = MiningStats.getEstimatedTimeToDailyGoal();
            lines.add(HudLine.text("ETA To Goal: " + eta, inactiveTextColor(eta, sessionPaused)));
        }

        MiningStats.GoalProgress dailyGoal = MiningStats.getDailyGoalProgress();
        int lineHeight = client.textRenderer.fontHeight + 2;
        int padding = 4;
        int width = Math.max(getTextWidth(client, lines), 190);
        int extraHeight = FeatureToggle.TWEAK_HUD_GOAL_PROGRESS.getBooleanValue() && dailyGoal.enabled() ? 24 : 0;
        int totalHeight = lines.size() * lineHeight + extraHeight + padding * 2;

        float scale = (float) Configs.Generic.HUD_SCALE.getDoubleValue();
        int scaledWidth = (int) ((width + padding * 2) * scale);
        int scaledHeight = (int) (totalHeight * scale);
        int x = resolveHudX(client, scaledWidth);
        int y = resolveHudY(client, scaledHeight);

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0D);
        context.getMatrices().scale(scale, scale, 1.0F);

        if (FeatureToggle.TWEAK_HUD_BOUNDING_BOX.getBooleanValue())
        {
            int bboxX = -padding;
            int bboxY = -2;
            int bboxW = width + padding * 2;
            int bboxH = lines.size() * lineHeight + extraHeight + 4;
            context.fill(bboxX, bboxY, bboxX + bboxW, bboxY + bboxH, BBOX_FILL_COLOR);
        }

        int drawY = 0;
        long now = System.currentTimeMillis();
        boolean syncHealthy = CloudSyncManager.isHudHealthy(now) && DigsSyncManager.isHudHealthy(now);
        String title = lines.getFirst().text();
        int titleTextWidth = client.textRenderer.getWidth(title);
        int syncIndicatorSize = client.textRenderer.fontHeight;
        int titleX = syncIndicatorSize + 4;
        if (Configs.Generic.HUD_TEXT_BACKGROUND.getBooleanValue())
        {
            drawLineBox(context, 0, drawY, titleX + titleTextWidth);
        }
        drawSyncIndicator(context, 0, drawY, syncIndicatorSize, syncHealthy ? SYNC_OK_COLOR : SYNC_FAIL_COLOR);
        context.drawText(client.textRenderer, Text.literal(title), titleX, drawY, hudTitleColor(), true);
        drawY += lineHeight;
        for (int i = 1; i < lines.size(); i++)
        {
            HudLine line = lines.get(i);
            if (Configs.Generic.HUD_TEXT_BACKGROUND.getBooleanValue())
            {
                drawLineBox(context, 0, drawY, line.width(client.textRenderer));
            }
            line.draw(context, client.textRenderer, 0, drawY, false);
            drawY += lineHeight;
        }

        if (FeatureToggle.TWEAK_HUD_GOAL_PROGRESS.getBooleanValue() && dailyGoal.enabled())
        {
            drawGoalProgress(context, client, 0, drawY + 2, width, dailyGoal);
        }

        context.getMatrices().pop();
        GoalNotificationManager.render(context, client);
    }

    public static int[] getBounds(MinecraftClient client)
    {
        List<String> lines = new ArrayList<>();
        lines.add("MMM");
        if (FeatureToggle.TWEAK_HUD_PROJECT.getBooleanValue()) lines.add("Project: Example Project | 12.3k Blocks Mined");
        if (FeatureToggle.TWEAK_HUD_TOTAL_MINED.getBooleanValue()) lines.add("Global Total: 123M Blocks Mined");
        if (FeatureToggle.TWEAK_HUD_TOTAL_MINED.getBooleanValue()) lines.add("World Total: 12.3k Blocks Mined");
        if (FeatureToggle.TWEAK_HUD_TOTAL_MINED.getBooleanValue()) lines.add("Session Total: 890 Blocks Mined");
        if (FeatureToggle.TWEAK_HUD_TOTAL_MINED.getBooleanValue()) lines.add("Today / Week: 12.3k / 84.2k Blocks Mined");
        if (FeatureToggle.TWEAK_HUD_TOTAL_MINED.getBooleanValue()) lines.add("PR Day / Week: 21.5k / 120k Blocks Mined");
        if (FeatureToggle.TWEAK_HUD_TOTAL_MINED.getBooleanValue()) lines.add("Fastest 100k: 8h 20m");
        if (FeatureToggle.TWEAK_HUD_BLOCKS_PER_HOUR.getBooleanValue()) lines.add("BPH: 12.3k / BPS: 3.4");
        lines.add("Session Time: 01:23:45");
        if (FeatureToggle.TWEAK_DAILY_GOAL.getBooleanValue()) lines.add("Daily Reset In: 23:59:59");
        if (FeatureToggle.TWEAK_HUD_ETA.getBooleanValue() && FeatureToggle.TWEAK_DAILY_GOAL.getBooleanValue()) lines.add("ETA To Goal: 1h 12m");

        int width = Math.max(getTextWidth(client, lines), 190);
        int lineHeight = client.textRenderer.fontHeight + 2;
        int padding = 4;
        int extraHeight = FeatureToggle.TWEAK_HUD_GOAL_PROGRESS.getBooleanValue() && FeatureToggle.TWEAK_DAILY_GOAL.getBooleanValue() ? 24 : 0;
        int totalHeight = lines.size() * lineHeight + extraHeight + padding * 2;
        double scale = Configs.Generic.HUD_SCALE.getDoubleValue();
        int scaledWidth = (int) ((width + padding * 2) * scale);
        int scaledHeight = (int) (totalHeight * scale);
        int x = resolveHudX(client, scaledWidth);
        int y = resolveHudY(client, scaledHeight);
        return new int[] { x, y, x + scaledWidth, y + scaledHeight };
    }

    private record HudSegment(String text, int color)
    {
    }

    private record HudLine(List<HudSegment> segments)
    {
        static HudLine text(String text, int color)
        {
            return new HudLine(List.of(new HudSegment(text, color)));
        }

        static HudLine blocksMined(String label, long value)
        {
            return blocksMined(label, value, false);
        }

        static HudLine blocksMined(String label, long value, boolean inactive)
        {
            return new HudLine(List.of(
                    new HudSegment(label, inactive ? hudInactiveColor() : hudTextColor()),
                    new HudSegment(UiFormat.formatCompact(value), inactive ? hudInactiveColor() : Configs.getHudNumberColor()),
                    new HudSegment(" Blocks Mined", inactive ? hudInactiveColor() : hudTextColor())));
        }

        static HudLine dualBlocksMined(String label, long left, long right)
        {
            return new HudLine(List.of(
                    new HudSegment(label, hudTextColor()),
                    new HudSegment(UiFormat.formatCompact(left), Configs.getHudNumberColor()),
                    new HudSegment(" / ", hudTextColor()),
                    new HudSegment(UiFormat.formatCompact(right), Configs.getHudNumberColor()),
                    new HudSegment(" Blocks Mined", hudTextColor())));
        }

        static HudLine bphBps(long blocksPerHour, double blocksPerSecond, boolean inactive)
        {
            int numberColor = inactive ? hudInactiveColor() : Configs.getHudNumberColor();
            int labelColor = inactive ? hudInactiveColor() : hudTextColor();
            return new HudLine(List.of(
                    new HudSegment("BPH: ", labelColor),
                    new HudSegment(UiFormat.formatCompact(Math.max(0L, blocksPerHour)), numberColor),
                    new HudSegment(" / BPS: ", labelColor),
                    new HudSegment(UiFormat.formatBlocksPerSecond(blocksPerSecond), numberColor)));
        }

        String text()
        {
            StringBuilder builder = new StringBuilder();
            for (HudSegment segment : this.segments)
            {
                builder.append(segment.text());
            }
            return builder.toString();
        }

        int width(TextRenderer renderer)
        {
            int width = 0;
            for (HudSegment segment : this.segments)
            {
                width += renderer.getWidth(segment.text());
            }
            return width;
        }

        void draw(DrawContext context, TextRenderer renderer, int x, int y, boolean shadow)
        {
            int drawX = x;
            for (HudSegment segment : this.segments)
            {
                context.drawText(renderer, Text.literal(segment.text()), drawX, y, segment.color(), shadow);
                drawX += renderer.getWidth(segment.text());
            }
        }
    }

    private static int resolveHudX(MinecraftClient client, int scaledWidth)
    {
        int maxX = Math.max(0, client.getWindow().getScaledWidth() - scaledWidth);
        int rawX = Math.max(0, Math.min(Configs.Generic.HUD_X.getIntegerValue(), 820));
        double normalized = rawX / 820.0D;
        HudAlignment alignment = (HudAlignment) Configs.Generic.HUD_ALIGNMENT.getOptionListValue();
        return switch (alignment)
        {
            case TOP_RIGHT, BOTTOM_RIGHT -> Math.max(0, Math.min(maxX, (int) Math.round(maxX * (1.0D - normalized))));
            default -> Math.max(0, Math.min(maxX, (int) Math.round(maxX * normalized)));
        };
    }

    private static int resolveHudY(MinecraftClient client, int scaledHeight)
    {
        int maxY = Math.max(0, client.getWindow().getScaledHeight() - scaledHeight);
        int rawY = Math.max(0, Math.min(Configs.Generic.HUD_Y.getIntegerValue(), 460));
        double normalized = rawY / 460.0D;
        HudAlignment alignment = (HudAlignment) Configs.Generic.HUD_ALIGNMENT.getOptionListValue();
        return switch (alignment)
        {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> Math.max(0, Math.min(maxY, (int) Math.round(maxY * (1.0D - normalized))));
            default -> Math.max(0, Math.min(maxY, (int) Math.round(maxY * normalized)));
        };
    }

    private static void drawLineBox(DrawContext context, int x, int y, int textWidth)
    {
        context.fill(x - 4, y - 2, x + textWidth + 5, y + 11, LINE_BOX_COLOR);
        context.fill(x - 3, y - 1, x + textWidth + 4, y, HUD_NEUTRAL_BORDER_COLOR);
        context.drawBorder(x - 4, y - 2, textWidth + 9, 13, HUD_NEUTRAL_BORDER_COLOR);
    }

    private static void drawSyncIndicator(DrawContext context, int x, int y, int size, int color)
    {
        context.fill(x, y, x + size, y + size, color);
        context.drawBorder(x, y, size, size, 0xAA000000);
    }

    private static void drawGoalProgress(DrawContext context, MinecraftClient client, int x, int y, int width, MiningStats.GoalProgress progress)
    {
        int fillColor = UiFormat.getGoalColor(progress);
        int fillWidth = progress.target() <= 0 ? 0 : (int) Math.min(width, (width * (double) progress.current()) / progress.target());
        String percentText = progress.getPercent() + "%";
        String progressText = UiFormat.formatProgress(progress.current(), progress.target());
        context.drawText(client.textRenderer, Text.literal("Daily Goal"), x, y, hudTitleColor(), false);
        int progressX = x + Math.max(0, (width - client.textRenderer.getWidth(progressText)) / 2);
        context.drawText(client.textRenderer, Text.literal(progressText), progressX, y, hudTextColor(), false);
        context.drawText(client.textRenderer, Text.literal(percentText), x + width - client.textRenderer.getWidth(Text.literal(percentText)), y, fillColor, false);

        int barY = y + 11;
        context.fill(x, barY, x + width, barY + 6, GOAL_BAR_BG);
        context.fill(x, barY, x + fillWidth, barY + 6, fillColor);
        context.drawBorder(x, barY, width, 6, GOAL_BAR_BORDER);
    }

    private static int getTextWidth(MinecraftClient client, List<String> lines)
    {
        int width = 0;
        for (String line : lines)
        {
            width = Math.max(width, client.textRenderer.getWidth(Text.literal(line)));
        }
        return width;
    }

    private static int getTextWidth(MinecraftClient client, Iterable<HudLine> lines)
    {
        int width = 0;
        for (HudLine line : lines)
        {
            width = Math.max(width, line.width(client.textRenderer));
        }
        return width;
    }

    private static int inactiveTextColor(String value, boolean sessionPaused)
    {
        String text = value == null ? "" : value.trim();
        return sessionPaused || "--".equals(text) || "Paused".equals(text) || ZERO_CLOCK.equals(text) ? hudInactiveColor() : hudTextColor();
    }

    private static int hudTitleColor()
    {
        return Configs.getHudTitleColor();
    }

    private static int hudTextColor()
    {
        return Configs.getHudTextColor();
    }

    private static int hudInactiveColor()
    {
        return Configs.getHudInactiveColor();
    }
}
