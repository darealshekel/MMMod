package com.mmm.legacy.settings;

import com.mmm.config.Configs;
import com.mmm.config.value.*;
import com.mmm.legacy.LegacyScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

public final class ValueEditorScreen extends LegacyScreen {
    private final IConfigBase config;
    private TextFieldWidget input;
    private String error = "";
    private int channel = -1;
    public ValueEditorScreen(Screen parent, IConfigBase config) { super(parent, config.getPrettyName()); this.config = config; }
    @Override public void init() {
        String value = input == null ? config.getStringValue() : input.getText();
        super.init(); buttons.get(0).message = "Cancel";
        input = field(1, 65, value); input.setFocused(true);
        int y = config instanceof ConfigColor ? 196 : 116;
        button(1, left, y, (panelWidth - 8) / 2, "Apply");
        button(2, left + (panelWidth + 8) / 2, y, (panelWidth - 8) / 2, "Default");
    }
    @Override protected void buttonClicked(ButtonWidget b) {
        if (b.id == 2) { input.setText(config.getDefaultStringValue()); error = ""; }
        else if (b.id == 1) {
            try {
                String value = SettingsUi.validate(config, input.getText());
                config.setValueFromString(value); Configs.saveToFile(); minecraft.openScreen(parent);
            } catch (IllegalArgumentException e) { error = "Invalid value"; }
        } else super.buttonClicked(b);
    }
    @Override public void render(int x, int y, float delta) {
        shell(); fit(config.getComment(), left, 47, panelWidth, 0xFFBBBBBB); input.render();
        if (config instanceof ConfigColor) {
            int color = 0;
            try { color = SettingsUi.color(input.getText()); } catch (IllegalArgumentException ignored) {}
            String[] names = {"Red", "Green", "Blue", "Alpha"};
            int[] shifts = {16, 8, 0, 24};
            for (int i = 0; i < 4; i++) {
                int py = 94 + i * 24, amount = color >>> shifts[i] & 255;
                text(names[i], left, py + 5, 0xFFBBBBBB);
                fill(left + 42, py, left + panelWidth - 34, py + 16, 0xFF292929);
                fill(left + 42, py + 12, left + 42 + (panelWidth - 76) * amount / 255, py + 16, Configs.getMenuColor());
                text(Integer.toString(amount), left + panelWidth - 28, py + 5, 0xFFFFFFFF);
            }
        } else {
            String hint = config instanceof IConfigInteger n ? n.getMinIntegerValue() + " - " + n.getMaxIntegerValue()
                    : config instanceof IConfigDouble n ? n.getMinDoubleValue() + " - " + n.getMaxDoubleValue() : "";
            fit(hint, left, 94, panelWidth, 0xFFBBBBBB);
        }
        fit(error, left, height - 16, panelWidth, 0xFFFF8888);
        super.render(x, y, delta);
    }
    private void slide(int x) {
        int[] shifts = {16, 8, 0, 24};
        try {
            int color = SettingsUi.color(input.getText());
            int value = (int) Math.round(255 * Math.max(0, Math.min(1, (x - left - 42.0) / Math.max(1, panelWidth - 76))));
            input.setText(SettingsUi.hex((color & ~(255 << shifts[channel])) | value << shifts[channel]));
        } catch (IllegalArgumentException e) { error = "Use #RRGGBB or #AARRGGBB"; }
    }
    @Override protected void mouseClicked(int x, int y, int b) {
        input.mouseClicked(x, y, b);
        if (b == 0 && config instanceof ConfigColor && x >= left + 42 && x < left + panelWidth - 34 && y >= 94 && y < 190) {
            channel = (y - 94) / 24; slide(x); return;
        }
        super.mouseClicked(x, y, b);
    }
    @Override protected void mouseDragged(int x, int y, int b, long elapsed) { if (channel >= 0) slide(x); }
    @Override protected void mouseReleased(int x, int y, int b) { channel = -1; super.mouseReleased(x, y, b); }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) super.keyPressed(c, key);
        else if (key == Keyboard.KEY_RETURN) buttonClicked(new ButtonWidget(1, 0, 0, ""));
        else input.keyPressed(c, key);
    }
    @Override public void tick() { input.tick(); }
}
