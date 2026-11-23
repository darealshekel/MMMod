package com.mmm.scoreboard;

import com.mmm.config.Configs;
import java.io.IOException;
import net.minecraft.client.gui.screen.Screen;
import com.mmm.legacy.LegacyScreen;
import com.mmm.ui.BoardEditorStyle;

abstract class ScoreboardBaseScreen extends LegacyScreen {
    protected String status = "";
    ScoreboardBaseScreen(Screen parent, String title) { super(parent, title); }
    @Override protected int maxContentWidth() { return 640; }
    @Override protected void shell() {
        super.shell();
        fit(status, left, height - 12, panelWidth, BoardEditorStyle.MUTED);
    }
    protected void changed() { Configs.saveToFile(); }
    protected void io(IoAction action) {
        try { status = action.run(); } catch (IOException e) { status = e.getMessage(); }
    }
    @FunctionalInterface protected interface IoAction { String run() throws IOException; }
    @Override public void removed() { Configs.saveToFile(); super.removed(); }
    @Override public boolean shouldPauseGame() { return false; }
}

