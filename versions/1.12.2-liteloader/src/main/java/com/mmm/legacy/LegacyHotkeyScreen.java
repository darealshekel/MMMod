package com.mmm.legacy;

import com.mmm.hotkey.MmmHotkey;
import com.mmm.legacy.settings.BoardField;
import com.mmm.legacy.settings.HotkeyEditorScreen;
import com.mmm.legacy.settings.SettingsUi;
import com.mmm.ui.ToolDirectory;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Browse registered bindings; the chord editor owns capture and persistence. */
final class LegacyHotkeyScreen extends LegacyScreen {
    private final List<MmmHotkey> bindings = LegacySettingsModel.hotkeys();
    private List<MmmHotkey> matches;
    private BoardField search;
    private int offset, capacity;

    LegacyHotkeyScreen(Screen parent) { super(parent, ToolDirectory.HOTKEYS.label); }

    @Override public void init() {
        super.init();
        BoardField previous = search;
        search = new BoardField(50, textRenderer, left + 1, 39, panelWidth - 2, 18);
        search.setMaxLength(128);
        if (previous != null) {
            search.setText(previous.getText()); search.setFocused(previous.isFocused());
            search.setCursor(previous.getCursor()); search.setSelectionEnd(previous.getSelectionEnd());
        }
        rebuild();
    }

    private void rebuild() {
        buttons.removeIf(b -> b.id != 900);
        matches = bindings.stream().filter(k -> SettingsUi.matches(k.getPrettyName() + " " + k.getComment()
                + " " + k.getStorageString(), search.getText())).toList();
        int cols = panelWidth >= 460 ? 2 : 1, span = (panelWidth + 4) / cols;
        capacity = Math.max(1, (height - 111) / 34) * cols;
        offset = Math.max(0, Math.min(offset, Math.max(0, (matches.size() - 1) / capacity * capacity)));
        for (int i = 0; i < capacity && offset + i < matches.size(); i++) {
            var key = matches.get(offset + i);
            addButton(new LegacyMenuButton(100 + i, left + i % cols * span, 78 + i / cols * 34, span - 4, 30,
                    key.getPrettyName(), key.getStorageString().isEmpty() ? "Unassigned" : key.getStorageString().replace(',', '+'), null, false));
        }
        button(1, left + panelWidth - 52, height - 26, 24, "<").active = offset > 0;
        button(2, left + panelWidth - 24, height - 26, 24, ">").active = offset + capacity < matches.size();
    }

    @Override protected void buttonClicked(ButtonWidget b) {
        if (b.id == 900) super.buttonClicked(b);
        else if (b.id >= 100) minecraft.openScreen(new HotkeyEditorScreen(this, matches.get(offset + b.id - 100), bindings));
        else { offset += b.id == 1 ? -capacity : capacity; rebuild(); }
    }
    @Override public void render(int mx, int my, float delta) {
        shell(); search.render();
        if (search.getText().isEmpty() && !search.isFocused()) fit("Search bindings", left + 5, 44, panelWidth - 10, BoardArt.MUTED);
        section("Bindings / " + matches.size(), 64);
        if (matches.isEmpty()) fit("No matching bindings", left + 6, 85, panelWidth - 12, BoardArt.MUTED);
        else fit((offset + 1) + "-" + Math.min(offset + capacity, matches.size()), left, height - 20, panelWidth - 60, BoardArt.MUTED);
        super.render(mx, my, delta);
    }
    @Override protected void mouseClicked(int x, int y, int b) { search.mouseClicked(x, y, b); super.mouseClicked(x, y, b); }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) { super.keyPressed(c, key); return; }
        if (!search.isFocused() && !Character.isISOControl(c)) search.setFocused(true);
        if (search.keyPressed(c, key)) { offset = 0; rebuild(); }
    }
    @Override public void handleMouse() {
        super.handleMouse(); int wheel = Mouse.getEventDWheel();
        if (wheel != 0) { offset += wheel > 0 ? -capacity : capacity; rebuild(); }
    }
    @Override public void tick() { search.tick(); }
}
