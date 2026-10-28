package com.mmm.scoreboard;

import com.mmm.config.Configs;
import com.mmm.legacy.LegacyDrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Mouse;

public final class ScoreboardMoveScreen extends ScoreboardBaseScreen {
    private boolean dragging;
    private int offsetX, offsetY;
    public ScoreboardMoveScreen(Screen parent) { super(parent, "Move Scoreboard"); }
    @Override public void init() {
        super.init();
        button(1, 8, 8, 22, "-");
        button(2, 34, 8, 22, "+");
        button(3, 60, 8, 54, "Reset");
    }
    @Override public void render(int x, int y, float delta) {
        fill(0, 0, width, height, 0x22050505);
        if (ScoreboardHudRenderer.hasSidebar(minecraft)) {
            var objective = ScoreboardService.getSidebarObjective(minecraft).orElse(null);
            var context = new LegacyDrawContext();
            var bounds = ScoreboardHudRenderer.render(context, minecraft, objective);
            context.drawBorder(bounds.left() - 1, bounds.top() - 1, bounds.width() + 2, bounds.height() + 2, 0xFFE00000);
        } else text("No sidebar scoreboard", Math.max(8, width / 2 - 60), height / 2, 0xFFCCCCCC);
        text(Math.round(Configs.Generic.SCOREBOARD_SCALE.getDoubleValue() * 100) + "%", 120, 14, 0xFFFFFFFF);
        super.render(x, y, delta);
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        for (ButtonWidget control : buttons)
            if (control.visible && x >= control.x && x < control.x + control.getWidth()
                    && y >= control.y && y < control.y + 20) { super.mouseClicked(x, y, button); return; }
        if (button == 0 && ScoreboardHudRenderer.hasSidebar(minecraft)) {
            var bounds = ScoreboardHudRenderer.getBounds(minecraft, ScoreboardService.getSidebarObjective(minecraft).orElse(null));
            if (bounds.contains(x, y)) { dragging = true; offsetX = x - bounds.left(); offsetY = y - bounds.top(); }
        }
    }
    @Override protected void mouseDragged(int x, int y, int button, long time) {
        if (dragging) ScoreboardHudRenderer.setPosition(minecraft, ScoreboardService.getSidebarObjective(minecraft).orElse(null), x - offsetX, y - offsetY);
    }
    @Override protected void mouseReleased(int x, int y, int button) {
        if (button == 0) { dragging = false; changed(); }
        super.mouseReleased(x, y, button);
    }
    @Override protected void buttonClicked(ButtonWidget button) {
        if (button.id == 1 || button.id == 2) scale(button.id == 1 ? -0.05 : 0.05);
        else if (button.id == 3) { ScoreboardHudRenderer.resetPosition(); Configs.Generic.SCOREBOARD_SCALE.resetToDefault(); changed(); }
        else super.buttonClicked(button);
    }
    @Override public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scale(wheel > 0 ? 0.05 : -0.05);
    }
    private void scale(double delta) {
        Configs.Generic.SCOREBOARD_SCALE.setDoubleValue(Math.max(0.5, Math.min(2, Math.round((Configs.Generic.SCOREBOARD_SCALE.getDoubleValue() + delta) * 100) / 100.0)));
        changed();
    }
}
