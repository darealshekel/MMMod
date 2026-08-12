package com.mmm.scoreboard;

import com.mmm.ui.CompatScreen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import com.mmm.ui.MmmUi;

import com.mmm.util.MmmMessages;

public final class ScoreboardEditScreen extends CompatScreen
{
    private static final int ROW_HEIGHT = 30;

    private final Screen parent;
    private final Objective objective;
    private final List<RowModel> rows = new ArrayList<>();
    private final List<RowWidgets> rowWidgets = new ArrayList<>();
    private Button addButton;
    private Button sortButton;
    private Button directionButton;
    private Button saveButton;
    private Button backButton;
    private SortMode sortMode = SortMode.SCORE;
    private boolean descending = true;
    private double scrollY;
    private int contentHeight;
    private String error = "";

    public ScoreboardEditScreen(Screen parent, Objective objective)
    {
        super(Component.literal("Edit Scoreboard"));
        this.parent = parent;
        this.objective = objective;
        for (PlayerScoreEntry entry : objective.getScoreboard().listPlayerScores(objective))
        {
            if (!entry.isHidden())
            {
                this.rows.add(new RowModel(entry.owner(), entry.value()));
            }
        }
        this.sortRows();
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.clearWidgets();
        this.rowWidgets.clear();
        this.addButton = this.addRenderableWidget(Button.builder(Component.literal("Add Row"), ignored -> this.addRow())
                .bounds(0, 0, 76, 18).build());
        this.sortButton = this.addRenderableWidget(Button.builder(Component.literal("Sort: " + this.sortMode.label), ignored -> this.cycleSort())
                .bounds(0, 0, 96, 18).build());
        this.directionButton = this.addRenderableWidget(Button.builder(Component.literal(this.descending ? "Descending" : "Ascending"), ignored -> this.toggleDirection())
                .bounds(0, 0, 82, 18).build());
        this.saveButton = this.addRenderableWidget(Button.builder(Component.literal("Save"), ignored -> this.save())
                .bounds(0, 0, 70, 18).build());
        this.backButton = this.addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> this.onClose())
                .bounds(0, 0, 70, 18).build());
        this.saveButton.active = this.hasEditPermission();

        for (int index = 0; index < this.rows.size(); index++)
        {
            RowModel model = this.rows.get(index);
            EditBox name = new EditBox(this.font, 0, 0, 160, 18, Component.literal("Player name"));
            name.setMaxLength(40);
            name.setValue(model.name);
            name.setResponder(value -> model.name = value);
            this.addRenderableWidget(name);

            EditBox score = new EditBox(this.font, 0, 0, 100, 18, Component.literal("Score"));
            score.setMaxLength(11);
            String[] lastValidScore = { Integer.toString(model.score) };
            boolean[] correctingScore = { false };
            score.setValue(lastValidScore[0]);
            score.setResponder(value -> {
                if (correctingScore[0])
                {
                    return;
                }
                if (!(value.isEmpty() || value.equals("-") || value.matches("-?\\d{0,10}")))
                {
                    correctingScore[0] = true;
                    score.setValue(lastValidScore[0]);
                    correctingScore[0] = false;
                    return;
                }
                lastValidScore[0] = value;
                try
                {
                    model.score = value.isBlank() || value.equals("-") ? 0 : Integer.parseInt(value);
                }
                catch (NumberFormatException ignored)
                {
                }
            });
            this.addRenderableWidget(score);

            int capturedIndex = index;
            Button delete = this.addRenderableWidget(Button.builder(Component.literal("Delete"), ignored -> this.deleteRow(capturedIndex))
                    .bounds(0, 0, 58, 18).build());
            this.rowWidgets.add(new RowWidgets(name, score, delete));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
    {
        MmmUi.backdrop(context, this.width, this.height);
        MmmUi.drawMmmScreensSidebar(context, this.font, this.width, this.height, mouseX, mouseY, "SCOREBOARD");
        MmmUi.drawMmmTopBar(context, this.font, this.width);
        int x = MmmUi.contentLeft(this.width);
        int y = MmmUi.TOP_BAR_HEIGHT;
        int width = MmmUi.contentWidth(this.width);
        int height = Math.max(1, this.height - y - MmmUi.pagePad(this.width));
        context.enableScissor(x, y, x + width, y + height);
        this.drawContent(context, x, y, width, height);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.disableScissor();
    }

    private void drawContent(GuiGraphicsExtractor context, int x, int viewportY, int width, int viewportHeight)
    {
        int y = viewportY + 16 - (int) Math.round(this.scrollY);
        MmmUi.drawTextWithin(context, this.font, "EDIT SCOREBOARD", x, y, width, MmmUi.accent(), false);
        MmmUi.drawTextWithin(context, this.font, this.objective.getDisplayName().getString(), x, y + 16, width, MmmUi.TEXT, false);
        String helper = this.saveButton.active
                ? "Changes are sent as vanilla scoreboard commands."
                : "Operator permission level 2 is required to save.";
        MmmUi.drawTextWithin(context, this.font, helper, x, y + 30, width, this.saveButton.active ? MmmUi.MUTED : 0xFFFF5965, false);
        if (!this.error.isBlank())
        {
            MmmUi.drawTextWithin(context, this.font, this.error, x, y + 43, width, 0xFFFF5965, false);
        }
        y += 60;

        int gap = 6;
        int addW = Math.min(76, Math.max(52, width / 6));
        int sortW = Math.min(104, Math.max(76, width / 5));
        int directionW = Math.min(86, Math.max(68, width / 5));
        int saveW = Math.min(70, Math.max(50, width / 7));
        int backW = Math.min(70, Math.max(50, width / 7));
        this.addButton.setRectangle(addW, 18, x, y);
        this.sortButton.setRectangle(sortW, 18, x + addW + gap, y);
        this.directionButton.setRectangle(directionW, 18, x + addW + sortW + gap * 2, y);
        this.saveButton.setRectangle(saveW, 18, x + width - backW - saveW - gap, y);
        this.backButton.setRectangle(backW, 18, x + width - backW, y);
        this.setToolbarVisible(y + 18 >= viewportY && y <= viewportY + viewportHeight);
        y += 28;

        int deleteW = 58;
        int scoreW = Math.min(112, Math.max(76, width / 4));
        int nameW = Math.max(80, width - scoreW - deleteW - 16);
        MmmUi.drawTextWithin(context, this.font, "PLAYER / HOLDER", x + 5, y, nameW, MmmUi.MUTED, false);
        MmmUi.drawTextWithin(context, this.font, "SCORE", x + nameW + 10, y, scoreW, MmmUi.MUTED, false);
        y += 14;

        if (this.rows.isEmpty())
        {
            MmmUi.card(context, x, y, width, 46, MmmUi.CARD, MmmUi.BORDER);
            MmmUi.drawTextWithin(context, this.font, "No rows. Use Add Row to create one.", x + 10, y + 17, width - 20, MmmUi.MUTED, false);
            this.contentHeight = y + 58 - viewportY + (int) Math.round(this.scrollY);
            return;
        }

        for (int index = 0; index < this.rows.size(); index++)
        {
            RowWidgets widgets = this.rowWidgets.get(index);
            MmmUi.card(context, x, y, width, ROW_HEIGHT - 3, MmmUi.CARD, MmmUi.BORDER);
            widgets.name.setRectangle(nameW, 18, x + 4, y + 4);
            widgets.score.setRectangle(scoreW, 18, x + nameW + 8, y + 4);
            widgets.delete.setRectangle(deleteW, 18, x + width - deleteW - 4, y + 4);
            boolean visible = y + ROW_HEIGHT >= viewportY && y <= viewportY + viewportHeight;
            widgets.setVisible(visible);
            y += ROW_HEIGHT;
        }
        this.contentHeight = y + 12 - viewportY + (int) Math.round(this.scrollY);
    }

    private void addRow()
    {
        this.rows.add(new RowModel("", 0));
        this.error = "";
        this.rebuildWidgets();
        this.scrollY = Math.max(0, this.contentHeight);
    }

    private void deleteRow(int index)
    {
        if (index >= 0 && index < this.rows.size())
        {
            this.rows.remove(index);
            this.error = "";
            this.rebuildWidgets();
        }
    }

    private void cycleSort()
    {
        this.sortMode = this.sortMode.next();
        this.sortButton.setMessage(Component.literal("Sort: " + this.sortMode.label));
        this.sortRows();
        this.rebuildWidgets();
    }

    private void toggleDirection()
    {
        this.descending = !this.descending;
        this.sortRows();
        this.rebuildWidgets();
    }

    private void sortRows()
    {
        Comparator<RowModel> comparator = switch (this.sortMode)
        {
            case NONE -> null;
            case NAME -> Comparator.comparing(row -> row.name, String.CASE_INSENSITIVE_ORDER);
            case SCORE -> Comparator.comparingInt(row -> row.score);
        };
        if (comparator != null)
        {
            this.rows.sort(this.descending ? comparator.reversed() : comparator);
        }
    }

    private boolean hasEditPermission()
    {
        return this.minecraft != null
                && this.minecraft.player != null
                && this.minecraft.player.permissions()
                .hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
    }

    private void save()
    {
        if (!this.hasEditPermission())
        {
            this.error = "Operator permission level 2 is required.";
            return;
        }
        LinkedHashMap<String, Integer> desired = new LinkedHashMap<>();
        for (RowModel row : this.rows)
        {
            String name = row.name.trim();
            if (name.isEmpty())
            {
                this.error = "Every row needs a player or score-holder name.";
                return;
            }
            if (name.chars().anyMatch(Character::isWhitespace))
            {
                this.error = "Score-holder names cannot contain spaces.";
                return;
            }
            if (desired.putIfAbsent(name, row.score) != null)
            {
                this.error = "Duplicate score-holder: " + name;
                return;
            }
        }

        Map<String, Integer> current = new LinkedHashMap<>();
        for (PlayerScoreEntry entry : this.objective.getScoreboard().listPlayerScores(this.objective))
        {
            if (!entry.isHidden())
            {
                current.put(entry.owner(), entry.value());
            }
        }

        int changes = 0;
        for (String name : current.keySet())
        {
            if (!desired.containsKey(name))
            {
                this.minecraft.player.connection.sendCommand(
                        "scoreboard players reset " + commandToken(name) + " " + commandToken(this.objective.getName()));
                changes++;
            }
        }
        for (Map.Entry<String, Integer> entry : desired.entrySet())
        {
            if (!entry.getValue().equals(current.get(entry.getKey())))
            {
                this.minecraft.player.connection.sendCommand(
                        "scoreboard players set " + commandToken(entry.getKey()) + " "
                                + commandToken(this.objective.getName()) + " " + entry.getValue());
                changes++;
            }
        }
        ScoreboardState.resetPage();
        MmmMessages.actionbar(changes == 0 ? "No scoreboard changes to save" : "Sent %d scoreboard changes", changes);
        this.onClose();
    }

    private static String commandToken(String value)
    {
        if (value.matches("[A-Za-z0-9_+.-]+"))
        {
            return value;
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void setToolbarVisible(boolean visible)
    {
        this.addButton.visible = visible;
        this.sortButton.visible = visible;
        this.directionButton.visible = visible;
        this.saveButton.visible = visible;
        this.backButton.visible = visible;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && MmmUi.handleMmmScreensSidebarClick(this, this.parent, mouseX, mouseY, "SCOREBOARD"))
        {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        int viewportHeight = Math.max(1, this.height - MmmUi.TOP_BAR_HEIGHT - MmmUi.pagePad(this.width));
        int maxScroll = Math.max(0, this.contentHeight - viewportHeight);
        this.scrollY = Math.max(0.0D, Math.min(maxScroll, this.scrollY - verticalAmount * 28.0D));
        return true;
    }

    @Override
    public void onClose()
    {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen()
    {
        return MmmUi.shouldPauseGame();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
    {
    }

    private static final class RowModel
    {
        private String name;
        private int score;

        private RowModel(String name, int score)
        {
            this.name = name;
            this.score = score;
        }
    }

    private record RowWidgets(EditBox name, EditBox score, Button delete)
    {
        private void setVisible(boolean visible)
        {
            this.name.visible = visible;
            this.score.visible = visible;
            this.delete.visible = visible;
        }
    }

    private enum SortMode
    {
        NONE("None"),
        NAME("Name"),
        SCORE("Score");

        private final String label;

        SortMode(String label)
        {
            this.label = label;
        }

        private SortMode next()
        {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }
}
