package com.mmm.legacy;

import com.mmm.sync.WebsiteLinkManager;
import com.mmm.sync.CloudSyncManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

final class LegacyLinkScreen extends LegacyScreen {
    private TextFieldWidget code;
    private ButtonWidget submit;
    private String browserError = "";
    LegacyLinkScreen(Screen parent) { super(parent, "Website account"); }
    @Override public void init() {
        String previous = code == null ? "" : code.getText();
        super.init();
        code = field(1, 91, previous); code.setFocused(true);
        int buttonWidth = (panelWidth - 8) / 2;
        button(2, left, 119, buttonWidth, "Open Website");
        submit = button(1, left + buttonWidth + 8, 119, buttonWidth, "Link Account");
        refresh();
    }
    private boolean busy() {
        var state = WebsiteLinkManager.getState().status();
        return state == WebsiteLinkManager.Status.SUBMITTING || state == WebsiteLinkManager.Status.QUEUED;
    }
    private void refresh() { submit.active = !busy() && code.getText().replaceAll("[^A-Za-z0-9]", "").length() >= 6; }
    @Override protected void buttonClicked(ButtonWidget button) {
        if (button.id == 1) {
            refresh();
            if (submit.active) WebsiteLinkManager.claimCode(code.getText());
            refresh();
        } else if (button.id == 2) {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://www.mmmaniacs.com/login"));
                browserError = "";
            } catch (Exception error) {
                browserError = "Open https://www.mmmaniacs.com/login in your browser.";
            }
        } else super.buttonClicked(button);
    }
    @Override public void render(int x, int y, float delta) {
        shell();
        boolean linked = WebsiteLinkManager.isCurrentPlayerLinked();
        String saved = linked ? "Linked as " + WebsiteLinkManager.getPersistedUsername()
                : WebsiteLinkManager.hasPersistedLink() ? "Saved link belongs to " + WebsiteLinkManager.getPersistedUsername()
                + "; link your current account." : "Not linked to the website";
        fit(saved, left, 45, panelWidth, linked ? 0xFF69D59A : 0xFFFFBA69);
        fit(CloudSyncManager.getStatusSummary(), left, 60, panelWidth, 0xFFBBBBBB);
        fit("Generate a mod link code on the website, then enter it below.", left, 77, panelWidth, 0xFFF5F5F5);
        code.render();
        String detail = browserError.isEmpty() ? WebsiteLinkManager.getState().detail() : browserError;
        if (detail.isBlank()) detail = CloudSyncManager.getStatusDetail();
        var lines = textRenderer.split(detail, panelWidth);
        for (int i = 0; i < lines.size() && 151 + i * 12 < height - 12; i++) text(lines.get(i), left, 151 + i * 12, 0xFFCCCCCC);
        refresh();
        super.render(x, y, delta);
    }
    @Override protected void mouseClicked(int x, int y, int button) { code.mouseClicked(x, y, button); super.mouseClicked(x, y, button); }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) super.keyPressed(c, key);
        else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) buttonClicked(submit);
        else { code.keyPressed(c, key); refresh(); }
    }
    @Override public void tick() { code.tick(); refresh(); }
}
