package com.mmm.scoreboard;

import com.mmm.ui.BoardEditorStyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mmm.ui.MmmUi;

import com.mmm.util.MmmMessages;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

public final class ScoreboardEditScreen extends Screen
{
    private static final int ROW_HEIGHT = 30;

    private final Screen parent;
    private final ScoreboardObjective objective;
    private final List<RowModel> rows = new ArrayList<>();
    private final List<RowWidgets> rowWidgets = new ArrayList<>();
    private ButtonWidget addButton;
    private ButtonWidget sortButton;
    private ButtonWidget directionButton;
    private ButtonWidget saveButton;
    private ButtonWidget backButton;
    private SortMode sortMode = SortMode.SCORE;
    private boolean descending = true;
    private double scrollY;
    private int contentHeight;
    private String error = "";

    public ScoreboardEditScreen(Screen parent, ScoreboardObjective objective)
    {
        super(Text.literal("Edit Scoreboard"));
        this.parent = parent;
        this.objective = objective;
        for (ScoreboardEntry entry : objective.getScoreboard().getScoreboardEntries(objective))
        {
            if (!entry.hidden())
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
        this.clearChildren();
        this.backButton = this.addSelectableChild(MmmUi.backButton(this, MmmUi.contentLeft(this.width)));
        this.rowWidgets.clear();
        this.addButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Add Row"), ignored -> this.addRow())
                .dimensions(0, 0, 76, 18).build());
        this.sortButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Sort: " + this.sortMode.label), ignored -> this.cycleSort())
                .dimensions(0, 0, 96, 18).build());
        this.directionButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(this.descending ? "Descending" : "Ascending"), ignored -> this.toggleDirection())
                .dimensions(0, 0, 82, 18).build());
        this.saveButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), ignored -> this.save())
                .dimensions(0, 0, 70, 18).build());
        this.saveButton.active = this.client != null && this.client.player != null && this.client.player.hasPermissionLevel(2);

        for (int index = 0; index < this.rows.size(); index++)
        {
            RowModel model = this.rows.get(index);
            TextFieldWidget name = new TextFieldWidget(this.textRenderer, 0, 0, 160, 18, Text.literal("Player name"));
            name.setDrawsBackground(false);
            name.setMaxLength(40);
            name.setText(model.name);
            name.setChangedListener(value -> model.name = value);
            this.addDrawableChild(name);

            TextFieldWidget score = new TextFieldWidget(this.textRenderer, 0, 0, 100, 18, Text.literal("Score"));
            score.setDrawsBackground(false);
            score.setMaxLength(11);
            score.setTextPredicate(value -> value.isEmpty() || value.equals("-") || value.matches("-?\\d{0,10}"));
            score.setText(Integer.toString(model.score));
            score.setChangedListener(value -> {
                try
                {
                    model.score = value.isBlank() || value.equals("-") ? 0 : Integer.parseInt(value);
                }
                catch (NumberFormatException ignored)
                {
                }
            });
            this.addDrawableChild(score);

            int capturedIndex = index;
            ButtonWidget delete = this.addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), ignored -> this.deleteRow(capturedIndex))
                    .dimensions(0, 0, 58, 18).build());
            this.rowWidgets.add(new RowWidgets(name, score, delete));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.toolBoard(context, this.textRenderer, this.width, this.height, MmmUi.contentLeft(this.width), "Scoreboard / Edit");
        int x = MmmUi.contentLeft(this.width);
        int y = MmmUi.TOP_BAR_HEIGHT;
        int width = MmmUi.contentWidth(this.width);
        int height = Math.max(1, this.height - y - MmmUi.pagePad(this.width));
        context.enableScissor(x, y, x + width, y + height);
        this.drawContent(context, x, y, width, height);
        super.render(context, mouseX, mouseY, delta);
        context.disableScissor();
        this.backButton.render(context, mouseX, mouseY, delta);
    }

    private void drawContent(DrawContext context, int x, int viewportY, int width, int viewportHeight)
    {
        boolean wrap = width < 380;
        int headingHeight = this.error.isBlank() ? 35 : 48;
        int toolbarHeight = (wrap ? 22 : 0) + 28;
        this.contentHeight = 16 + headingHeight + toolbarHeight + 14
                + (this.rows.isEmpty() ? 58 : this.rows.size() * ROW_HEIGHT + 12);
        this.scrollY = Math.clamp(this.scrollY, 0.0D, Math.max(0, this.contentHeight - viewportHeight));
        int y = viewportY + 16 - (int) Math.round(this.scrollY);
        MmmUi.drawTextWithin(context, this.textRenderer, this.objective.getDisplayName().getString(), x, y, width, BoardEditorStyle.TEXT, false);
        String helper = this.saveButton.active
                ? "Changes are sent as vanilla scoreboard commands."
                : "Operator permission level 2 is required to save.";
        MmmUi.drawTextWithin(context, this.textRenderer, helper, x, y + 15, width, this.saveButton.active ? BoardEditorStyle.MUTED : 0xFFFF5965, false);
        if (!this.error.isBlank())
        {
            MmmUi.drawTextWithin(context, this.textRenderer, this.error, x, y + 28, width, 0xFFFF5965, false);
        }
        y += headingHeight;

        int gap = 6;
        int cell = (width - gap * (wrap ? 2 : 3)) / (wrap ? 3 : 4);
        int actionY = wrap ? y + 22 : y;
        this.addButton.setDimensionsAndPosition(cell, 18, x, y);
        this.sortButton.setDimensionsAndPosition(cell, 18, x + cell + gap, y);
        this.directionButton.setDimensionsAndPosition(cell, 18, x + (cell + gap) * 2, y);
        this.saveButton.setDimensionsAndPosition(cell, 18, x + width - cell, actionY);
        this.setToolbarVisible(actionY + 18 >= viewportY && y <= viewportY + viewportHeight);
        y = actionY + 28;

        int deleteW = Math.min(58, width / 4);
        int scoreW = Math.min(112, width / 4);
        int nameW = Math.max(1, width - scoreW - deleteW - 16);
        context.fill(x, y - 4, x + width, y - 3, BoardEditorStyle.LINE);
        MmmUi.drawTextWithin(context, this.textRenderer, "PLAYER / HOLDER", x + 5, y, nameW, BoardEditorStyle.MUTED, false);
        MmmUi.drawTextWithin(context, this.textRenderer, "SCORE", x + nameW + 10, y, scoreW, BoardEditorStyle.MUTED, false);
        y += 14;

        if (this.rows.isEmpty())
        {
            MmmUi.boardRow(context, x, y, width, 46, false, false);
            MmmUi.drawTextWithin(context, this.textRenderer, "No rows. Use Add Row to create one.", x + 10, y + 17, width - 20, BoardEditorStyle.MUTED, false);
            return;
        }

        for (int index = 0; index < this.rows.size(); index++)
        {
            RowWidgets widgets = this.rowWidgets.get(index);
            MmmUi.boardRow(context, x, y, width, ROW_HEIGHT, false, false);
            fieldRule(context, x + 4, y + 19, nameW, widgets.name.isFocused());
            fieldRule(context, x + nameW + 8, y + 19, scoreW, widgets.score.isFocused());
            widgets.name.setDimensionsAndPosition(nameW - 4, 13, x + 6, y + 7);
            widgets.score.setDimensionsAndPosition(scoreW - 4, 13, x + nameW + 10, y + 7);
            widgets.delete.setDimensionsAndPosition(deleteW, 18, x + width - deleteW - 4, y + 4);
            boolean visible = y + ROW_HEIGHT >= viewportY && y <= viewportY + viewportHeight;
            widgets.setVisible(visible);
            y += ROW_HEIGHT;
        }
    }

    private static void fieldRule(DrawContext context, int x, int y, int width, boolean focused)
    {
        context.fill(x, y, x + width, y + 1, focused ? BoardEditorStyle.ROSE : BoardEditorStyle.LINE);
    }

    private void addRow()
    {
        this.rows.add(new RowModel("", 0));
        this.error = "";
        this.clearAndInit();
        this.scrollY = this.contentHeight + ROW_HEIGHT;
    }

    private void deleteRow(int index)
    {
        if (index >= 0 && index < this.rows.size())
        {
            this.rows.remove(index);
            this.error = "";
            this.clearAndInit();
        }
    }

    private void cycleSort()
    {
        this.sortMode = this.sortMode.next();
        this.sortButton.setMessage(Text.literal("Sort: " + this.sortMode.label));
        this.sortRows();
        this.clearAndInit();
    }

    private void toggleDirection()
    {
        this.descending = !this.descending;
        this.sortRows();
        this.clearAndInit();
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

    private void save()
    {
        if (this.client == null || this.client.player == null || !this.client.player.hasPermissionLevel(2))
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
        for (ScoreboardEntry entry : this.objective.getScoreboard().getScoreboardEntries(this.objective))
        {
            if (!entry.hidden())
            {
                current.put(entry.owner(), entry.value());
            }
        }

        int changes = 0;
        for (String name : current.keySet())
        {
            if (!desired.containsKey(name))
            {
                this.client.player.networkHandler.sendChatCommand(
                        "scoreboard players reset " + commandToken(name) + " " + commandToken(this.objective.getName()));
                changes++;
            }
        }
        for (Map.Entry<String, Integer> entry : desired.entrySet())
        {
            if (!entry.getValue().equals(current.get(entry.getKey())))
            {
                this.client.player.networkHandler.sendChatCommand(
                        "scoreboard players set " + commandToken(entry.getKey()) + " "
                                + commandToken(this.objective.getName()) + " " + entry.getValue());
                changes++;
            }
        }
        ScoreboardState.resetPage();
        MmmMessages.actionbar(changes == 0 ? "No scoreboard changes to save" : "Sent %d scoreboard changes", changes);
        this.close();
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
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (mouseY < MmmUi.TOP_BAR_HEIGHT && !this.backButton.isMouseOver(mouseX, mouseY)) return false;
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
    public void close()
    {
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean shouldPause()
    {
        return MmmUi.shouldPauseGame();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta)
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

    private record RowWidgets(TextFieldWidget name, TextFieldWidget score, ButtonWidget delete)
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
