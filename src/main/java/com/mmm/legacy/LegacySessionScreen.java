package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.legacy.settings.*;
import com.mmm.storage.*;
import com.mmm.timer.MmmTimerState;
import com.mmm.tracker.MiningStats;
import com.mmm.tracker.SessionEfficiency;
import com.mmm.util.UiFormat;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class LegacySessionScreen extends LegacyScreen {
    private static final int CONTENT_TOP = 98;
    private final SessionData saved;
    private final String world;
    private CompletableFuture<SessionEfficiency> activityLoad;
    private CompletableFuture<Path> export;
    private String status = "", activityStatus = "";
    private TextFieldWidget search;
    private SessionUiModel.Filter filter = SessionUiModel.Filter.ALL;
    private boolean byName, confirmEnd;
    private int scroll, maxScroll, breakdownTop, efficiencyTop;
    private boolean draggingScroll;
    private List<Map.Entry<String, Long>> blocks = List.of();

    public LegacySessionScreen(Screen parent, SessionData saved) { this(parent, saved, WorldSessionContext.getCurrentWorldName()); }
    public LegacySessionScreen(Screen parent, SessionData saved, String world) {
        super(parent, "Session Summary / " + world); this.saved = saved; this.world = world;
        if (saved != null && saved != MiningStats.getCurrentSession() && saved.efficiency.tracked() == 0)
            activityLoad = EfficiencyStore.load(saved);
    }
    @Override public void init() {
        String query = search == null ? "" : search.getText();
        boolean focused = search != null && search.isFocused();
        super.init();
        search = new TextFieldWidget(1, textRenderer, left + 4, 44, Math.max(24, panelWidth - 158), 18);
        search.setMaxLength(128); search.setText(query); search.setFocused(focused);
        button(5, left + panelWidth - 148, 43, 86, filter.label);
        button(6, left + panelWidth - 58, 43, 58, byName ? "Name" : "Count");
        int tabWidth = (panelWidth - 12) / 3;
        button(7, left, 68, tabWidth, "Overview");
        button(8, left + tabWidth + 6, 68, tabWidth, "Activity");
        button(9, left + (tabWidth + 6) * 2, 68, tabWidth, "Blocks");
        int n = saved == null ? 4 : 2, w = (panelWidth - (n - 1) * 6) / n;
        button(1, left, height - 28, w, "Copy");
        button(2, left + w + 6, height - 28, w, "Save Full");
        if (saved == null) {
            button(3, left + (w + 6) * 2, height - 28, w, MiningStats.isSessionPaused() ? "Resume" : "Pause");
            button(4, left + (w + 6) * 3, height - 28, w, MiningStats.isSessionActive() ? (confirmEnd ? "Confirm End" : "End & Save") : "Start");
        }
        refreshActions();
    }
    private SessionData session() { return saved == null ? MiningStats.getCurrentSession() : saved; }
    private long activeMs() { return saved == null ? MiningStats.getSessionDurationMs() : saved.getActiveDurationMs(); }
    private long wallMs() { return saved == null ? MiningStats.getSessionWallDurationMs() : saved.getWallDurationMs(); }
    private long bestHour() { return saved == null ? MmmTimerState.getBestHourBlocks() : saved.getBestHourBlocks(); }
    private void refreshActions() {
        for (ButtonWidget b : buttons) {
            if (b.id == 1 || b.id == 2) b.active = activityLoad == null && (export == null || export.isDone());
            if (b.id == 3) { b.message = MiningStats.isSessionPaused() ? "Resume" : "Pause"; b.active = MiningStats.isSessionActive() && minecraft.world != null; }
            if (b.id == 4) b.active = minecraft.world != null && minecraft.player != null;
        }
    }
    @Override public void render(int x, int y, float delta) {
        shell(); search.render();
        if (search.getText().isEmpty() && !search.isFocused()) fit("Search blocks", left + 8, 49, Math.max(1, panelWidth - 170), 0xFF949494);
        SessionData session = session();
        blocks = SessionUiModel.blocks(session, search.getText(), filter, byName, LegacyBlockItems::name);
        int columns = panelWidth >= 600 ? 5 : 2, cell = (panelWidth - (columns - 1) * 8 - 10) / columns;
        int y0 = CONTENT_TOP + 4 - scroll;
        String[] labels = {"Total Mined", "Session IGT", "Session Time", "Avg Rate", "Best Hour"};
        String[] values = {UiFormat.formatCompact(session.totalBlocks), MmmTimerState.formatTime(activeMs()), MmmTimerState.formatTime(wallMs()),
                UiFormat.formatCompact(SessionUiModel.average(session.totalBlocks, activeMs())), UiFormat.formatCompact(bestHour())};
        String[] units = {"blocks", "pauses excluded", "pauses included", "blocks/hr", "blocks mined"};
        List<String> tooltip = null;
        LegacyDrawContext context = new LegacyDrawContext();
        try (var clip = context.clip(left, CONTENT_TOP, left + panelWidth, height - 54)) {
            for (int i = 0; i < labels.length; i++) {
                int px = left + i % columns * (cell + 8), py = y0 + i / columns * 52;
                fill(px, py, px + cell, py + 46, 0xE0121212);
                fit(labels[i], px + 6, py + 6, cell - 12, 0xFF949494);
                fit(values[i], px + 6, py + 18, cell - 12, 0xFFFFFFFF);
                fit(units[i], px + 6, py + 32, cell - 12, 0xFF949494);
            }
            int graphY = y0 + ((labels.length + columns - 1) / columns) * 52 + 8;
            fit("Mining Rate / blocks per hour", left, graphY, panelWidth - 10, 0xFFFFFFFF);
            tooltip = graph(session, graphY + 18, x, y);
            int goalY = graphY + 108;
            if (saved == null) {
                MiningStats.GoalProgress goal = MiningStats.getDailyGoalProgress();
                if (goal.enabled()) {
                    fit("Daily Goal / " + UiFormat.formatProgress(goal.current(), goal.target()) + " / " + UiFormat.formatGoalPercent(goal), left, goalY, panelWidth - 10, 0xFFFFFFFF);
                    fill(left, goalY + 14, left + panelWidth - 10, goalY + 19, 0xFF292929);
                    fill(left, goalY + 14, left + (int) ((panelWidth - 10) * Math.min(1, goal.current() / (double) Math.max(1, goal.target()))), goalY + 19, UiFormat.getGoalColor(goal));
                }
            }
            fit("Best Streak: " + UiFormat.formatDuration(session.bestStreakSeconds) + " / Peak: " + UiFormat.formatBlocksPerHour(session.getPeakBlocksPerHour()), left, goalY + 28, panelWidth - 10, 0xFFBBBBBB);
            int efficiencyY = goalY + 54;
            efficiencyTop = efficiencyY + scroll - CONTENT_TOP;
            fit("Mining Efficiency", left, efficiencyY, panelWidth - 10, 0xFFFFFFFF);
            fit(activityLoad != null ? "Loading activity..." : !activityStatus.isEmpty() ? activityStatus : session.efficiency.tracked() == 0 ? "No activity recorded; time is untracked" : "Session IGT / pauses excluded", left, efficiencyY + 13, panelWidth - 10, 0xFF949494);
            int effColumns = panelWidth >= 480 ? 2 : 1, effWidth = (panelWidth - 10 - (effColumns - 1) * 18) / effColumns;
            SessionEfficiency.Activity[] activities = SessionEfficiency.Activity.values();
            for (int i = 0; i <= activities.length; i++) {
                var activity = i < activities.length ? activities[i] : null;
                long duration = activity == null ? session.efficiency.untracked(activeMs()) : session.efficiency.time(activity);
                int px = left + (i % effColumns) * (effWidth + 18), py = efficiencyY + 34 + i / effColumns * 36;
                fit(activity == null ? "Untracked time" : activity.label, px, py, effWidth, 0xFFDDDDDD);
                double percent = SessionUiModel.percent(duration, activeMs());
                fit(MmmTimerState.formatTime(duration) + " / " + String.format(Locale.ROOT, "%.1f%%", percent), px, py + 11, effWidth, 0xFF949494);
                fill(px, py + 24, px + effWidth, py + 27, 0xFF292929);
                fill(px, py + 24, px + (int) (effWidth * percent / 100), py + 27, activity == null ? 0xFF888888 : activity.color);
                if (x >= px && x < px + effWidth && y >= py && y < py + 30 && y >= CONTENT_TOP && y < height - 54)
                    tooltip = textRenderer.split(activity == null ? "Older session time and gaps longer than 250ms cannot be classified reliably. Pauses are excluded." : activity.label + ": " + duration + " ms. Pauses are excluded.", Math.min(280, panelWidth));
            }
            int blockY = efficiencyY + 34 + ((activities.length + 1 + effColumns - 1) / effColumns) * 36 + 12;
            breakdownTop = blockY + scroll - CONTENT_TOP;
            fit("Block Breakdown / " + blocks.size() + " types", left, blockY, panelWidth - 10, 0xFFFFFFFF);
            for (int i = 0; i < blocks.size(); i++) {
                int py = blockY + 24 + i * 25;
                if (py < 50 || py > height - 54) continue;
                var entry = blocks.get(i);
                context.drawItem(LegacyBlockItems.icon(entry.getKey()), left, py - 3);
                String count = UiFormat.formatCompact(entry.getValue()) + " / " + String.format(Locale.ROOT, "%.1f%%", SessionUiModel.percent(entry.getValue(), session.totalBlocks));
                int right = left + panelWidth - 12;
                fit(LegacyBlockItems.name(entry.getKey()), left + 24, py, panelWidth - textRenderer.getWidth(count) - 44, 0xFFDDDDDD);
                text(count, right - textRenderer.getWidth(count), py, 0xFFFFFFFF);
                fill(left + 24, py + 15, right, py + 17, 0xFF292929);
                fill(left + 24, py + 15, left + 24 + (int) ((panelWidth - 36) * SessionUiModel.percent(entry.getValue(), session.totalBlocks) / 100), py + 17, Configs.getMenuColor());
                if (x >= left && x < right && y >= py - 3 && y < py + 21 && y >= CONTENT_TOP && y < height - 54)
                    tooltip = List.of(LegacyBlockItems.name(entry.getKey()), entry.getKey(), Long.toString(entry.getValue()) + " blocks");
            }
            if (blocks.isEmpty()) fit(session.blockBreakdown.isEmpty() ? "No mined blocks" : "No matching blocks", left, blockY + 26, panelWidth - 10, 0xFF949494);
            int contentEnd = blockY + scroll + 28 + Math.max(1, blocks.size()) * 25;
            maxScroll = Math.max(0, contentEnd - (height - 54));
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        drawScrollbar();
        fit(status.isEmpty() && saved == null ? MiningStats.isSessionActive() ? MiningStats.isSessionPaused() ? "Paused" : "Session active" : "No active session" : status, left, height - 45, panelWidth, 0xFFBBBBBB);
        super.render(x, y, delta);
        if (tooltip != null && !draggingScroll) renderTooltip(tooltip, x, y);
    }
    private List<String> graph(SessionData session, int y, int mouseX, int mouseY) {
        int h = 72, w = panelWidth - 10;
        fill(left, y, left + w, y + h, 0xFF151515);
        for (int i = 1; i < 4; i++) fill(left, y + h * i / 4, left + w, y + h * i / 4 + 1, 0xFF292929);
        List<Integer> buckets = session.miningRateBuckets;
        if (buckets.isEmpty()) return null;
        int max = SessionUiModel.graphCeiling(session);
        int lineColor = Configs.getMenuColor(), areaColor = 0x28000000 | lineColor & 0xFFFFFF;
        int previousY = 0;
        int samples = buckets.size() == 1 ? 1 : w;
        for (int pixel = 0; pixel < samples; pixel++) {
            double position = samples == 1 ? 0 : pixel * (buckets.size() - 1.0) / (samples - 1);
            int start = (int) position, end = Math.min(buckets.size() - 1, start + 1);
            double value = session.getBucketBlocksPerHour(buckets.get(start)) * (1 - (position - start))
                    + session.getBucketBlocksPerHour(buckets.get(end)) * (position - start);
            if (buckets.size() > samples) {
                value = 0;
                int first = pixel * buckets.size() / samples, last = (pixel + 1) * buckets.size() / samples;
                for (int i = first; i < last; i++) value = Math.max(value, session.getBucketBlocksPerHour(buckets.get(i)));
            }
            int lineY = y + h - 2 - (int) Math.round(value * (h - 12) / max);
            int px = left + (samples == 1 ? w / 2 : pixel);
            if (samples == 1) fill(px - 2, lineY - 2, px + 3, lineY + 3, lineColor);
            else {
                fill(px, lineY, px + 1, y + h, areaColor);
                fill(px, pixel == 0 ? lineY : Math.min(lineY, previousY), px + 1,
                        (pixel == 0 ? lineY : Math.max(lineY, previousY)) + 1, lineColor);
            }
            previousY = lineY;
        }
        fit(UiFormat.formatCompact(max) + "/hr", left + 4, y + 3, w - 8, 0xFF949494);
        if (mouseX >= left && mouseX < left + w && mouseY >= y && mouseY < y + h && mouseY >= CONTENT_TOP && mouseY < height - 54) {
            int i = Math.min(buckets.size() - 1, (mouseX - left) * buckets.size() / w);
            return List.of("Active minute " + (i + 1), UiFormat.formatBlocksPerHour(session.getBucketBlocksPerHour(buckets.get(i))), UiFormat.formatBlocks(buckets.get(i)));
        }
        return null;
    }
    private void drawScrollbar() {
        if (maxScroll <= 0) return;
        int track = Math.max(1, height - 54 - CONTENT_TOP), thumb = Math.max(12, track * track / (track + maxScroll));
        int y = CONTENT_TOP + (track - thumb) * scroll / maxScroll;
        fill(left + panelWidth - 4, CONTENT_TOP, left + panelWidth - 2, height - 54, 0xFF292929);
        fill(left + panelWidth - 4, y, left + panelWidth - 2, y + thumb, Configs.getMenuColor());
    }
    private void scrollTo(int y) {
        int track = Math.max(1, height - 54 - CONTENT_TOP), thumb = Math.max(12, track * track / Math.max(1, track + maxScroll));
        scroll = (int) Math.round(Math.max(0, Math.min(1, (y - CONTENT_TOP - thumb / 2.0) / Math.max(1, track - thumb))) * maxScroll);
    }
    @Override protected void buttonClicked(ButtonWidget b) {
        if (b.id == 1) { setClipboard(SessionUiModel.share(session(), world, activeMs(), wallMs(), bestHour())); status = "Copied full session"; }
        else if (b.id == 2) { export = SessionExport.write(SessionUiModel.snapshot(session(), world, activeMs(), wallMs(), bestHour()), "session"); status = "Saving full session..."; }
        else if (b.id == 3 && saved == null) { MiningStats.togglePauseSession(); confirmEnd = false; init(); }
        else if (b.id == 4 && saved == null) {
            if (!MiningStats.isSessionActive()) { MiningStats.startNewSession(); init(); }
            else if (!confirmEnd) { confirmEnd = true; status = session().totalBlocks < SessionHistory.MIN_SESSION_BLOCKS ? "Under 10,000 blocks: history will not retain this session. Save Full exports it." : "End this session and save it to history?"; init(); }
            else { SessionData finished = MiningStats.finaliseSession(); minecraft.openScreen(new LegacySessionScreen(parent, finished, world)); }
        } else if (b.id == 5) { filter = SessionUiModel.Filter.values()[(filter.ordinal() + 1) % SessionUiModel.Filter.values().length]; scroll = breakdownTop; init(); }
        else if (b.id == 6) { byName = !byName; scroll = breakdownTop; init(); }
        else if (b.id == 7) scroll = 0;
        else if (b.id == 8) scroll = Math.min(maxScroll, efficiencyTop);
        else if (b.id == 9) scroll = Math.min(maxScroll, breakdownTop);
        else super.buttonClicked(b);
        refreshActions();
    }
    @Override public void tick() {
        search.tick();
        if (activityLoad != null && activityLoad.isDone()) {
            try { saved.efficiency = activityLoad.join(); } catch (RuntimeException e) { activityStatus = "Saved activity data unavailable"; }
            activityLoad = null;
        }
        if (export != null && export.isDone()) {
            try { status = "Saved: " + export.join(); } catch (RuntimeException e) { status = "Export failed; session is unchanged"; }
            export = null;
        }
        refreshActions();
    }
    @Override protected void mouseClicked(int x, int y, int b) {
        if (b == 0 && x >= left + panelWidth - 8 && x <= left + panelWidth && y >= CONTENT_TOP && y < height - 54) { draggingScroll = true; scrollTo(y); return; }
        search.mouseClicked(x, y, b); super.mouseClicked(x, y, b);
    }
    @Override protected void mouseDragged(int x, int y, int b, long elapsed) { if (draggingScroll) scrollTo(y); }
    @Override protected void mouseReleased(int x, int y, int b) { draggingScroll = false; super.mouseReleased(x, y, b); }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (!search.getText().isEmpty()) { search.setText(""); scroll = 0; } else super.keyPressed(c, key); return;
        }
        if (!search.isFocused() && !Character.isISOControl(c)) search.setFocused(true);
        if (search.keyPressed(c, key)) scroll = breakdownTop;
    }
    @Override public void handleMouse() { super.handleMouse(); int wheel = Mouse.getEventDWheel(); if (wheel != 0) scroll = Math.max(0, Math.min(maxScroll, scroll + (wheel > 0 ? -36 : 36))); }
}
