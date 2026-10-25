package com.mmm.legacy.settings;

import com.mmm.config.Configs;
import com.mmm.feature.PerimeterWallDigHelper;
import com.mmm.legacy.LegacyBlockItems;
import com.mmm.legacy.LegacyDrawContext;
import com.mmm.legacy.LegacyScreen;
import java.util.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class PerimeterBlocksScreen extends LegacyScreen {
    private TextFieldWidget input;
    private int offset;
    private String status = "";
    public PerimeterBlocksScreen(Screen parent) { super(parent, "Perimeter Block List"); }
    private int capacity() { return Math.max(1, (height - 118) / 24); }
    @Override public void init() {
        String text = input == null ? "" : input.getText();
        super.init();
        input = new TextFieldWidget(1, textRenderer, left + 4, 45, panelWidth - 62, 18);
        input.setMaxLength(128); input.setText(text);
        button(1, left + panelWidth - 52, 43, 52, "Add");
        var blocks = Configs.Generic.PERIMETER_OUTLINE_BLOCKS_LIST.getStrings();
        offset = SettingsUi.clampScroll(offset, blocks.size(), capacity());
        for (int i = 0; i < capacity() && offset + i < blocks.size(); i++) button(100 + i, left + panelWidth - 24, 75 + i * 24, 24, "x");
    }
    @Override protected void buttonClicked(ButtonWidget b) {
        List<String> blocks = new ArrayList<>(Configs.Generic.PERIMETER_OUTLINE_BLOCKS_LIST.getStrings());
        if (b.id == 1) {
            String id = input.getText().trim().toLowerCase(Locale.ROOT);
            if (!id.contains(":")) id = "minecraft:" + id;
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || LegacyBlockItems.icon(id).isEmpty()) { status = "Unknown 1.12.2 block ID"; return; }
            if (!blocks.contains(id)) blocks.add(id);
            input.setText(""); status = "";
        } else if (b.id >= 100 && b.id < 100 + capacity()) blocks.remove(offset + b.id - 100);
        else { super.buttonClicked(b); return; }
        Configs.Generic.PERIMETER_OUTLINE_BLOCKS_LIST.setStrings(blocks);
        PerimeterWallDigHelper.refreshFromConfig(); Configs.saveToFile(); init();
    }
    @Override public void render(int x, int y, float delta) {
        shell(); input.render();
        if (input.getText().isEmpty() && !input.isFocused()) fit("minecraft:stone", left + 8, 50, panelWidth - 72, 0xFF949494);
        var blocks = Configs.Generic.PERIMETER_OUTLINE_BLOCKS_LIST.getStrings();
        for (int i = 0; i < capacity() && offset + i < blocks.size(); i++) {
            String id = blocks.get(offset + i); int py = 78 + i * 24;
            new LegacyDrawContext().drawItem(LegacyBlockItems.icon(id), left, py - 3);
            fit(LegacyBlockItems.name(id), left + 24, py, panelWidth - 56, 0xFFFFFFFF);
        }
        if (blocks.isEmpty()) fit("No protected block types", left, 79, panelWidth, 0xFF949494);
        fit(status, left, height - 20, panelWidth, 0xFFFFB84D);
        super.render(x, y, delta);
    }
    @Override public void tick() { input.tick(); }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) super.keyPressed(c, key);
        else if (key == Keyboard.KEY_RETURN) buttonClicked(new ButtonWidget(1, 0, 0, ""));
        else input.keyPressed(c, key);
    }
    @Override protected void mouseClicked(int x, int y, int b) { input.mouseClicked(x, y, b); super.mouseClicked(x, y, b); }
    @Override public void handleMouse() { super.handleMouse(); int wheel = Mouse.getEventDWheel(); if (wheel != 0) { offset += wheel > 0 ? -1 : 1; init(); } }
}
