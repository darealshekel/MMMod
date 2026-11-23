package com.mmm.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

public final class PreWorldBoardSmoke {
    private int stage, frames, routeIndex;
    private Screen title, settings;
    private final java.util.function.Function<Screen, Screen> factory;

    private final int leftMouseButton;

    public PreWorldBoardSmoke(java.util.function.Function<Screen, Screen> factory, int leftMouseButton) {
        this.leftMouseButton = leftMouseButton;
        this.factory = factory;
    }

    public boolean tick(Screen current, Consumer<String> shot) {
        var client = Minecraft.getInstance();
        if (client.level != null || client.player != null) throw new AssertionError("Expected title screen context");
        if (++frames < 10) return false;
        frames = 0;
        switch (stage++) {
            case 0 -> {
                client.options.guiScale().set(1);
                title = current instanceof TitleScreen ? current : new TitleScreen();
                settings = factory.apply(title);
                BoardPlatform.open(settings);
            }
            case 1 -> {
                if (current != settings) throw new AssertionError("Mod Menu did not open Settings");
                shot.accept("title-settings.png");
                press(current, "Website Link");
            }
            case 2 -> {
                if (!(current instanceof WebsiteLinkScreen)) throw new AssertionError("Website Link missing");
                current.onClose();
            }
            case 3 -> {
                if (current != settings) throw new AssertionError("Lost settings parent");
                BoardPlatform.open(new CollectionBookScreen(settings));
            }
            case 4 -> {
                shot.accept("title-collections.png");
                var search = Screens.getWidgets(current).stream().filter(EditBox.class::isInstance)
                        .map(EditBox.class::cast).findFirst().orElseThrow();
                search.setValue("minecraft:stone");
                press(current, "Stone");
            }
            case 5 -> press(current, "Tier I");
            case 6 -> {
                shot.accept("title-tier.png");
                var share = Screens.getWidgets(current).stream().filter(w -> w.getMessage().getString().equals("Share collection")).findFirst().orElseThrow();
                if (share.active) throw new AssertionError("Catalog share must require a player");
                current.onClose();
            }
            case 7 -> {
                if (current != settings) throw new AssertionError("Collections lost Settings parent");
                BoardPlatform.open(new GoalSoundSettingsScreen(settings));
                stage = 9;
            }
            case 9 -> {
                require(current, GoalSoundSettingsScreen.class);
                shot.accept("title-goal-sounds.png");
                press(current, "Done");
            }
            case 10 -> {
                if (current != settings) throw new AssertionError("Goal sounds lost Settings parent");
                BoardPlatform.open(new SyncScoreboardScreen(settings));
            }
            case 11 -> {
                require(current, SyncScoreboardScreen.class);
                shot.accept("title-sync-scoreboard.png");
                current.onClose();
                BoardPlatform.open(new PerimeterBlockListScreen(settings));
            }
            case 12 -> {
                require(current, PerimeterBlockListScreen.class);
                shot.accept("title-perimeter.png");
                current.onClose();
                if (BoardPlatform.SPEED_GRAPH) BoardPlatform.open(BoardPlatform.ignoreList(settings));
            }
            case 13 -> {
                if (BoardPlatform.SPEED_GRAPH) {
                    if (!current.getClass().getSimpleName().equals("MmmChatIgnoreListScreen")) throw new AssertionError("Ignore route");
                    shot.accept("title-ignore.png");
                    current.onClose();
                }
                openPicker(settings);
            }
            case 14 -> {
                checkPicker(current);
                shot.accept("title-picker.png");
                press(current, "Back");
            }
            case 15 -> {
                if (current != settings) throw new AssertionError("Picker lost Settings parent");
                click(current, "Hotkeys");
            }
            case 16 -> {
                require(current, com.mmm.gui.GuiConfigs.class);
                click(current, "Back");
            }
            case 17 -> {
                if (current != settings) throw new AssertionError("Hotkeys lost the Settings board");
                widget(current, "Hotkeys");
                click(current, "Tools");
                stage = 19;
            }
            case 19 -> {
                if (current != settings) throw new AssertionError("Tool lost its Settings parent");
                widget(current, "Projects");
                if (routeIndex == 0) shot.accept("title-tools.png");
                if (routeIndex < MmmUi.availableTools().size()) {
                    click(current, MmmUi.availableTools().get(routeIndex).label);
                } else {
                    click(current, "< Back");
                    stage = 21;
                }
            }
            case 20 -> {
                ToolDirectory expected = MmmUi.availableTools().get(routeIndex++);
                if (!expected.name().equals(routeId(current))) throw new AssertionError("Wrong tile for " + expected);
                shot.accept("title-route-" + expected.name().toLowerCase(java.util.Locale.ROOT) + ".png");
                click(current, "Back");
                stage = 19;
            }
            case 21 -> {
                if (current != settings) throw new AssertionError("Tools lost Settings home");
                widget(current, "Hotkeys");
                settings.onClose();
            }
            case 22 -> {
                if (current != title) throw new AssertionError("Lost title parent");
                return true;
            }
            default -> throw new AssertionError("Unexpected pre-world stage");
        }
        return false;
    }

    private static void require(Screen current, Class<?> expected) {
        if (!expected.isInstance(current)) throw new AssertionError("Expected " + expected.getSimpleName() + ": " + current);
    }

    private static String routeId(Screen current) {
        return switch (current.getClass().getSimpleName()) {
            case "GuiConfigs" -> "HOTKEYS";
            case "ProjectManagerScreen" -> "PROJECTS";
            case "PlayerProfileScreen" -> "PROFILE";
            case "SessionHistoryScreen" -> "HISTORY";
            case "SummaryScreen" -> "SUMMARY";
            case "ScoreboardScreen" -> "SCOREBOARD";
            case "LeaderboardScreen" -> "LEADERBOARDS";
            default -> "";
        };
    }

    public static void openPicker(Screen parent) {
        var value = new com.mmm.config.value.ConfigColor("pickerSmoke", "#80402010", "Smoke fixture");
        BoardPlatform.open(new MmmColorEditorScreen(parent, value, () -> {}));
    }

    public static void checkPicker(Screen current) {
        require(current, MmmColorEditorScreen.class);
        var picker = (MmmColorEditorScreen) current;
        var field = Screens.getWidgets(current).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
        field.setValue("#8000FF00");
        int x = BoardEditorStyle.contentX(current.width, 352) + 8;
        int sheet = Math.min(210, current.height - BoardEditorStyle.HEADER_HEIGHT - 8);
        int y = BoardEditorStyle.HEADER_HEIGHT + (current.height - BoardEditorStyle.HEADER_HEIGHT - sheet) / 2 + 28;
        if (!picker.mouseClicked(x, y, 0) || !field.getValue().equalsIgnoreCase("#80FFFFFF"))
            throw new AssertionError("Picker white corner / alpha: " + field.getValue());
        picker.mouseDragged(x + 42, y + sheet - 61, 0, 42, sheet - 61);
        picker.mouseReleased(x + 42, y + sheet - 61, 0);
        if (!field.getValue().equalsIgnoreCase("#80000000")) throw new AssertionError("Picker black edge: " + field.getValue());
        field.setValue("#8038BAA0");
    }

    private static AbstractButton widget(Screen screen, String name) {
        return Screens.getWidgets(screen).stream().filter(w -> w.visible && w.active && w.getMessage().getString().equals(name))
                .map(AbstractButton.class::cast).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + name));
    }

    private void click(Screen screen, String name) {
        var button = widget(screen, name);
        var event = new MouseButtonEvent(button.getX() + button.getWidth() / 2.0,
                button.getY() + button.getHeight() / 2.0, new MouseButtonInfo(leftMouseButton, 0));
        if (!screen.mouseClicked(event, false)) throw new AssertionError("Inactive button: " + name);
        screen.mouseReleased(event);
    }

    public static void press(Screen screen, String name) {
        widget(screen, name).onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }
}
