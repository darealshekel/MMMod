package com.mmm.legacy;

import com.mmm.legacy.settings.SessionExport;
import com.mmm.legacy.settings.SettingsUi;
import com.mmm.storage.SessionData;
import com.mmm.storage.SessionHistory;
import com.mmm.util.UiFormat;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class LegacyHistoryScreen extends LegacyScreen {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yy HH:mm").withZone(ZoneId.systemDefault());
    private List<SessionHistory.WorldHistory> worlds = List.of();
    private List<SessionData> sessions = List.of();
    private CompletableFuture<List<SessionHistory.WorldHistory>> loading;
    private CompletableFuture<Path> export;
    private TextFieldWidget search;
    private int selectedWorld, offset, sort;
    private String status = "";
    public LegacyHistoryScreen(Screen parent) {
        super(parent, "Session History");
        loading = CompletableFuture.supplyAsync(SessionHistory::getWorldHistories);
    }
    @Override public void init() {
        String query = search == null ? "" : search.getText();
        boolean focus = search != null && search.isFocused();
        int cursor = search == null ? query.length() : search.getCursor();
        int selection = search == null ? cursor : search.getSelectionEnd();
        super.init();
        search = new TextFieldWidget(10, textRenderer, left + 4, 72, panelWidth - 90, 18);
        search.setText(query); search.setMaxLength(128); search.setFocused(focus);
        search.setCursor(Math.min(cursor, query.length())); search.setSelectionEnd(Math.min(selection, query.length()));
        button(2, left + panelWidth - 80, 70, 80, new String[]{"Recent", "Most Blocks", "Longest"}[sort]);
        if (worlds.isEmpty()) return;
        selectedWorld = Math.max(0, Math.min(selectedWorld, worlds.size() - 1));
        var world = worlds.get(selectedWorld);
        button(1, left, 43, panelWidth, world.displayName() + " (" + (selectedWorld + 1) + "/" + worlds.size() + ")");
        Comparator<SessionData> order = sort == 1 ? Comparator.comparingLong(s -> s.totalBlocks)
                : sort == 2 ? Comparator.comparingLong(SessionData::getActiveDurationMs)
                : Comparator.comparingLong(s -> s.startTimeMs);
        sessions = world.sessions().stream()
                .filter(s -> SettingsUi.matches(DATE.format(Instant.ofEpochMilli(s.startTimeMs)) + " " + s.totalBlocks + " " + s.blockBreakdown.keySet(), query))
                .sorted(order.reversed().thenComparingLong(s -> s.startTimeMs)).toList();
        offset = SettingsUi.clampScroll(offset, sessions.size(), capacity());
        for (int i = 0; i < capacity() && offset + i < sessions.size(); i++) {
            SessionData session = sessions.get(offset + i);
            button(100 + i, left + panelWidth - 48, 131 + i * 38, 48, "Open");
        }
        button(3, left, height - 28, 32, "<").active = offset > 0;
        button(4, left + 38, height - 28, 32, ">").active = offset + capacity() < sessions.size();
        button(5, left + panelWidth - 124, height - 28, 124, "Export This World").active = export == null && !world.sessions().isEmpty();
    }
    private int capacity() { return Math.max(1, (height - 183) / 38); }
    @Override protected void buttonClicked(ButtonWidget b) {
        if (b.id == 1) { selectedWorld = (selectedWorld + 1) % worlds.size(); offset = 0; init(); }
        else if (b.id == 2) { sort = (sort + 1) % 3; offset = 0; init(); }
        else if (b.id == 3 || b.id == 4) { offset += b.id == 3 ? -capacity() : capacity(); init(); }
        else if (b.id == 5) { export = SessionExport.world(worlds.get(selectedWorld)); status = "Exporting complete selected-world history..."; init(); }
        else if (b.id >= 100 && b.id < 100 + capacity()) minecraft.openScreen(new LegacySessionScreen(this, sessions.get(offset + b.id - 100), worlds.get(selectedWorld).displayName()));
        else super.buttonClicked(b);
    }
    @Override public void render(int x, int y, float delta) {
        shell(); search.render();
        if (search.getText().isEmpty() && !search.isFocused()) fit("Search date, blocks or block ID", left + 8, 77, panelWidth - 98, 0xFF949494);
        if (loading != null) fit("Loading history...", left, 106, panelWidth, 0xFF949494);
        else if (!worlds.isEmpty()) {
            var all = worlds.get(selectedWorld).sessions();
            long total = 0, active = 0, best = 0;
            for (SessionData session : all) { total += session.totalBlocks; active += session.getActiveDurationMs(); best = Math.max(best, session.getBestHourBlocks()); }
            fit(all.size() + " sessions / " + UiFormat.formatBlocks(total) + " / IGT " + UiFormat.formatDuration(active / 1000), left, 99, panelWidth, 0xFFBBBBBB);
            fit("Best Hour: " + UiFormat.formatBlocks(best), left, 113, panelWidth, 0xFFBBBBBB);
            for (int i = 0; i < capacity() && offset + i < sessions.size(); i++) {
                SessionData session = sessions.get(offset + i); int py = 131 + i * 38;
                fit(DATE.format(Instant.ofEpochMilli(session.startTimeMs)) + " / " + UiFormat.formatBlocks(session.totalBlocks), left, py, panelWidth - 58, 0xFFFFFFFF);
                fit("IGT " + session.getDurationString() + " / Time " + session.getWallDurationString() + " / " + UiFormat.formatBlocksPerHour(session.getAverageBlocksPerHour()), left, py + 12, panelWidth - 58, 0xFF949494);
                fill(left, py + 31, left + panelWidth, py + 32, 0xFF292929);
            }
            if (sessions.isEmpty()) fit(all.isEmpty() ? "No saved sessions (minimum 10,000 blocks)" : "No matching sessions", left, 139, panelWidth, 0xFF949494);
        }
        fit(status, left, height - 44, panelWidth, 0xFFBBBBBB);
        if (!sessions.isEmpty()) fit((offset + 1) + "-" + Math.min(sessions.size(), offset + capacity()) + " / " + sessions.size(), left + 80, height - 22, panelWidth - 215, 0xFF949494);
        super.render(x, y, delta);
    }
    @Override public void tick() {
        search.tick();
        if (loading != null && loading.isDone()) {
            try { worlds = loading.join(); } catch (RuntimeException e) { status = "Could not load history"; }
            loading = null; init();
        }
        if (export != null && export.isDone()) {
            try { status = "Saved: " + export.join(); } catch (RuntimeException e) { status = "Export failed; saved sessions are unchanged"; }
            export = null; init();
        }
    }
    @Override protected void mouseClicked(int x, int y, int b) { search.mouseClicked(x, y, b); super.mouseClicked(x, y, b); }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) { super.keyPressed(c, key); return; }
        if (!search.isFocused() && !Character.isISOControl(c)) search.setFocused(true);
        if (search.keyPressed(c, key)) { offset = 0; init(); }
    }
    @Override public void handleMouse() { super.handleMouse(); int wheel = Mouse.getEventDWheel(); if (wheel != 0) { offset += wheel > 0 ? -1 : 1; init(); } }
}
