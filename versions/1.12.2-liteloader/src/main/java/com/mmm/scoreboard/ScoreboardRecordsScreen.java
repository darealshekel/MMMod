package com.mmm.scoreboard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Mouse;

public final class ScoreboardRecordsScreen extends ScoreboardBaseScreen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private int offset;
    public ScoreboardRecordsScreen(Screen parent) { super(parent, "Recorded Scoreboards"); }
    private int capacity() { return Math.max(1, (height - 116) / 36); }
    @Override public void init() {
        super.init();
        var rows = ScoreboardState.getSnapshots();
        offset = Math.max(0, Math.min(offset, Math.max(0, rows.size() - capacity())));
        button(1, left, 40, 90, "Record");
        button(2, left + 94, 40, 90, "Export");
        for (int i = 0; i < capacity() && offset + i < rows.size(); i++) {
            int x = left + panelWidth - 82, y = 72 + i * 36;
            button(100 + i * 3, x, y, 24, "^").active = offset + i > 0;
            button(101 + i * 3, x + 28, y, 24, "v").active = offset + i + 1 < rows.size();
            button(102 + i * 3, x + 56, y, 26, "X");
        }
        button(3, left, height - 36, 24, "<");
        button(4, left + 28, height - 36, 24, ">");
    }
    @Override public void render(int x, int y, float delta) {
        shell();
        var rows = ScoreboardState.getSnapshots();
        if (rows.isEmpty()) text("No records", left, 76, 0xFFBBBBBB);
        for (int i = 0; i < capacity() && offset + i < rows.size(); i++) {
            var row = rows.get(offset + i);
            text(textRenderer.trim(row.displayName(), panelWidth - 90), left, 72 + i * 36, 0xFFFFFFFF);
            text(textRenderer.trim(TIME.format(Instant.ofEpochMilli(row.capturedAtMs())) + "  " + row.rows().size() + " rows", panelWidth - 90),
                    left, 84 + i * 36, 0xFFAAAAAA);
        }
        super.render(x, y, delta);
    }
    @Override protected void buttonClicked(ButtonWidget button) {
        if (button.id >= 100 && button.id < 100 + capacity() * 3) {
            int row = offset + (button.id - 100) / 3;
            int action = (button.id - 100) % 3;
            if (action == 2) ScoreboardState.removeSnapshot(row); else ScoreboardState.moveSnapshot(row, action == 0 ? -1 : 1);
            init();
        } else if (button.id == 1) { io(() -> "Recorded " + ScoreboardService.recordCurrent().displayName()); init(); }
        else if (button.id == 2) io(() -> "Exported " + ScoreboardService.exportRecorded().getFileName());
        else if (button.id == 3 || button.id == 4) { offset += button.id == 3 ? -capacity() : capacity(); init(); }
        else super.buttonClicked(button);
    }
    @Override public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) { offset += wheel > 0 ? -1 : 1; init(); }
    }
}

