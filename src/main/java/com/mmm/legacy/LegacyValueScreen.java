package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.config.value.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

final class LegacyValueScreen extends LegacyScreen {
    private final IConfigBase config;
    private TextFieldWidget input;
    private String error = "";
    private int inputY;

    LegacyValueScreen(Screen parent, IConfigBase config) { super(parent, config.getPrettyName()); this.config = config; }
    @Override public void init() {
        String draft = input == null ? config.getStringValue() : input.getText();
        super.init();
        buttons.stream().filter(b -> b.id == 900).forEach(b -> b.message = "Cancel");
        inputY = 52 + Math.min(3, textRenderer.split(config.getComment(), panelWidth).size()) * 12;
        input = field(1, inputY, draft); input.setFocused(true);
        button(1, left, inputY + 46, 90, "Apply");
        button(2, left + 98, inputY + 46, 100, "Default");
    }
    @Override protected void buttonClicked(ButtonWidget button) {
        if (button.id == 2) { input.setText(config.getDefaultStringValue()); error = ""; return; }
        if (button.id != 1) { super.buttonClicked(button); return; }
        String value = input.getText().trim();
        try {
            if (config instanceof IConfigInteger number) {
                int parsed = Integer.parseInt(value);
                if (parsed < number.getMinIntegerValue() || parsed > number.getMaxIntegerValue()) throw new NumberFormatException();
            }
            if (config instanceof IConfigDouble number) {
                double parsed = Double.parseDouble(value);
                if (!Double.isFinite(parsed) || parsed < number.getMinDoubleValue() || parsed > number.getMaxDoubleValue()) throw new NumberFormatException();
            }
            if (config instanceof ConfigColor && !value.matches("#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new NumberFormatException();
            config.setValueFromString(value); Configs.saveToFile(); minecraft.openScreen(parent);
        } catch (RuntimeException e) { error = "Invalid value. " + formatHint(); }
    }
    private String formatHint() {
        if (config instanceof IConfigInteger n) return "Range: " + n.getMinIntegerValue() + " to " + n.getMaxIntegerValue();
        if (config instanceof IConfigDouble n) return "Range: " + n.getMinDoubleValue() + " to " + n.getMaxDoubleValue();
        if (config instanceof ConfigColor) return "#RRGGBB or #AARRGGBB";
        return "";
    }
    @Override public void render(int x, int y, float delta) {
        shell(); input.render();
        var lines = textRenderer.split(config.getComment(), panelWidth);
        for (int i = 0; i < Math.min(3, lines.size()); i++) text(lines.get(i), left, 46 + i * 12, 0xFFBBBBBB);
        fit(formatHint(), left, inputY + 28, panelWidth, 0xFFBBBBBB);
        fit(error, left, inputY + 76, panelWidth, 0xFFFF8888);
        super.render(x, y, delta);
    }
    @Override protected void keyPressed(char c, int key) { if (key == Keyboard.KEY_ESCAPE) super.keyPressed(c, key); else if (key == Keyboard.KEY_RETURN) buttonClicked(new ButtonWidget(1, 0, 0, "")); else input.keyPressed(c, key); }
    @Override protected void mouseClicked(int x, int y, int button) { input.mouseClicked(x, y, button); super.mouseClicked(x, y, button); }
    @Override public void tick() { input.tick(); }
}
