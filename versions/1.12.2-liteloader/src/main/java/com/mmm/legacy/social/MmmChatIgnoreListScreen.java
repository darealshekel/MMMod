package com.mmm.legacy.social;

import com.mmm.config.Configs;
import com.mmm.legacy.LegacyScreen;
import com.mmm.social.MmmChatIgnoreList;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import com.mmm.legacy.settings.BoardField;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class MmmChatIgnoreListScreen extends LegacyScreen {
    private TextFieldWidget username;
    private int offset;
    private String status = "";
    private List<String> entries = List.of();

    public MmmChatIgnoreListScreen(Screen parent) { super(parent, "Ignored Players"); }

    @Override protected int maxContentWidth() { return 580; }
    @Override public void init() {
        super.init();
        String draft = username == null ? "" : username.getText();
        username = new BoardField(1, textRenderer, left + 4, 75, Math.max(20, panelWidth - 82), 20);
        username.setMaxLength(16);
        username.setText(draft);
        username.setFocused(true);
        rebuild();
    }

    private int rows() { return Math.max(0, (height - 165) / 24); }

    private void rebuild() {
        entries = MmmChatIgnoreList.entries();
        offset = Math.max(0, Math.min(offset, Math.max(0, entries.size() - rows())));
        buttons.clear();
        button(0, left + panelWidth - 60, 5, 60, "Done");
        button(1, left + panelWidth - 70, 74, 70, "Ignore");
        int half = (panelWidth - 4) / 2;
        button(2, left, 42, half, "Censor: " + (Configs.Generic.CENSOR_MMM_CHAT.getBooleanValue() ? "ON" : "OFF"));
        button(3, left + half + 4, 42, half, "Chat: " + (Configs.Generic.SHOW_MMM_CHAT_MESSAGES.getBooleanValue() ? "ON" : "OFF"));
        for (int index = 0; index < rows() && offset + index < entries.size(); index++)
            button(100 + index, left + panelWidth - 24, 118 + index * 24, 24, "X");
    }

    @Override public void render(int mouseX, int mouseY, float delta) {
        shell();
        username.render();
        section("Ignored players / " + entries.size(), 103);
        if (entries.isEmpty()) fit("No ignored players", left + 7, 124, panelWidth - 14, com.mmm.ui.BoardEditorStyle.MUTED);
        for (int index = 0; index < rows() && offset + index < entries.size(); index++) {
            int y = 118 + index * 24;
            recordRow(y, 21, mouseX >= left && mouseX < left + panelWidth && mouseY >= y && mouseY < y + 21);
            fit(entries.get(offset + index), left + 7, y + 6, panelWidth - 38, com.mmm.ui.BoardEditorStyle.TEXT);
        }
        textRenderer.draw(textRenderer.trim(status, panelWidth), left, height - 22, 0xFFAAAAAA, false);
        super.render(mouseX, mouseY, delta);
        for (int index = 0; index < rows() && offset + index < entries.size(); index++) {
            int y = 118 + index * 24;
            if (mouseX >= left + panelWidth - 24 && mouseX < left + panelWidth && mouseY >= y && mouseY < y + 20)
                renderTooltip("Unignore " + entries.get(offset + index), mouseX, mouseY);
        }
    }

    private void addPlayer() {
        String name = username.getText().trim();
        if (!MmmChatIgnoreList.isValidUsername(name)) status = "Enter a valid Minecraft username.";
        else if (!MmmChatIgnoreList.add(name)) status = name + " is already ignored.";
        else { status = "Ignoring " + name + "."; username.setText(""); }
        rebuild();
        username.setFocused(true);
    }

    @Override protected void buttonClicked(ButtonWidget button) {
        if (button.id == 0) minecraft.openScreen(parent);
        else if (button.id == 1) addPlayer();
        else if (button.id == 2 || button.id == 3) {
            var config = button.id == 2 ? Configs.Generic.CENSOR_MMM_CHAT : Configs.Generic.SHOW_MMM_CHAT_MESSAGES;
            config.setBooleanValue(!config.getBooleanValue());
            Configs.saveToFile();
            rebuild();
        } else if (button.id >= 100 && offset + button.id - 100 < entries.size()) {
            String name = entries.get(offset + button.id - 100);
            MmmChatIgnoreList.remove(name);
            status = "Showing messages from " + name + " again.";
            rebuild();
        }
    }

    @Override protected void mouseClicked(int x, int y, int button) {
        username.mouseClicked(x, y, button);
        super.mouseClicked(x, y, button);
    }
    @Override protected void keyPressed(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) minecraft.openScreen(parent);
        else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) addPlayer();
        else username.keyPressed(character, key);
    }
    @Override public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) { offset -= Integer.signum(wheel); rebuild(); }
    }
    @Override public void tick() { username.tick(); }
    @Override public boolean shouldPauseGame() { return true; }
}
