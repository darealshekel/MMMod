package com.mmm.smoke;

import com.mmm.config.Configs;
import com.mmm.ui.MmmSettingsScreen;
import com.mmm.ui.ProgressShareScreen;
import com.mmm.ui.SettingsNavigation;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

/** Opt-in fixture for an isolated smoke game, never included in release jars. */
final class HudSettingsSmoke
{
    private int phase, ticks, screenshots;
    private long started;
    private MmmSettingsScreen settings;
    private boolean originalOverlay;
    private java.util.Set<Path> previousExports;
    private final String[] queries = {"Custom Sounds", "Ignored Players", "Perimeter Block List", "Sync Scoreboard", "Set the menu accent", "Hotkeys"};
    private final String[] editors = {"GoalSoundSettingsScreen", "MmmChatIgnoreListScreen", "PerimeterBlockListScreen", "SyncScoreboardScreen", "MmmColorEditorScreen", "GuiConfigs"};
    private final String[] routes = {"Projects", "Profile", "History", "Summary", "Scoreboard", "Leaderboards"};
    private final String[] tools = {"ProjectManagerScreen", "PlayerProfileScreen", "SessionHistoryScreen", "SummaryScreen", "ScoreboardScreen", "LeaderboardScreen"};
    private int editor, route;
    private String originalColor;
    private net.minecraft.client.gui.screen.Screen scoreboardScreen;
    private ScoreboardObjective editObjective;

    void install()
    {
        started = System.nanoTime();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (phase == 99) return;
            try
            {
                if (System.nanoTime() - started > 90_000_000_000L) throw new AssertionError("Settings smoke timed out");
                if (client.player == null || client.world == null || ++ticks < 12) return;
                ticks = 0;
                switch (phase)
                {
                    case 0 -> {
                        resize(client, 1280, 720);
                        originalOverlay = Configs.Generic.MINED_BLOCK_OVERLAY.getBooleanValue();
                        settings = new MmmSettingsScreen(null);
                        client.setScreen(settings);
                        phase++;
                    }
                    case 1 -> {
                        checkInline("Everyday settings", "Mining Tracker", "Main HUD", "Daily Goal", "Move HUD");
                        shot(client, "01-everyday.png");
                        press(client, "Hotkeys");
                        phase = 20;
                    }
                    case 20 -> {
                        check(client.currentScreen instanceof com.mmm.gui.GuiConfigs, "Home Hotkeys missed");
                        press(client, "Back");
                        phase++;
                    }
                    case 21 -> {
                        check(client.currentScreen == settings, "Hotkeys lost the Settings parent");
                        checkInline("Everyday settings", "Mining Tracker");
                        press(client, "Tools");
                        phase = 23;
                    }
                    case 23 -> {
                        shot(client, "01-tools.png");
                        press(client, "HUD");
                        check(settings.getFocused() == null, "Rail navigation retained a removed button as focus");
                        phase = 2;
                    }
                    case 2 -> {
                        checkInline("HUD", "Mined Block Overlay", "World Playtime", "Playtime Format", "HUD Title");
                        shot(client, "02-hud.png");
                        search().setText("minedBlockOverlay");
                        phase = 5;
                    }
                    case 5 -> {
                        check(!field("visibleSections", java.util.List.class).isEmpty(), "Search missed internal config name");
                        shot(client, "03-search.png");
                        check(settings.keyPressed(new KeyInput(256, 0, 0)), "Escape did not clear search");
                        check(search().getText().isEmpty(), "Search remained after Escape");
                        checkInline("HUD", "Mined Block Overlay", "Playtime Format");
                        settings.setFocused(null);
                        search().setFocused(false);
                        check(settings.charTyped(new CharInput('p', 0)), "Typed input did not focus search");
                        check(search().getText().equals("p"), "Typed search lost or duplicated a character");
                        check(settings.getFocused() == search(), "Typed search focus not synchronized");
                        search().setText("");
                        resize(client, 640, 360);
                        phase++;
                    }
                    case 6 -> {
                        press(client, "Mining visuals");
                        checkInline("Mining visuals", "Block ESP", "Lava Opacity", "Small Dig Items");
                        check(!inlineLabels().contains("Speed Graph"), "Disabled speed graph appeared on the rail page");
                        phase = 3;
                    }
                    case 3 -> {
                        press(client, "HUD");
                        phase++;
                    }
                    case 4 -> {
                        settings.keyPressed(new KeyInput(267, 0, 0));
                        shot(client, "04-narrow-playtime.png");
                        check(Configs.Generic.MINED_BLOCK_OVERLAY.getBooleanValue() == originalOverlay,
                                "Navigation or clipped controls changed configuration");
                        resize(client, 1280, 720);
                        client.setScreen(ProgressShareScreen.collection(settings, "Stone", "minecraft:stone", 3_785_086,
                                "Smoke world", new ItemStack(Items.STONE)));
                        phase = 7;
                    }
                    case 7 -> {
                        press(client, "Copy text");
                        check(client.keyboard.getClipboard().contains("3,785,086 blocks mined"), "Share caption is incomplete");
                        Path directory = client.runDirectory.toPath().resolve("screenshots/mmm");
                        if (Files.isDirectory(directory)) {
                            try (var files = Files.list(directory)) { previousExports = files.collect(java.util.stream.Collectors.toSet()); }
                        } else previousExports = java.util.Set.of();
                        press(client, "Save PNG");
                        phase++;
                    }
                    case 8 -> {
                        if (!button(client, "Save PNG").active) return;
                        Path directory = client.runDirectory.toPath().resolve("screenshots/mmm");
                        check(Files.isDirectory(directory), "Share export directory missing");
                        try (var files = Files.list(directory))
                        {
                            Path png = files.filter(path -> path.toString().endsWith(".png") && !previousExports.contains(path)).findFirst().orElseThrow();
                            try (var stream = Files.newInputStream(png); var image = NativeImage.read(stream))
                            {
                                check(image.getWidth() < client.getFramebuffer().textureWidth, "Share captured full screen");
                                check(image.getHeight() < client.getFramebuffer().textureHeight, "Share captured full screen");
                                check(image.getColorArgb(4, image.getHeight() / 2) == com.mmm.ui.BoardEditorStyle.FLOOR,
                                        "Share export captured a stale or blank frame");
                            }
                        }
                        shot(client, "05-share.png");
                        phase++;
                    }
                    case 9 -> {
                        press(client, "Back");
                        check(client.currentScreen == settings, "Share lost its parent");
                        search().setText(queries[editor]);
                        phase++;
                    }
                    case 10 -> {
                        var target = field("clickTargets", java.util.List.class).stream()
                                .filter(t -> integer(t, "y") > 75 && (editor == 4 ? integer(t, "width") == 18 : integer(t, "width") > 32))
                                .findFirst().orElseThrow();
                        click(client, integer(target, "x") + 4, integer(target, "y") + 4);
                        check(client.currentScreen.getClass().getSimpleName().equals(editors[editor]), "Wrong editor for " + queries[editor]);
                        if (editor == 4) {
                            originalColor = Configs.Generic.MENU_HEX_COLOR.getStringValue();
                            resize(client, 640, 360);
                            var picker = client.currentScreen;
                            var hex = picker.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();
                            hex.setText("#FFDF2040");
                            click(client, integer(picker, "fieldX") + integer(picker, "fieldWidth") / 2,
                                    integer(picker, "fieldY") + integer(picker, "fieldHeight") / 2);
                        }
                        phase++;
                    }
                    case 11 -> {
                        shot(client, "editor-" + editors[editor] + ".png");
                        if (editor == 4) checkPicker(client);
                        if (editor == 5) {
                            press(client, "Back");
                            check(client.currentScreen == settings && search().getText().equals("Hotkeys"), "Search tool lost its return context");
                            phase = 24; return;
                        }
                        press(client, "Back");
                        check(client.currentScreen == settings, "Back lost settings for " + queries[editor]);
                        if (editor == 4) {
                            Configs.Generic.MENU_HEX_COLOR.setValueFromString(originalColor);
                            resize(client, 1280, 720);
                        }
                        search().setText(queries[++editor]);
                        phase = 10;
                    }
                    case 24 -> { press(client, "Tools"); phase = 12; }
                    case 12 -> {
                        check(client.currentScreen == settings, "Lost Tools board before " + routes[route]);
                        press(client, routes[route]);
                        check(client.currentScreen.getClass().getSimpleName().equals(tools[route]), "Tile missed " + routes[route]);
                        phase++;
                    }
                    case 13 -> {
                        shot(client, "tool-" + tools[route] + ".png");
                        if (tools[route].equals("ScoreboardScreen")) {
                            scoreboardScreen = client.currentScreen;
                            var board = client.world.getScoreboard();
                            editObjective = board.addObjective("mmm_smoke_edit", ScoreboardCriterion.DUMMY, Text.literal("Smoke scores"),
                                    ScoreboardCriterion.RenderType.INTEGER, false, null);
                            board.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, editObjective);
                            scoreboardScreen.mouseScrolled(scoreboardScreen.width - 30, 130, 0, -200);
                            phase = 15;
                            return;
                        }
                        client.currentScreen.close();
                        check(client.currentScreen == settings, "Tool navigation lost settings parent");
                        if (++route < routes.length) { phase = 12; return; }
                        phase++;
                    }
                    case 14 -> {
                        if (screenshots != 0) return;
                        Files.writeString(Path.of("mmm-smoke-result.txt"),
                                "PASS: navigation, search, six editors, six tool routes, scoreboard records and edit, picker gradients and marker, share caption, cropped PNG and return paths");
                        System.out.println("MMM_HUD_SETTINGS_SMOKE_PASS");
                        phase = 99;
                        client.scheduleStop();
                    }
                    case 15, 17 -> {
                        var f = scoreboardScreen.getClass().getDeclaredField("clickTargets");
                        f.setAccessible(true);
                        var targets = (java.util.List<?>) f.get(scoreboardScreen);
                        // Manage Records and Edit Scores are the last two actions in the scrolled Tools section.
                        var target = targets.get(targets.size() - (phase == 15 ? 2 : 1));
                        click(client, integer(target, "x") + 5, integer(target, "y") + 5);
                        String screen = phase == 15 ? "ScoreboardRecordsScreen" : "ScoreboardEditScreen";
                        check(client.currentScreen.getClass().getSimpleName().equals(screen), "Missing scoreboard action: " + screen);
                        phase++;
                    }
                    case 16, 18 -> {
                        shot(client, "tool-" + client.currentScreen.getClass().getSimpleName() + ".png");
                        press(client, "Back");
                        check(client.currentScreen == scoreboardScreen, "Scoreboard child lost its parent");
                        if (phase == 18) {
                            client.world.getScoreboard().removeObjective(editObjective);
                            scoreboardScreen.close();
                            route++;
                            phase = 12;
                        } else phase++;
                    }
                    default -> throw new AssertionError("Unexpected smoke phase");
                }
            }
            catch (Throwable failure) { fail(client, failure); }
        });
    }

    private TextFieldWidget search() throws Exception { return field("searchField", TextFieldWidget.class); }

    private java.util.List<String> inlineLabels() throws Exception {
        var labels = new java.util.ArrayList<String>();
        for (Object group : field("visibleSections", java.util.List.class)) {
            var rows = group.getClass().getDeclaredMethod("rows");
            rows.setAccessible(true);
            for (Object row : (java.util.List<?>)rows.invoke(group)) labels.add(((SettingsNavigation.Row)row).label());
        }
        return labels;
    }

    private void checkInline(String category, String... expected) throws Exception {
        check(field("currentPage", SettingsNavigation.Page.class).title().equals(category), "Wrong category: " + category);
        check(field("navigationButtons", java.util.List.class).isEmpty(), "Group gate on " + category);
        check(inlineLabels().containsAll(java.util.List.of(expected)), "Missing inline rows on " + category);
    }

    private static int integer(Object o, String name) {
        try {
            var f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.getInt(o);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void click(MinecraftClient client, double x, double y) {
        var screen = client.currentScreen;
        var click = new Click(x, y, new MouseInput(0, 0));
        check(screen.mouseClicked(click, false), "No click target at " + x + ", " + y);
        screen.mouseReleased(click);
    }

    private void checkPicker(MinecraftClient client) {
        var picker = client.currentScreen;
        int x = integer(picker, "fieldX"), y = integer(picker, "fieldY");
        int w = integer(picker, "fieldWidth"), h = integer(picker, "fieldHeight");
        int guiW = picker.width, guiH = picker.height;
        screenshots++;
        ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> {
            try (image) {
                java.util.function.IntBinaryOperator pixel = (px, py) -> image.getColorArgb(
                        (int)((px + .5) * image.getWidth() / guiW), (int)((py + .5) * image.getHeight() / guiH)) & 0xffffff;
                var horizontal = new java.util.HashSet<Integer>(); var vertical = new java.util.HashSet<Integer>();
                for (int i = 1; i < w-1; i++) horizontal.add(pixel.applyAsInt(x+i, y+5));
                for (int i = 1; i < h-1; i++) vertical.add(pixel.applyAsInt(x+5, y+i));
                check(horizontal.size() > 70 && vertical.size() > 40, "Color picker rendered as a low-resolution grid: " + horizontal.size() + "/" + vertical.size());
                int mx = x+w/2, my = y+h/2;
                check(pixel.applyAsInt(mx-3, my) == 0 && pixel.applyAsInt(mx-2, my) == 0xffffff, "Picker marker was blurred or misplaced");
            } catch (Throwable e) { fail(client, e); }
            finally { screenshots--; }
        });
    }

    private <T> T field(String name, Class<T> type) throws Exception
    {
        var field = MmmSettingsScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(settings));
    }

    private static ButtonWidget button(MinecraftClient client, String label)
    {
        return client.currentScreen.children().stream().filter(child -> child instanceof ButtonWidget)
                .map(child -> (ButtonWidget) child).filter(button -> button.visible && button.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button: " + label));
    }

    private static void press(MinecraftClient client, String label)
    {
        ButtonWidget button = button(client, label);
        Click click = new Click(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, new MouseInput(0, 0));
        check(client.currentScreen.mouseClicked(click, false), "Button did not accept typed click: " + label);
        client.currentScreen.mouseReleased(click);
    }

    private void shot(MinecraftClient client, String name) throws Exception
    {
        Path directory = Path.of("../hud-settings-screenshots");
        Files.createDirectories(directory);
        screenshots++;
        ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> {
            try (image) { image.writeTo(directory.resolve(name)); }
            catch (Throwable failure) { fail(client, failure); }
            finally { screenshots--; }
        });
    }

    private void fail(MinecraftClient client, Throwable failure)
    {
        phase = 99;
        failure.printStackTrace();
        try { Files.writeString(Path.of("mmm-smoke-result.txt"), "FAIL: " + failure); }
        catch (Exception ignored) {}
        client.scheduleStop();
    }

    private static void resize(MinecraftClient client, int width, int height)
    {
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), width, height);
        client.options.getGuiScale().setValue(2);
        client.onResolutionChanged();
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
