package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.hud.HudModuleId;
import com.mmm.hud.MiningHudRenderer;
import com.mmm.timer.TimerHudRenderer;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;

final class LegacyHudMoveScreen extends LegacyScreen {
    private HudModuleId selected;
    private int offsetX, offsetY;
    LegacyHudMoveScreen(Screen parent) { super(parent, "Move HUD"); }
    @Override public void render(int mouseX, int mouseY, float delta) {
        LegacyDrawContext context = new LegacyDrawContext();
        for (HudModuleId module : HudModuleId.values()) {
            if (module != HudModuleId.MAIN) TimerHudRenderer.drawModule(context, minecraft, module, true);
            int[] bounds = TimerHudRenderer.getBounds(minecraft, module);
            context.drawBorder(bounds[0], bounds[1], bounds[2] - bounds[0], bounds[3] - bounds[1], module == selected ? 0xFFE00000 : 0xFF888888);
        }
        super.render(mouseX, mouseY, delta);
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (button != 0) return;
        for (HudModuleId module : HudModuleId.values()) {
            int[] b = TimerHudRenderer.getBounds(minecraft, module);
            if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) { selected = module; offsetX = x - b[0]; offsetY = y - b[1]; }
        }
    }
    @Override protected void mouseDragged(int x, int y, int button, long elapsed) {
        if (selected == null || button != 0) return;
        if (selected == HudModuleId.MAIN) {
            int[] b = MiningHudRenderer.getBounds(minecraft);
            Configs.Generic.HUD_ALIGNMENT.setOptionListValue(Configs.HudAlignment.TOP_LEFT);
            Configs.Generic.HUD_X.setIntegerValue((int) Math.round(Math.max(0, x - offsetX) * 820.0 / Math.max(1, width - (b[2] - b[0]))));
            Configs.Generic.HUD_Y.setIntegerValue((int) Math.round(Math.max(0, y - offsetY) * 460.0 / Math.max(1, height - (b[3] - b[1]))));
        } else TimerHudRenderer.setModulePosition(minecraft, selected, x - offsetX, y - offsetY);
    }
    @Override protected void mouseReleased(int x, int y, int button) { super.mouseReleased(x, y, button); Configs.saveToFile(); }
    @Override public void handleMouse() { super.handleMouse(); int wheel = Mouse.getEventDWheel(); if (wheel != 0 && selected != null) { if (selected == HudModuleId.MAIN) Configs.Generic.HUD_SCALE.setDoubleValue(Configs.Generic.HUD_SCALE.getDoubleValue() + (wheel > 0 ? 0.05 : -0.05)); else TimerHudRenderer.adjustScale(selected, wheel > 0 ? 0.05 : -0.05); } }
    @Override public void removed() { Configs.saveToFile(); super.removed(); }
}
