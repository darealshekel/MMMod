package com.mmm.legacy;

import com.mmm.ui.BoardEditorStyle;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import com.mmm.legacy.settings.BoardField;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

public abstract class LegacyScreen extends Screen {
    protected final Screen parent;
    protected final String title;
    protected int left, panelWidth;
    private boolean repeat;
    private boolean repeatCaptured;

    protected LegacyScreen(Screen parent, String title) { this.parent = parent; this.title = title; }

    protected int maxContentWidth() { return 760; }

    @Override public void init() {
        if (!repeatCaptured) { repeat = Keyboard.areRepeatEventsEnabled(); repeatCaptured = true; }
        Keyboard.enableRepeatEvents(true);
        panelWidth = BoardEditorStyle.contentWidth(width, maxContentWidth());
        left = BoardEditorStyle.contentX(width, maxContentWidth());
        buttons.clear();
        button(900, left + panelWidth - 60, 5, 60, parent instanceof LegacyMenuScreen ? "Back" : "Done");
    }

    protected ButtonWidget button(int id, int x, int y, int width, String label) {
        return addButton(new com.mmm.legacy.settings.ActionButton(id, x, y, width, label, false));
    }

    protected ButtonWidget recordButton(int id, int y, String label, boolean selected) {
        return addButton(new com.mmm.legacy.settings.ActionButton(id, left, y, panelWidth, label, selected, true));
    }
    protected TextFieldWidget field(int id, int y, String text) {
        TextFieldWidget field = new BoardField(id, textRenderer, left + 4, y, panelWidth - 8, 20);
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

    protected void section(String label, int y) {
        BoardArt.section(new LegacyDrawContext(), textRenderer, left, y, panelWidth, label);
    }
    protected void recordRow(int y, int h, boolean hovered) {
        BoardArt.row(new LegacyDrawContext(), left, y, panelWidth, h, hovered);
    }
    protected void shell() {
        fill(0, 0, width, height, com.mmm.legacy.settings.SettingsUi.background());
        BoardArt.header(new LegacyDrawContext(), textRenderer, width, left, panelWidth - 66, title);
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
