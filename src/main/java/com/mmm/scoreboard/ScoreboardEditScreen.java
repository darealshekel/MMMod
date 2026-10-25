package com.mmm.scoreboard;

import java.util.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ScoreboardEditScreen extends ScoreboardBaseScreen {
    private final ScoreboardObjective objective;
    private final List<Row> rows = new ArrayList<>();
    private final List<Fields> fields = new ArrayList<>();
    private final Map<String, Integer> original = new LinkedHashMap<>();
    private int offset;
    public ScoreboardEditScreen(Screen parent, ScoreboardObjective objective) {
        super(parent, "Edit: " + objective.getDisplayName());
        this.objective = objective;
        for (var entry : ScoreboardService.getSortedEntries(objective)) {
            original.put(entry.getOwner(), entry.get());
            rows.add(new Row(entry.getOwner(), Integer.toString(entry.get())));
        }
    }
    private int capacity() { return Math.max(1, (height - 116) / 26); }
    private boolean canEdit() {
        return minecraft.player != null && minecraft.world != null && minecraft.world.getScoreboard() == objective.getScoreboard()
                && minecraft.world.getScoreboard().getObjective(objective.getName()) == objective
                && minecraft.player.canUseCommand(2, "scoreboard");
    }
    @Override public void init() {
        capture();
        super.init();
        fields.clear();
        offset = Math.max(0, Math.min(offset, Math.max(0, rows.size() - capacity())));
        button(1, left, 40, 58, "Add");
        button(2, left + 62, 40, 58, "Save").active = canEdit();
        button(3, left + 124, 40, 64, "Name");
        button(4, left + 192, 40, 64, "Score");
        for (int i = 0; i < capacity() && offset + i < rows.size(); i++) {
            Row row = rows.get(offset + i);
            int y = 70 + i * 26;
            int nameWidth = Math.max(60, (panelWidth - 42) * 2 / 3);
            var name = new TextFieldWidget(i * 2, textRenderer, left, y, nameWidth, 20);
            name.setMaxLength(40); name.setText(row.name);
            var value = new TextFieldWidget(i * 2 + 1, textRenderer, left + nameWidth + 4, y, panelWidth - nameWidth - 36, 20);
            value.setMaxLength(11); value.setText(row.score);
            fields.add(new Fields(row, name, value));
            button(100 + i, left + panelWidth - 28, y, 28, "X");
        }
        button(5, left, height - 36, 24, "<");
        button(6, left + 28, height - 36, 24, ">");
        if (!canEdit()) status = "Operator permission level 2 required";
    }
    private void capture() {
        for (var field : fields) { field.row.name = field.name.getText(); field.row.score = field.score.getText(); }
    }
    @Override public void render(int x, int y, float delta) {
        shell();
        for (var field : fields) { field.name.render(); field.score.render(); }
        super.render(x, y, delta);
    }
    @Override public void tick() { for (var field : fields) { field.name.tick(); field.score.tick(); } }
    @Override protected void mouseClicked(int x, int y, int button) {
        for (var field : fields) { field.name.mouseClicked(x, y, button); field.score.mouseClicked(x, y, button); }
        super.mouseClicked(x, y, button);
    }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) { super.keyPressed(c, key); return; }
        for (var field : fields) { field.name.keyPressed(c, key); field.score.keyPressed(c, key); }
    }
    @Override protected void buttonClicked(ButtonWidget button) {
        capture();
        if (button.id >= 100 && button.id < 100 + fields.size()) { rows.remove(offset + button.id - 100); init(); }
        else switch (button.id) {
            case 1 -> { rows.add(new Row("", "0")); offset = Math.max(0, rows.size() - capacity()); init(); }
            case 2 -> save();
            case 3 -> { rows.sort(Comparator.comparing(row -> row.name, String.CASE_INSENSITIVE_ORDER)); init(); }
            case 4 -> {
                try { rows.sort(Comparator.comparingInt((Row row) -> Integer.parseInt(row.score)).reversed()); init(); }
                catch (NumberFormatException e) { status = "Invalid integer score"; }
            }
            case 5, 6 -> { offset += button.id == 5 ? -capacity() : capacity(); init(); }
            default -> super.buttonClicked(button);
        }
    }
    private void save() {
        if (!canEdit()) { status = "Scoreboard unavailable or permission denied"; return; }
        Map<String, Integer> after = new LinkedHashMap<>();
        try {
            for (Row row : rows) {
                if (after.putIfAbsent(row.name, Integer.parseInt(row.score)) != null) throw new IllegalArgumentException("Duplicate owner: " + row.name);
            }
            // Protect edited rows without rejecting saves because unrelated players are still mining.
            Map<String, Integer> current = new LinkedHashMap<>();
            for (var score : ScoreboardService.getSortedEntries(objective)) current.put(score.getOwner(), score.get());
            var commands = ScoreboardCommands.changes(objective.getName(), original, after, current);
            for (String command : commands) minecraft.player.sendChat(command);
            status = "Sent " + commands.size() + " changes";
            minecraft.openScreen(parent);
        } catch (IllegalArgumentException e) { status = e.getMessage(); }
    }
    @Override public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) { capture(); offset += wheel > 0 ? -1 : 1; init(); }
    }
    private static final class Row {
        String name, score;
        Row(String name, String score) { this.name = name; this.score = score; }
    }
    private record Fields(Row row, TextFieldWidget name, TextFieldWidget score) {}
}
