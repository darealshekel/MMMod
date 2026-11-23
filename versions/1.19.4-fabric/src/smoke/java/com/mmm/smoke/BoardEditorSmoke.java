package com.mmm.smoke;

import com.mmm.config.value.ConfigColor;
import com.mmm.gui.GuiConfigs;
import com.mmm.ui.BoardChrome;
import com.mmm.ui.BoardEditorStyle;
import com.mmm.ui.GoalSoundSettingsScreen;
import com.mmm.ui.MmmColorEditorScreen;
import com.mmm.ui.MmmSettingsScreen;
import com.mmm.ui.ToolDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

/** Development-only optical and navigation regression check with no world or account service. */
final class BoardEditorSmoke {
    private final ConfigColor color = new ConfigColor("Probe color", "#80FF0000", "");
    private final long deadline = System.nanoTime() + 160_000_000_000L;
    private Screen settings, hotkeys;
    private int phase, ticks, callbacks, toolIndex;

    void install() {
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            try {
                if (phase < 0) return;
                if (System.nanoTime() > deadline) throw new AssertionError("timeout phase " + phase);
                if (c.currentScreen == null || c.getOverlay() != null || ++ticks % 10 != 0) return;
                switch (phase++) {
                    case 0 -> {
                        settings = new MmmSettingsScreen(c.currentScreen);
                        c.setScreen(new GoalSoundSettingsScreen(settings));
                    }
                    case 1 -> { capture(c, "goal-sounds").close(); press(c.currentScreen, "< Back"); }
                    case 2 -> {
                        require(c.currentScreen == settings, "goal-sounds Back");
                        for (int i = 0; i <= settings.children().size(); i++) {
                            settings.keyPressed(258, 0, 0);
                            if (settings.getFocused() instanceof ButtonWidget b && b.getMessage().getString().equals("Hotkeys")) break;
                        }
                        require(settings.getFocused() instanceof ButtonWidget b && b.getMessage().getString().equals("Hotkeys"), "Hotkeys outside tab order");
                        settings.keyPressed(257, 0, 0);
                    }
                    case 3 -> {
                        hotkeys = c.currentScreen;
                        require(hotkeys instanceof GuiConfigs, "home Hotkeys");
                        capture(c, "home-hotkeys").close();
                        c.setScreen(new MmmColorEditorScreen(hotkeys, color, () -> callbacks++));
                    }
                    case 4 -> checkPicker(c);
                    case 5 -> {
                        require(c.currentScreen == hotkeys && callbacks == 1 && color.getStringValue().equalsIgnoreCase("#40123456"), "commit/back failed");
                        require(hotkeys.keyPressed(256, 0, 0), "Hotkeys Esc");
                    }
                    case 6 -> { require(c.currentScreen == settings, "home Hotkeys Back"); press(settings, "Tools"); }
                    case 7 -> {
                        String label = ToolDirectory.values()[toolIndex].label;
                        if (button(settings, label) == null) {
                            settings.mouseScrolled(settings.width / 2.0, settings.height - 16, -1);
                            phase--;
                        } else press(settings, label);
                    }
                    case 8 -> {
                        String tool = ToolDirectory.values()[toolIndex].label;
                        require(c.currentScreen instanceof BoardChrome.Page, "tool not on board: " + tool);
                        capture(c, tool.toLowerCase(Locale.ROOT)).close();
                        press(c.currentScreen, "< Back");
                        require(c.currentScreen == settings, "wrong return screen: " + tool);
                        if (++toolIndex < ToolDirectory.values().length) { phase = 7; return; }
                    }
                    case 9 -> press(settings, "< Back");
                    case 10 -> {
                        for (char ch : "hotkeys".toCharArray()) require(settings.charTyped(ch, 0), "search input");
                    }
                    case 11 -> press(settings, "Hotkeys");
                    case 12 -> {
                        require(c.currentScreen instanceof GuiConfigs, "Hotkeys search result");
                        press(c.currentScreen, "< Back");
                    }
                    case 13 -> {
                        require(c.currentScreen == settings && settings.children().stream().anyMatch(child ->
                                child instanceof TextFieldWidget box && box.visible && box.getText().equals("hotkeys")), "lost search");
                        Files.writeString(Path.of("board-editor-result.txt"), "PASS: goal sounds, home keyboard Hotkeys, seven Tools tiles, search and Back, framebuffer HSV midpoint/ramp/marker/floor, drag, hex, alpha and parent callback");
                        phase = -1;
                        c.scheduleStop();
                    }
                }
            } catch (Throwable e) {
                phase = -1;
                e.printStackTrace();
                try { Files.writeString(Path.of("board-editor-result.txt"), "FAIL: " + e); } catch (Exception ignored) {}
                c.scheduleStop();
            }
        });
    }

    private void checkPicker(MinecraftClient c) throws Exception {
        Screen screen = c.currentScreen;
        int sw = BoardEditorStyle.contentWidth(screen.width, 268), x = BoardEditorStyle.contentX(screen.width, 268);
        int h = Math.min(134, Math.max(8, screen.height - BoardEditorStyle.HEADER_HEIGHT - 86));
        int y = BoardEditorStyle.HEADER_HEIGHT + Math.max(4, (screen.height - BoardEditorStyle.HEADER_HEIGHT - h - 76) / 2) + 24;
        try (NativeImage im = capture(c, "picker")) {
            double scale = c.getWindow().getScaleFactor();
            int mid = pixel(im, x + (sw - 38) / 2, y + h / 2, scale);
            require(Math.abs((mid & 255) - 127) < 7 && Math.abs(((mid >>> 8) & 255) - 64) < 7,
                    "incorrect HSV midpoint ABGR=" + Integer.toHexString(mid));
            require((pixel(im, 2, screen.height - 3, scale) & 0xffffff) == (BoardEditorStyle.FLOOR & 0xffffff), "backdrop is not board floor");
            var levels = new HashSet<Integer>();
            for (int i = 0; i < sw - 38; i++) levels.add(pixel(im, x + i, y + 5, scale) >>> 8 & 255);
            require(levels.size() > 120, "coarse saturation ramp: " + levels.size());
            require((pixel(im, x + sw - 39, y + 2, scale) & 0xffffff) == 0xffffff, "marker not white");
            require((pixel(im, x + sw - 39, y + 3, scale) & 0xffffff) == (BoardEditorStyle.FLOOR & 0xffffff), "marker not crisp");
        }
        screen.mouseClicked(x + 8, y + 8, 0);
        screen.mouseDragged(x + 16, y + 16, 0, 8, 8);
        screen.mouseReleased(x + 16, y + 16, 0);
        require(color.getStringValue().startsWith("#80") && !color.getStringValue().equals("#80FF0000"), "drag lost alpha or did not edit");
        for (var child : screen.children()) if (child instanceof TextFieldWidget f) f.setText("#40123456");
        press(screen, "Done");
    }

    private static NativeImage capture(MinecraftClient c, String name) throws Exception {
        NativeImage image = ScreenshotRecorder.takeScreenshot(c.getFramebuffer());
        image.writeTo(Path.of("board-" + name + ".png"));
        return image;
    }
    private static int pixel(NativeImage image, int x, int y, double scale) {
        return image.getColor((int)((x + .5) * scale), (int)((y + .5) * scale));
    }
    private static ButtonWidget button(Screen s, String label) {
        for (var child : s.children()) if (child instanceof ButtonWidget b && b.visible && b.active && b.getMessage().getString().equals(label)) {
            return b;
        }
        return null;
    }
    private static void press(Screen s, String label) {
        ButtonWidget b = button(s, label);
        require(b != null, "missing " + label + " on " + s);
        require(s.mouseClicked(b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0, 0), "click ignored: " + label);
    }
    private static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
}
