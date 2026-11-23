package com.mmm.smoke;

import com.mmm.config.Configs;
import com.mmm.scoreboard.ScoreboardEditScreen;
import com.mmm.scoreboard.ScoreboardMoveScreen;
import com.mmm.scoreboard.ScoreboardRecordsScreen;
import com.mmm.scoreboard.ScoreboardScreen;
import com.mmm.ui.MmmUi;
import com.mmm.ui.ToolDirectory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.text.Text;

/** Click the real controls after layout; only the disposable development client runs this. */
final class ToolControlsSmoke {
    private int phase;
    private ScoreboardScreen scoreboard;

    boolean done(MinecraftClient client, Screen settings) {
        switch (phase) {
            case 0 -> {
                MmmUi.openTool(settings, ToolDirectory.PROJECTS);
                Screen projects = client.currentScreen;
                projects.init(client, 340, 240);
                int before = Configs.PROJECTS.size();
                click(projects, "New Project");
                var entry = Configs.PROJECTS.getLast();
                require(Configs.PROJECTS.size() == before + 1 && entry.id.equals(Configs.activeProjectId), "New project");
                var name = projects.children().stream().filter(TextFieldWidget.class::isInstance)
                        .map(TextFieldWidget.class::cast).findFirst().orElseThrow();
                require(entry.name.equals(name.getText()), "New project lost selection");
                name.setText("Compact smoke");
                click(projects, "Apply Changes");
                require(client.currentScreen == projects && entry.name.equals("Compact smoke"), "Apply lost selection");
                Configs.activeProjectId = Configs.PROJECTS.getFirst().id;
                projects.init(client, 340, 240);
                click(projects, "Set Active");
                require(entry.id.equals(Configs.activeProjectId), "Set Active used wrong project");
                click(projects, "Remove");
                click(projects, "Confirm Delete");
                require(Configs.PROJECTS.size() == before && !Configs.PROJECTS.contains(entry), "Remove failed");
                click(projects, "Back");
                require(client.currentScreen == settings, "Projects Back");
                MmmUi.openTool(settings, ToolDirectory.SCOREBOARD);
                scoreboard = (ScoreboardScreen) client.currentScreen;
                var objective = new Scoreboard().addObjective("compact", ScoreboardCriterion.DUMMY,
                        Text.literal("Compact"), ScoreboardCriterion.RenderType.INTEGER, false, null);
                open(client, new ScoreboardEditScreen(scoreboard, objective));
            }
            case 1 -> click(client.currentScreen, "Add Row");
            case 2 -> click(client.currentScreen, "Delete");
            case 3 -> {
                click(client.currentScreen, "Back");
                require(client.currentScreen == scoreboard, "Edit Back");
                open(client, new ScoreboardRecordsScreen(scoreboard));
            }
            case 4 -> {
                bounds(client.currentScreen, "Record Current");
                bounds(client.currentScreen, "Export All");
                click(client.currentScreen, "Back");
                require(client.currentScreen == scoreboard, "Records Back");
                open(client, new ScoreboardMoveScreen(scoreboard));
            }
            case 5 -> {
                double original = Configs.Generic.SCOREBOARD_SCALE.getDoubleValue();
                click(client.currentScreen, original < 2 ? "+" : "-");
                require(Configs.Generic.SCOREBOARD_SCALE.getDoubleValue() != original, "Move scale button");
                click(client.currentScreen, "Reset");
                click(client.currentScreen, "Back");
                require(client.currentScreen == scoreboard, "Move Back");
                click(scoreboard, "Back");
                require(client.currentScreen == settings, "Scoreboard Back");
                System.out.println("MMM_TOOL_CONTROLS_PASS projects, scoreboard edit/records/move, compact geometry, Back");
            }
            default -> { return true; }
        }
        phase++;
        return false;
    }

    private static void open(MinecraftClient client, Screen screen) {
        client.setScreen(screen);
        screen.init(client, 340, 240);
    }

    private static ButtonWidget bounds(Screen screen, String label) {
        var button = screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(b -> b.visible && b.getMessage().getString().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + label));
        require(button.getX() >= 0 && button.getY() >= 0 && button.getRight() <= screen.width
                && button.getBottom() <= screen.height, "Clipped " + label);
        require(MinecraftClient.getInstance().textRenderer.getWidth(label) + 4 <= button.getWidth(), "Clipped label: " + label);
        for (var child : screen.children()) {
            if (child instanceof ClickableWidget other && other != button && other.visible)
                require(other.getX() >= button.getRight() || other.getRight() <= button.getX()
                        || other.getY() >= button.getBottom() || other.getBottom() <= button.getY(), "Overlapping " + label);
        }
        return button;
    }

    private static void click(Screen screen, String label) {
        var b = bounds(screen, label);
        require(b.active && screen.mouseClicked(b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0, 0),
                "Unhandled " + label);
        screen.mouseReleased(b.getX(), b.getY(), 0);
    }

    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
