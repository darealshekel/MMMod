package com.mmm.legacy.settings;

import com.mmm.config.Configs;
import com.mmm.hotkey.MmmHotkey;
import com.mmm.legacy.LegacyInput;
import com.mmm.legacy.LegacyScreen;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

public final class HotkeyEditorScreen extends LegacyScreen {
    private final MmmHotkey hotkey;
    private final List<MmmHotkey> bindings;
    private final KeyChordDraft draft = new KeyChordDraft();
    private boolean capturing;
    private boolean released;
    public HotkeyEditorScreen(Screen parent, MmmHotkey hotkey, List<MmmHotkey> bindings) {
        super(parent, hotkey.getPrettyName()); this.hotkey = hotkey; this.bindings = bindings; draft.set(hotkey.getStorageString());
    }
    @Override public void init() {
        super.init();
        buttons.get(0).message = "Cancel";
        button(1, left, 78, panelWidth, capturing ? "Press up to two keys..." : draft.label());
        int w = (panelWidth - 12) / 3;
        button(2, left, 108, w, "Unassign");
        button(3, left + w + 6, 108, w, "Default");
        button(4, left + (w + 6) * 2, 108, w, "Apply");
    }
    @Override protected void buttonClicked(ButtonWidget b) {
        if (b.id == 1) { draft.clear(); capturing = true; released = false; init(); }
        else if (b.id == 2) { draft.clear(); capturing = false; init(); }
        else if (b.id == 3) { draft.set(hotkey.getDefaultStorageString()); capturing = false; init(); }
        else if (b.id == 4) { hotkey.setStorageString(draft.storage()); Configs.saveToFile(); minecraft.openScreen(parent); }
        else super.buttonClicked(b);
    }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) { minecraft.openScreen(parent); return; }
        if (capturing && released && !Keyboard.isRepeatEvent()) { draft.add(LegacyInput.keyName(key)); buttons.stream().filter(b -> b.id == 1).forEach(b -> b.message = draft.label()); }
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        boolean overControl = buttons.stream().anyMatch(b -> b.visible && x >= b.x && x < b.x + b.getWidth() && y >= b.y && y < b.y + 20);
        if (capturing && released && !overControl) {
            draft.add("MOUSE_" + (button + 1));
            buttons.stream().filter(b -> b.id == 1).forEach(b -> b.message = draft.label());
            return;
        }
        super.mouseClicked(x, y, button);
    }
    @Override public void tick() {
        if (!released) {
            boolean down = false;
            for (int key = 1; key < Keyboard.KEYBOARD_SIZE; key++) down |= Keyboard.isKeyDown(key);
            if (!down) released = true;
        }
    }
    @Override public void render(int x, int y, float delta) {
        shell(); fit(hotkey.getComment(), left, 48, panelWidth, 0xFFBBBBBB);
        int row = 145;
        for (MmmHotkey binding : bindings) if (binding != hotkey && draft.conflicts(binding.getStorageString())) {
            fit("Also assigned: " + binding.getPrettyName(), left, row, panelWidth, 0xFFFFB84D); row += 12;
            if (row > height - 20) break;
        }
        super.render(x, y, delta);
    }
}
