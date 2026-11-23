package com.mmm.legacy.settings;

import com.mmm.config.Configs;
import com.mmm.config.value.*;
import com.mmm.legacy.LegacyScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import static com.mmm.ui.BoardEditorStyle.*;

public final class ValueEditorScreen extends LegacyScreen {
    private final IConfigBase config;
    private TextFieldWidget input;
    private String error = "";
    private int channel = -1;
    private static final String[] CHANNELS = {"Red", "Green", "Blue", "Alpha"};
    private static final int[] SHIFTS = {16, 8, 0, 24};
    @Override protected int maxContentWidth() { return 520; }
    public ValueEditorScreen(Screen parent, IConfigBase config) { super(parent, config.getPrettyName()); this.config = config; }
    @Override public void init() {
        String value = input == null ? config.getStringValue() : input.getText();
        super.init(); buttons.get(0).message = "Cancel";
        input = new BoardField(1, textRenderer, left + 2, 65,
                panelWidth - (config instanceof ConfigColor ? 34 : 4), 20);
        input.setMaxLength(256); input.setText(value); input.setFocused(true);
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
        if (config instanceof ConfigColor) fill(0, 0, width, height, FLOOR);
        shell(); fit(config.getComment(), left, 47, panelWidth, 0xFFBBBBBB); input.render();
        if (config instanceof ConfigColor) {
            int color = 0;
            try { color = SettingsUi.color(input.getText()); } catch (IllegalArgumentException ignored) {}
            int swatch = left + panelWidth - 24;
            fill(swatch, 64, swatch + 23, 86, LINE);
            for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++)
                fill(swatch + 2 + col * 5, 66 + row * 4, swatch + 7 + col * 5, 70 + row * 4,
                        (row + col) % 2 == 0 ? 0xffbbbbbb : 0xff555555);
            fill(swatch + 2, 66, swatch + 22, 82, color);
            for (int i = 0; i < 4; i++) {
                int py = 94 + i * 24, amount = color >>> SHIFTS[i] & 255;
                text(CHANNELS[i], left, py + 5, MUTED);
                int x1 = left + 42, x2 = left + panelWidth - 34;
                int low = color & ~(255 << SHIFTS[i]), high = low | (255 << SHIFTS[i]);
                ramp(x1, py + 3, x2, py + 13, i == 3 ? FLOOR : low, high);
                int marker = x1 + Math.round((x2 - x1 - 1) * amount / 255f);
                fill(marker - 1, py + 1, marker + 2, py + 15, FLOOR);
                fill(marker, py + 1, marker + 1, py + 15, TEXT);
                text(Integer.toString(amount), left + panelWidth - 28, py + 5, TEXT);
            }
        } else {
            String hint = config instanceof IConfigInteger n ? n.getMinIntegerValue() + " - " + n.getMaxIntegerValue()
                    : config instanceof IConfigDouble n ? n.getMinDoubleValue() + " - " + n.getMaxDoubleValue() : "";
            fit(hint, left, 94, panelWidth, 0xFFBBBBBB);
        }
        fit(error, left, height - 16, panelWidth, 0xFFFF8888);
        super.render(x, y, delta);
    }
    // Interpolate at framebuffer resolution; never sample a scaled gradient texture.
    private static void ramp(int x, int y, int right, int bottom, int low, int high) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glBegin(GL11.GL_QUADS);
            rgb(low); GL11.glVertex2i(x, y); GL11.glVertex2i(x, bottom);
            rgb(high); GL11.glVertex2i(right, bottom); GL11.glVertex2i(right, y);
            GL11.glEnd();
        } finally { GL11.glPopAttrib(); }
    }
    private static void rgb(int argb) {
        GL11.glColor3ub((byte)(argb >>> 16), (byte)(argb >>> 8), (byte)argb);
    }
    private void slide(int x) {
        try {
            int color = SettingsUi.color(input.getText());
            int value = (int) Math.round(255 * Math.max(0, Math.min(1, (x - left - 42.0) / Math.max(1, panelWidth - 77))));
            error = "";
            input.setText(SettingsUi.hex((color & ~(255 << SHIFTS[channel])) | value << SHIFTS[channel]));
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
