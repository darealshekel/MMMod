package com.mmm.legacy.social;

import com.mmm.config.Configs;
import com.mmm.legacy.LegacyScreen;
import com.mmm.social.MmmChatIgnoreList;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class MmmChatIgnoreListScreen extends LegacyScreen {
    private TextFieldWidget username;
    private int offset;
    private String status = "";
    private List<String> entries = List.of();

    public MmmChatIgnoreListScreen(Screen parent) { super(parent, "MMM Chat - Ignored Players"); }

    @Override public void init() {
        super.init();
        String draft = username == null ? "" : username.getText();
        panelWidth = Math.min(580, Math.max(80, width - 24));
        left = (width - panelWidth) / 2;
        username = new TextFieldWidget(1, textRenderer, left + 4, 75, Math.max(20, panelWidth - 82), 20);
        username.setMaxLength(16);
        username.setText(draft);
        username.setFocused(true);
        rebuild();
    }

    private int rows() { return Math.max(0, (height - 150) / 24); }

    private void rebuild() {
        entries = MmmChatIgnoreList.entries();
        offset = Math.max(0, Math.min(offset, Math.max(0, entries.size() - rows())));
        buttons.clear();
        addButton(new ButtonWidget(0, left + panelWidth - 60, 12, 60, 20, "Done"));
        addButton(new ButtonWidget(1, left + panelWidth - 70, 74, 70, 20, "Ignore"));
        int half = (panelWidth - 4) / 2;
        addButton(new ButtonWidget(2, left, 42, half, 20, "Censor: " + (Configs.Generic.CENSOR_MMM_CHAT.getBooleanValue() ? "ON" : "OFF")));
        addButton(new ButtonWidget(3, left + half + 4, 42, half, 20, "Chat: " + (Configs.Generic.SHOW_MMM_CHAT_MESSAGES.getBooleanValue() ? "ON" : "OFF")));
        for (int index = 0; index < rows() && offset + index < entries.size(); index++)
            addButton(new ButtonWidget(100 + index, left + panelWidth - 24, 103 + index * 24, 24, 20, "X"));
    }

    @Override public void render(int mouseX, int mouseY, float delta) {
        fill(0, 0, width, height, 0xEA080808);
        textRenderer.draw(textRenderer.trim("MMM Chat - Ignored Players", Math.max(1, panelWidth - 68)), left, 18, 0xFFF5F5F5, false);
        fill(left, 36, left + panelWidth, 37, 0xFFE00000);
        username.render();
        if (entries.isEmpty()) textRenderer.draw("No ignored players", left + 4, 109, 0xFFAAAAAA, false);
        for (int index = 0; index < rows() && offset + index < entries.size(); index++)
            textRenderer.draw(textRenderer.trim(entries.get(offset + index), panelWidth - 34), left + 4, 109 + index * 24, 0xFFFFFFFF, false);
        textRenderer.draw(textRenderer.trim(status, panelWidth), left, height - 22, 0xFFAAAAAA, false);
        super.render(mouseX, mouseY, delta);
        for (int index = 0; index < rows() && offset + index < entries.size(); index++) {
            int y = 103 + index * 24;
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
