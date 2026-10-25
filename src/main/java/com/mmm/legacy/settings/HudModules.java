package com.mmm.legacy.settings;

import com.mmm.hud.HudModuleId;
import com.mmm.hud.MiningHudRenderer;
import com.mmm.legacy.LegacyDrawContext;
import com.mmm.timer.TimerHudRenderer;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

public final class HudModules {
    public interface Module {
        String id();
        String label();
        /** Scaled GUI coordinates: left, top, right, bottom. No display-pixel coordinates. */
        int[] bounds(Minecraft client);
        void renderPreview(LegacyDrawContext context, Minecraft client);
        void move(Minecraft client, int x, int y);
        void scale(double delta);
        boolean visible();
        void toggle();
        /** Reset this module's position and scale, preserving content/visibility. */
        void reset();
        /** Called after drag and before leaving the editor. */
        default void save() {}
    }
    private static final Map<String, Supplier<List<Module>>> providers = new LinkedHashMap<>();
    private HudModules() {}
    public static void register(String id, Supplier<List<Module>> provider) { providers.put(id, Objects.requireNonNull(provider)); }
    public static List<Module> modules() {
        Map<String, Module> result = new LinkedHashMap<>();
        for (HudModuleId id : HudModuleId.values()) {
            Module module = new Builtin(id); result.put(module.id(), module);
        }
        for (Supplier<List<Module>> provider : providers.values()) for (Module module : provider.get()) result.putIfAbsent(module.id(), module);
        return List.copyOf(result.values());
    }
    private record Builtin(HudModuleId module) implements Module {
        public String id() { return "mmm:" + module.name(); }
        public String label() { return module.label(); }
        public int[] bounds(Minecraft client) { return TimerHudRenderer.getBounds(client, module); }
        public void renderPreview(LegacyDrawContext context, Minecraft client) {
            if (module == HudModuleId.MAIN) MiningHudRenderer.renderPreview(context, client);
            else TimerHudRenderer.drawModule(context, client, module, true);
        }
        public void move(Minecraft client, int x, int y) { TimerHudRenderer.setModulePosition(client, module, x, y); }
        public void scale(double delta) { TimerHudRenderer.adjustScale(module, delta); }
        public boolean visible() { return TimerHudRenderer.isVisible(module); }
        public void toggle() { TimerHudRenderer.toggleVisible(module); }
        public void reset() { TimerHudRenderer.resetModuleLayout(module); }
        public void save() { com.mmm.config.Configs.saveToFile(); }
    }
}
