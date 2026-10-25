package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.hotkey.MmmHotkey;
import java.util.LinkedHashSet;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

final class LegacyHotkeyScreen extends LegacyScreen {
    private final MmmHotkey hotkey;
    private final LinkedHashSet<String> chord = new LinkedHashSet<>();
    LegacyHotkeyScreen(Screen parent, MmmHotkey hotkey) { super(parent, hotkey.getPrettyName()); this.hotkey = hotkey; }
    @Override public void init() { super.init(); button(1, left, 100, 100, "Apply"); }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) { hotkey.setStorageString(""); Configs.saveToFile(); minecraft.openScreen(parent); return; }
        if (!Keyboard.isRepeatEvent()) chord.add(Keyboard.getKeyName(key));
    }
    @Override protected void buttonClicked(ButtonWidget button) {
        if (button.id == 1) { hotkey.setStorageString(String.join("+", chord)); Configs.saveToFile(); minecraft.openScreen(parent); }
        else super.buttonClicked(button);
    }
    @Override public void render(int x, int y, float delta) { shell(); fit(String.join(" + ", chord), left, 65, panelWidth, 0xFFFFFFFF); super.render(x, y, delta); }
}
