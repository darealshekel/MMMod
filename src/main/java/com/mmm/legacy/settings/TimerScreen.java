package com.mmm.legacy.settings;

import com.mmm.legacy.LegacyScreen;
import com.mmm.timer.MmmTimerState;
import com.mmm.util.UiFormat;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

public final class TimerScreen extends LegacyScreen {
    private TextFieldWidget duration;
    private String status = "";
    private boolean confirmReset;
    public TimerScreen(Screen parent) { super(parent, "Block Timer"); }
    @Override public void init() {
        String value = duration == null ? Long.toString(MmmTimerState.getDurationMs() / 1000) + "s" : duration.getText();
        super.init(); duration = field(1, 84, value);
        int w = (panelWidth - 12) / 3;
        button(1, left, 115, w, "Set Duration");
        button(2, left + w + 6, 115, w, MmmTimerState.isPaused() ? "Resume" : "Start");
        button(3, left + (w + 6) * 2, 115, w, "Pause");
        button(4, left, 142, w, "Stop");
        button(5, left + w + 6, 142, w, confirmReset ? "Confirm Reset" : "Reset");
        button(6, left + (w + 6) * 2, 142, w, "Results");
        updateButtons();
    }
    private void updateButtons() {
        for (ButtonWidget b : buttons) {
            if (b.id == 2) { b.active = minecraft.world != null && minecraft.player != null && !MmmTimerState.isRunning(); b.message = MmmTimerState.isPaused() ? "Resume" : "Start"; }
            if (b.id == 1) b.active = !MmmTimerState.isRunning() && !MmmTimerState.isPaused();
            if (b.id == 3) b.active = MmmTimerState.isRunning();
            if (b.id == 4) b.active = MmmTimerState.isRunning() || MmmTimerState.isPaused();
        }
    }
    @Override protected void buttonClicked(ButtonWidget b) {
        if (b.id == 1) {
            try {
                MmmTimerState.setDuration(SettingsUi.duration(duration.getText())); MmmTimerState.save(); status = "Duration saved";
            } catch (IllegalArgumentException e) { status = "Duration must be between 1s and 24h"; }
        } else if (b.id == 2) { MmmTimerState.start(null); confirmReset = false; }
        else if (b.id == 3) MmmTimerState.pause();
        else if (b.id == 4) MmmTimerState.stop();
        else if (b.id == 5) { if (confirmReset) { MmmTimerState.reset(); confirmReset = false; } else confirmReset = true; init(); }
        else if (b.id == 6) minecraft.openScreen(new TimerResultsScreen(this));
        else super.buttonClicked(b);
        updateButtons();
    }
    @Override public void render(int x, int y, float delta) {
        shell();
        String state = MmmTimerState.isRunning() ? "Running" : MmmTimerState.isPaused() ? "Paused" : MmmTimerState.isExpired() ? "Finished" : "Stopped";
        fit(MmmTimerState.formatTime(MmmTimerState.getRemainingMs()) + " / " + state, left, 47, panelWidth, 0xFFFFFFFF);
        fit(UiFormat.formatBlocks(MmmTimerState.getBlocksBroken()), left, 64, panelWidth, 0xFFBBBBBB);
        duration.render(); fit(status, left, 176, panelWidth, 0xFFFFB84D); super.render(x, y, delta);
    }
    @Override public void tick() { duration.tick(); updateButtons(); }
    @Override protected void keyPressed(char c, int key) { if (key == Keyboard.KEY_ESCAPE) super.keyPressed(c, key); else duration.keyPressed(c, key); }
    @Override protected void mouseClicked(int x, int y, int b) { duration.mouseClicked(x, y, b); super.mouseClicked(x, y, b); }
}
