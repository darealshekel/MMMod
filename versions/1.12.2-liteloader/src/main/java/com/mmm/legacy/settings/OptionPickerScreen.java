package com.mmm.legacy.settings;

import com.mmm.config.Configs;
import com.mmm.config.value.*;
import com.mmm.legacy.LegacyScreen;
import java.util.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Mouse;

public final class OptionPickerScreen extends LegacyScreen {
    private final ConfigOptionList config;
    private final List<IConfigOptionListEntry> entries = new ArrayList<>();
    private int offset;
    public OptionPickerScreen(Screen parent, ConfigOptionList config) {
        super(parent, config.getPrettyName()); this.config = config;
        var next = config.getOptionListValue();
        Set<String> seen = new HashSet<>();
        while (seen.add(next.getStringValue())) { entries.add(next); next = next.cycle(true); }
    }
    private int capacity() { return Math.max(1, (height - 78) / 24); }
    @Override public void init() {
        super.init(); offset = SettingsUi.clampScroll(offset, entries.size(), capacity());
        for (int i = 0; i < capacity() && offset + i < entries.size(); i++) {
            var entry = entries.get(offset + i);
            button(100 + i, left, 48 + i * 24, panelWidth, (entry == config.getOptionListValue() ? "[x] " : "[ ] ") + entry.getDisplayName());
        }
    }
    @Override protected void buttonClicked(ButtonWidget b) {
        if (b.id >= 100 && b.id < 100 + capacity()) {
            config.setOptionListValue(entries.get(offset + b.id - 100)); Configs.saveToFile(); minecraft.openScreen(parent);
        } else super.buttonClicked(b);
    }
    @Override public void render(int x, int y, float delta) { shell(); super.render(x, y, delta); }
    @Override public void handleMouse() { super.handleMouse(); int wheel = Mouse.getEventDWheel(); if (wheel != 0) { offset += wheel > 0 ? -1 : 1; init(); } }
}
