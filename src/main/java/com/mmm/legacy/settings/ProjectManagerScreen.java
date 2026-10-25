package com.mmm.legacy.settings;

import com.mmm.config.Configs;
import com.mmm.config.Configs.ProjectEntry;
import com.mmm.legacy.LegacyScreen;
import com.mmm.util.UiFormat;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ProjectManagerScreen extends LegacyScreen {
    private int selected, offset;
    private TextFieldWidget name;
    private boolean confirm;
    private String status = "";
    public ProjectManagerScreen(Screen parent) { super(parent, "Projects"); }
    private ProjectEntry selected() { return Configs.PROJECTS.isEmpty() ? null : Configs.PROJECTS.get(Math.max(0, Math.min(selected, Configs.PROJECTS.size() - 1))); }
    private int capacity() { return Math.max(1, (height - 178) / 24); }
    @Override public void init() {
        String draft = name == null ? null : name.getText();
        super.init();
        offset = SettingsUi.clampScroll(offset, Configs.PROJECTS.size(), capacity());
        ProjectEntry entry = selected();
        for (int i = 0; i < capacity() && offset + i < Configs.PROJECTS.size(); i++) {
            ProjectEntry item = Configs.PROJECTS.get(offset + i);
            button(100 + i, left, 45 + i * 24, panelWidth,
                    (item.id.equals(Configs.activeProjectId) ? "[Active] " : "") + item.name + " / " + UiFormat.formatBlocks(item.progress));
        }
        int y = height - 122;
        name = field(1, y, draft != null ? draft : entry == null ? "" : entry.name); name.setMaxLength(64);
        int w = (panelWidth - 12) / 3;
        button(1, left, y + 28, w, "Apply").active = entry != null;
        button(2, left + w + 6, y + 28, w, "Set Active").active = entry != null && !entry.id.equals(Configs.activeProjectId);
        button(3, left + 2 * (w + 6), y + 28, w, "New Project");
        button(4, left, y + 54, w, confirm ? "Confirm Delete" : "Remove").active = entry != null && Configs.PROJECTS.size() > 1;
        button(5, left + w + 6, y + 54, w, "<").active = offset > 0;
        button(6, left + 2 * (w + 6), y + 54, w, ">").active = offset + capacity() < Configs.PROJECTS.size();
    }
    private boolean apply() {
        if (selected() == null || name.getText().trim().isEmpty()) { status = "Project name cannot be empty"; return false; }
        selected().name = name.getText().trim(); return true;
    }
    @Override protected void buttonClicked(ButtonWidget b) {
        if (b.id >= 100 && b.id < 100 + capacity()) { selected = offset + b.id - 100; name = null; confirm = false; status = ""; init(); }
        else if (b.id == 1 || b.id == 2) {
            if (apply()) { if (b.id == 2) Configs.activeProjectId = selected().id; Configs.saveToFile(); status = "Saved"; init(); }
        } else if (b.id == 3) {
            ProjectEntry entry = Configs.createProject("Project " + (Configs.PROJECTS.size() + 1));
            selected = Configs.PROJECTS.indexOf(entry); Configs.activeProjectId = entry.id;
            offset = Math.max(0, selected - capacity() + 1); name = null; confirm = false; Configs.saveToFile(); init();
        } else if (b.id == 4 && Configs.PROJECTS.size() > 1) {
            if (!confirm) { confirm = true; init(); return; }
            ProjectEntry entry = selected(); Configs.PROJECTS.remove(entry);
            if (entry.id.equals(Configs.activeProjectId)) Configs.activeProjectId = Configs.PROJECTS.get(0).id;
            selected = Math.min(selected, Configs.PROJECTS.size() - 1); name = null; confirm = false; Configs.saveToFile(); init();
        } else if (b.id == 5 || b.id == 6) { offset += b.id == 5 ? -capacity() : capacity(); init(); }
        else super.buttonClicked(b);
    }
    @Override public void render(int x, int y, float delta) {
        shell(); name.render();
        fit(status, left, height - 22, panelWidth, 0xFFBBBBBB);
        super.render(x, y, delta);
    }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) super.keyPressed(c, key);
        else { name.keyPressed(c, key); confirm = false; }
    }
    @Override protected void mouseClicked(int x, int y, int b) { name.mouseClicked(x, y, b); super.mouseClicked(x, y, b); }
    @Override public void tick() { name.tick(); }
    @Override public void handleMouse() { super.handleMouse(); int wheel = Mouse.getEventDWheel(); if (wheel != 0) { offset += wheel > 0 ? -1 : 1; init(); } }
}
