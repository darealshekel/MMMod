package com.mmm.hud;

import java.util.ArrayList;
import java.util.List;

import com.mmm.config.Configs;
import com.mmm.config.Configs.HudAlignment;
import com.mmm.config.FeatureToggle;
import com.mmm.sync.CloudSyncManager;
import com.mmm.sync.DigsSyncManager;
import com.mmm.timer.MmmTimerState;
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
    private static final int SYNC_OK_COLOR = MmmUi.SUCCESS;
    private static final int SYNC_FAIL_COLOR = MmmUi.ERROR;
    private static final int HUD_NEUTRAL_BORDER_COLOR = 0x66090909;
    private static final int GOAL_BAR_BG = MmmUi.INSET;
    private static final int GOAL_BAR_BORDER = HUD_NEUTRAL_BORDER_COLOR;
    private static final int GOAL_BAR_EXTRA_HEIGHT = 24;
    private static final String ZERO_CLOCK = "00:00:00";

    private MiningHudRenderer()
    {
    }

    public static void render(DrawContext context, MinecraftClient client)
    {
        if (FeatureToggle.MMM_MINING_TRACKER.getBooleanValue() == false ||
            FeatureToggle.MMM_HUD.getBooleanValue() == false ||
            client.player == null ||
            client.options.hudHidden)
        {
            GoalNotificationManager.render(context, client);
            return;
        }

        boolean showTitle = Configs.Generic.HUD_TITLE_VISIBLE.getBooleanValue();
        boolean sessionPaused = MiningStats.isSessionPaused();
        List<HudLine> lines = buildHudLines(showTitle, sessionPaused);
        MiningStats.GoalProgress dailyGoal = MiningStats.getDailyGoalProgress();
        boolean showDailyGoalBar = shouldShowDailyGoalBar(dailyGoal);

        if (lines.isEmpty() && !showDailyGoalBar)
        {
            GoalNotificationManager.render(context, client);
            return;
        }
        int lineHeight = client.textRenderer.fontHeight + 2;
        int padding = 4;
        int width = Math.max(Math.max(getTextWidth(client, lines), getGoalHeaderWidth(client, dailyGoal, showDailyGoalBar)), 190);
        int extraHeight = showDailyGoalBar ? GOAL_BAR_EXTRA_HEIGHT : 0;
        int totalHeight = lines.size() * lineHeight + extraHeight + padding * 2;

        float scale = (float) Configs.Generic.HUD_SCALE.getDoubleValue();
        int scaledWidth = (int) ((width + padding * 2) * scale);
        int scaledHeight = (int) (totalHeight * scale);
        int x = resolveHudX(client, scaledWidth);
        int y = resolveHudY(client, scaledHeight);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

        if (FeatureToggle.MMM_HUD_BOUNDING_BOX.getBooleanValue())
        {
            int bboxX = -padding;
            int bboxY = -2;
            int bboxW = width + padding * 2;
            int bboxH = lines.size() * lineHeight + extraHeight + 4;
            context.fill(bboxX, bboxY, bboxX + bboxW, bboxY + bboxH, Configs.getHudBackgroundColor());
        }

        int drawY = 0;
        long now = System.currentTimeMillis();
        boolean syncHealthy = CloudSyncManager.isHudHealthy(now) && DigsSyncManager.isHudHealthy(now);
        int firstContentLine = 0;
        if (showTitle)
        {
            String title = lines.getFirst().text();
            int titleTextWidth = client.textRenderer.getWidth(title);
            int syncIndicatorSize = client.textRenderer.fontHeight;
            int titleX = syncIndicatorSize + 4;
            if (Configs.Generic.HUD_TEXT_BACKGROUND.getBooleanValue())
            {
                drawLineBox(context, 0, drawY, titleX + titleTextWidth);
            }
            drawSyncIndicator(context, 0, drawY, syncIndicatorSize, syncHealthy ? SYNC_OK_COLOR : SYNC_FAIL_COLOR);
            context.drawText(client.textRenderer, Text.literal(title), titleX, drawY, hudTitleColor(), Configs.useHudTextShadow());
            drawY += lineHeight;
            firstContentLine = 1;
        }
        for (int i = firstContentLine; i < lines.size(); i++)
        {
            HudLine line = lines.get(i);
            if (Configs.Generic.HUD_TEXT_BACKGROUND.getBooleanValue())
            {
                drawLineBox(context, 0, drawY, line.width(client.textRenderer));
            }
            line.draw(context, client.textRenderer, 0, drawY, Configs.useHudTextShadow());
            drawY += lineHeight;
        }

        if (showDailyGoalBar)
        {
            drawGoalProgress(context, client, 0, drawY + 2, width, dailyGoal);
        }

        context.getMatrices().popMatrix();
        GoalNotificationManager.render(context, client);
    }

    private static List<HudLine> buildHudLines(boolean showTitle, boolean sessionPaused)
    {
        List<HudLine> lines = new ArrayList<>();
        if (showTitle)
        {
            lines.add(HudLine.text("MMM", hudTitleColor()));
        }

        boolean sessionInactive = MiningStats.isSessionActive() == false || sessionPaused;
        if (FeatureToggle.MMM_HUD_PROJECT.getBooleanValue())
        {
            MiningStats.ProjectProgress project = MiningStats.getActiveProjectProgress();
            lines.add(HudLine.blocksMined("Project: " + UiFormat.truncate(project.name(), 18) + " | ", project.blocksMined()));
        }
        if (FeatureToggle.MMM_HUD_TOTAL_MINED.getBooleanValue())
        {
            if (Configs.Generic.HUD_GLOBAL_TOTAL_VISIBLE.getBooleanValue())
            {
                lines.add(HudLine.blocksMined("Global Total: ", MiningStats.getGlobalTotalMinedForDisplay()));
            }
            if (Configs.Generic.HUD_WORLD_TOTAL_VISIBLE.getBooleanValue())
            {
                lines.add(HudLine.blocksMined("World Total: ", MiningStats.getCurrentSourceTotalMined()));
            }
            if (Configs.Generic.HUD_SESSION_TOTAL_VISIBLE.getBooleanValue())
            {
                lines.add(HudLine.blocksMined("Session Total: ", MiningStats.getSessionBlocksMined(), sessionInactive));
            }
            if (Configs.Generic.HUD_DAILY_WEEK_VISIBLE.getBooleanValue())
            {
                lines.add(HudLine.dualBlocksMined("Today / Week: ", MiningStats.getDailyBlocksMined(), MiningStats.getWeeklyBlocksMined()));
            }
            if (Configs.Generic.HUD_RECORDS_VISIBLE.getBooleanValue())
            {
                lines.add(HudLine.dualBlocksMined("PR Day / Week: ", MiningStats.getPersonalRecordDailyBlocks(), MiningStats.getPersonalRecordWeeklyBlocks()));
            }
            if (Configs.Generic.HUD_FASTEST_100K_VISIBLE.getBooleanValue())
            {
                String fastest100k = MiningStats.getFastest100kClock();
                lines.add(HudLine.text("Fastest 100k: " + fastest100k, inactiveTextColor(fastest100k, false)));
            }
        }
        if (FeatureToggle.MMM_HUD_BLOCKS_PER_HOUR.getBooleanValue())
        {
            lines.add(HudLine.speedStats(MiningStats.getDisplayedBlocksPerHour(), MiningStats.getDisplayedBlocksPerSecond(), MmmTimerState.getBlocksPerMinute(), false));
        }
        if (Configs.Generic.HOURLY_STATS_VISIBLE.getBooleanValue())
        {
            lines.add(HudLine.dualBlocksMined("Hour / Best Hour: ", MmmTimerState.getCurrentHourBlocks(), MmmTimerState.getBestHourBlocks()));
        }
        if (Configs.Generic.HUD_TIMER_STATUS_VISIBLE.getBooleanValue())
        {
            lines.add(HudLine.timerStats(MmmTimerState.getRemainingMs(), MmmTimerState.isRunning()));
        }
        if (Configs.Generic.HUD_SESSION_TIME_VISIBLE.getBooleanValue())
        {
            String sessionClock = MiningStats.getSessionDurationClock();
            lines.add(HudLine.text("Session Time: " + sessionClock, inactiveTextColor(sessionClock, sessionPaused)));
        }
        if (Configs.Generic.HUD_DAILY_RESET_VISIBLE.getBooleanValue() && FeatureToggle.MMM_DAILY_GOAL.getBooleanValue())
        {
            lines.add(HudLine.text("Daily Reset In: " + MiningStats.getDailyResetCountdownClock(), hudTextColor()));
        }
        if (FeatureToggle.MMM_HUD_ETA.getBooleanValue() && FeatureToggle.MMM_DAILY_GOAL.getBooleanValue())
        {
            String eta = MiningStats.getEstimatedTimeToDailyGoal();
            lines.add(HudLine.text("ETA To Goal: " + eta, inactiveTextColor(eta, sessionPaused)));
        }

        return lines;
    }

    public static int[] getBounds(MinecraftClient client)
    {
        List<HudLine> lines = buildHudLines(Configs.Generic.HUD_TITLE_VISIBLE.getBooleanValue(), MiningStats.isSessionPaused());
        MiningStats.GoalProgress dailyGoal = MiningStats.getDailyGoalProgress();
        boolean showDailyGoalBar = shouldShowDailyGoalBar(dailyGoal);

        int width = Math.max(Math.max(getTextWidth(client, lines), getGoalHeaderWidth(client, dailyGoal, showDailyGoalBar)), 190);
        int lineHeight = client.textRenderer.fontHeight + 2;
        int padding = 4;
        int extraHeight = showDailyGoalBar ? GOAL_BAR_EXTRA_HEIGHT : 0;
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

        static HudLine speedStats(long blocksPerHour, double blocksPerSecond, double blocksPerMinute, boolean inactive)
        {
            int numberColor = inactive ? hudInactiveColor() : Configs.getHudNumberColor();
            int labelColor = inactive ? hudInactiveColor() : hudTextColor();
            List<HudSegment> segments = new ArrayList<>();
            segments.add(new HudSegment("Blocks/hr: ", labelColor));
            segments.add(new HudSegment(UiFormat.formatCompact(Math.max(0L, blocksPerHour)), numberColor));
            segments.add(new HudSegment(" / Blocks/sec: ", labelColor));
            segments.add(new HudSegment(UiFormat.formatBlocksPerSecond(blocksPerSecond), numberColor));
            if (Configs.Generic.BLOCKS_PER_MINUTE_VISIBLE.getBooleanValue())
            {
                segments.add(new HudSegment(" / Blocks/min: ", labelColor));
                segments.add(new HudSegment(UiFormat.formatBlocksPerMinute(blocksPerMinute), numberColor));
            }
            return new HudLine(segments);
        }

        static HudLine timerStats(long remainingMs, boolean running)
        {
            int labelColor = running ? hudTextColor() : hudInactiveColor();
            int numberColor = running ? Configs.getHudNumberColor() : hudInactiveColor();
            return new HudLine(List.of(
                    new HudSegment("Timer: ", labelColor),
                    new HudSegment(MmmTimerState.formatTime(remainingMs), numberColor)));
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
        context.fill(x - 4, y - 2, x + textWidth + 5, y + 11, Configs.getHudBackgroundColor());
        context.fill(x - 3, y - 1, x + textWidth + 4, y, HUD_NEUTRAL_BORDER_COLOR);
        context.drawBorder(x - 4, y - 2, textWidth + 9, 13, HUD_NEUTRAL_BORDER_COLOR);
    }

    private static void drawSyncIndicator(DrawContext context, int x, int y, int size, int color)
    {
        context.fill(x, y, x + size, y + size, color);
        context.drawBorder(x, y, size, size, 0xAA000000);
    }

    private static boolean shouldShowDailyGoalBar(MiningStats.GoalProgress progress)
    {
        return Configs.Generic.HUD_DAILY_GOAL_BAR_VISIBLE.getBooleanValue()
                && FeatureToggle.MMM_DAILY_GOAL.getBooleanValue()
                && progress != null
                && progress.enabled();
    }

    private static int getGoalHeaderWidth(MinecraftClient client, MiningStats.GoalProgress progress, boolean visible)
    {
        if (!visible || progress == null)
        {
            return 0;
        }

        return client.textRenderer.getWidth("Daily Goal")
                + client.textRenderer.getWidth(UiFormat.formatProgress(progress.current(), progress.target()))
                + client.textRenderer.getWidth(UiFormat.formatGoalPercent(progress))
                + 16;
    }

    private static void drawGoalProgress(DrawContext context, MinecraftClient client, int x, int y, int width, MiningStats.GoalProgress progress)
    {
        int fillColor = UiFormat.getGoalProgressColor(progress);
        int fillWidth = progress.target() <= 0L
                ? 0
                : (int) Math.min(width, width * Math.max(0.0D, progress.current() / (double) progress.target()));
        String percentText = UiFormat.formatGoalPercent(progress);
        String progressText = UiFormat.formatProgress(progress.current(), progress.target());

        context.drawText(client.textRenderer, Text.literal("Daily Goal"), x, y, hudTitleColor(), Configs.useHudTextShadow());
        int progressX = x + Math.max(0, (width - client.textRenderer.getWidth(progressText)) / 2);
        context.drawText(client.textRenderer, Text.literal(progressText), progressX, y, hudTextColor(), Configs.useHudTextShadow());
        int percentX = x + width - client.textRenderer.getWidth(percentText);
        context.drawText(client.textRenderer, Text.literal(percentText), percentX, y, fillColor, Configs.useHudTextShadow());

        int barY = y + 11;
        context.fill(x, barY, x + width, barY + 6, GOAL_BAR_BG);
        if (fillWidth > 0)
        {
            context.fill(x, barY, x + fillWidth, barY + 6, fillColor);
        }
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
