package com.mmm.legacy;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

public abstract class LegacyScreen extends Screen {
    protected final Screen parent;
    protected final String title;
    protected int left, panelWidth;
    private boolean repeat;
    private boolean repeatCaptured;

    protected LegacyScreen(Screen parent, String title) { this.parent = parent; this.title = title; }

    @Override public void init() {
        if (!repeatCaptured) { repeat = Keyboard.areRepeatEventsEnabled(); repeatCaptured = true; }
        Keyboard.enableRepeatEvents(true);
        panelWidth = Math.max(1, Math.min(760, width - 24));
        left = (width - panelWidth) / 2;
        buttons.clear();
        button(900, left + panelWidth - 60, 10, 60, "Done");
    }

    protected ButtonWidget button(int id, int x, int y, int width, String label) {
        return addButton(new com.mmm.legacy.settings.ActionButton(id, x, y, width, label, false));
    }

    protected TextFieldWidget field(int id, int y, String text) {
        TextFieldWidget field = new TextFieldWidget(id, textRenderer, left + 4, y, panelWidth - 8, 20);
        field.setMaxLength(256);
        field.setText(text);
        return field;
    }

    protected void text(String text, int x, int y, int color) {
        textRenderer.draw(text, x, y, color, false);
    }

    protected void fit(String text, int x, int y, int available, int color) {
        text(textRenderer.trim(text, Math.max(0, available)), x, y, color);
    }

    protected void shell() {
        fill(0, 0, width, height, com.mmm.legacy.settings.SettingsUi.background());
        fill(left, 34, left + panelWidth, 35, com.mmm.config.Configs.getMenuColor());
        fit(title, left, 16, panelWidth - 70, 0xFFF6F3EF);
    }

    @Override protected void buttonClicked(ButtonWidget button) {
        if (button.id == 900) minecraft.openScreen(parent);
    }
    @Override protected void keyPressed(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) minecraft.openScreen(parent);
    }
    @Override public void removed() { Keyboard.enableRepeatEvents(repeat); repeatCaptured = false; }
    @Override public boolean shouldPauseGame() { return true; }
}
