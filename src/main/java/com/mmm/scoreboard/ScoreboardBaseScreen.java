package com.mmm.scoreboard;

import com.mmm.config.Configs;
import java.io.IOException;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

abstract class ScoreboardBaseScreen extends Screen {
    protected final Screen parent;
    private final String title;
    protected int left, panelWidth;
    protected String status = "";
    private boolean repeat, captured;
    ScoreboardBaseScreen(Screen parent, String title) { this.parent = parent; this.title = title; }
    @Override public void init() {
        if (!captured) { repeat = Keyboard.areRepeatEventsEnabled(); captured = true; }
        Keyboard.enableRepeatEvents(true);
        panelWidth = Math.max(180, Math.min(640, width - 20));
        left = (width - panelWidth) / 2;
        buttons.clear();
        button(900, left + panelWidth - 54, 8, 54, "Done");
    }
    protected ButtonWidget button(int id, int x, int y, int w, String label) {
        return addButton(new ButtonWidget(id, x, y, w, 20, label));
    }
    protected void text(String value, int x, int y, int color) { textRenderer.draw(value, x, y, color, false); }
    protected void shell() {
        fill(0, 0, width, height, 0xE8050505);
        text(textRenderer.trim(title, panelWidth - 64), left, 14, 0xFFFFFFFF);
        fill(left, 34, left + panelWidth, 35, 0xFFE00000);
        text(textRenderer.trim(status, panelWidth), left, height - 12, 0xFFDDDDDD);
    }
    protected void changed() { Configs.saveToFile(); }
    protected void io(IoAction action) {
        try { status = action.run(); } catch (IOException e) { status = e.getMessage(); }
    }
    @FunctionalInterface protected interface IoAction { String run() throws IOException; }
    @Override protected void buttonClicked(ButtonWidget button) { if (button.id == 900) minecraft.openScreen(parent); }
    @Override protected void keyPressed(char c, int key) { if (key == Keyboard.KEY_ESCAPE) minecraft.openScreen(parent); }
    @Override public void removed() { Configs.saveToFile(); Keyboard.enableRepeatEvents(repeat); captured = false; }
    @Override public boolean shouldPauseGame() { return false; }
}

