package com.mmm.legacy.settings;

import com.mmm.config.Configs;
import com.mmm.legacy.LegacyDrawContext;
import com.mmm.legacy.LegacyScreen;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class HudEditorScreen extends LegacyScreen {
    private String selectedId = "mmm:MAIN";
    private boolean dragging;
    private int dx, dy;
    public HudEditorScreen(Screen parent) { super(parent, "HUD Layout"); }
    private HudModules.Module selected() {
        return HudModules.modules().stream().filter(m -> m.id().equals(selectedId)).findFirst().orElse(null);
    }
    @Override public void init() {
        super.init(); buttons.clear();
        int w = Math.max(28, Math.min(100, (width - 40) / 7));
        int x = (width - w * 7 - 24) / 2, y = height - 26;
        button(1, x, y, w, "<");
        button(2, x + w + 4, y, w, ">");
        button(3, x + 2 * (w + 4), y, w, "-");
        button(4, x + 3 * (w + 4), y, w, "+");
        button(5, x + 4 * (w + 4), y, w, "Visible");
        button(6, x + 5 * (w + 4), y, w, "Reset");
        button(900, x + 6 * (w + 4), y, w, "Done");
    }
    @Override public void render(int x, int y, float delta) {
        LegacyDrawContext context = new LegacyDrawContext();
        fill(0, 0, width, height - 30, 0x50000000);
        for (HudModules.Module module : HudModules.modules()) {
            module.renderPreview(context, minecraft);
            int[] b = module.bounds(minecraft);
            context.drawBorder(b[0], b[1], b[2] - b[0], b[3] - b[1], module.id().equals(selectedId) ? Configs.getMenuColor() : 0xFF949494);
        }
        HudModules.Module selected = selected();
        fill(0, height - 43, width, height, 0xF0050505);
        if (selected != null) {
            fit(selected.label() + (selected.visible() ? " / Visible" : " / Hidden"), 8, height - 39, width - 16, 0xFFFFFFFF);
            buttons.stream().filter(b -> b.id == 5).forEach(b -> b.message = selected.visible() ? "Hide" : "Show");
        }
        super.render(x, y, delta);
    }
    @Override protected void buttonClicked(ButtonWidget b) {
        HudModules.Module module = selected();
        if (b.id == 1 || b.id == 2) {
            List<HudModules.Module> modules = HudModules.modules();
            int index = 0;
            for (int i = 0; i < modules.size(); i++) if (modules.get(i).id().equals(selectedId)) index = i;
            selectedId = modules.get(Math.floorMod(index + (b.id == 1 ? -1 : 1), modules.size())).id();
        } else if (module != null && b.id == 3) module.scale(-0.05);
        else if (module != null && b.id == 4) module.scale(0.05);
        else if (module != null && b.id == 5) module.toggle();
        else if (module != null && b.id == 6) module.reset();
        else super.buttonClicked(b);
    }
    @Override protected void mouseClicked(int x, int y, int b) {
        if (y >= height - 43) { super.mouseClicked(x, y, b); return; }
        if (b != 0) return;
        List<HudModules.Module> modules = HudModules.modules();
        for (int i = modules.size() - 1; i >= 0; i--) {
            var module = modules.get(i); int[] bounds = module.bounds(minecraft);
            if (x >= bounds[0] && x < bounds[2] && y >= bounds[1] && y < bounds[3]) {
                selectedId = module.id(); dx = x - bounds[0]; dy = y - bounds[1]; dragging = true; break;
            }
        }
    }
    @Override protected void mouseDragged(int x, int y, int b, long elapsed) {
        HudModules.Module module = selected();
        if (!dragging || module == null || b != 0) return;
        int[] bounds = module.bounds(minecraft);
        module.move(minecraft, Math.max(0, Math.min(width - (bounds[2] - bounds[0]), x - dx)), Math.max(0, Math.min(height - (bounds[3] - bounds[1]), y - dy)));
    }
    @Override protected void mouseReleased(int x, int y, int b) { dragging = false; HudModules.Module module = selected(); if (module != null) module.save(); super.mouseReleased(x, y, b); }
    @Override protected void keyPressed(char c, int key) {
        HudModules.Module module = selected(); int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 10 : 1;
        if (module != null && (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT || key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN)) {
            int[] bounds = module.bounds(minecraft);
            module.move(minecraft, bounds[0] + (key == Keyboard.KEY_LEFT ? -step : key == Keyboard.KEY_RIGHT ? step : 0), bounds[1] + (key == Keyboard.KEY_UP ? -step : key == Keyboard.KEY_DOWN ? step : 0));
        } else super.keyPressed(c, key);
    }
    @Override public void handleMouse() { super.handleMouse(); int wheel = Mouse.getEventDWheel(); HudModules.Module module = selected(); if (wheel != 0 && module != null) module.scale(wheel > 0 ? 0.05 : -0.05); }
    @Override public void removed() { for (var module : HudModules.modules()) module.save(); super.removed(); }
}
