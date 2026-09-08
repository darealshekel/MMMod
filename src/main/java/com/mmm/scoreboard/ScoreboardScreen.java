package com.mmm.scoreboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mmm.config.Configs;
import com.mmm.ui.MmmUi;

import com.mmm.config.value.IConfigBase;
import com.mmm.config.value.IConfigBoolean;
import com.mmm.config.value.IConfigDouble;
import com.mmm.config.value.IConfigInteger;
import com.mmm.config.value.IConfigOptionListEntry;
import com.mmm.config.value.IConfigResettable;
import com.mmm.config.value.ConfigOptionList;
import com.mmm.util.MmmMessages;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class ScoreboardScreen extends Screen
{
    private static final int TOP_HEIGHT = MmmUi.TOP_BAR_HEIGHT;
    private static final int GAP = 12;
    private static final int CARD_PAD = 12;
    private static final int ROW_HEIGHT = 34;
    private static final int CARD_HEADER = 48;
    private static final int FIELD_HEIGHT = 18;
    private static final int RESET_WIDTH = 18;
    private static final int TWO_COLUMN_MIN_WIDTH = 650;

    private final Screen parent;
    private final List<Section> sections = new ArrayList<>();
    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private final List<SliderTarget> sliderTargets = new ArrayList<>();
    private double scrollY;
    private int contentHeight;
    private SliderTarget draggingSlider;

    public ScoreboardScreen(Screen parent)
    {
        super(Text.literal("Scoreboard"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.clearChildren();
        this.sections.clear();

        this.sections.add(new Section("DISPLAY", "Choose what the sidebar shows.", List.of(
                this.toggle("Show Scoreboard", "Hide or show the whole sidebar.", Configs.Generic.SCOREBOARD_VISIBLE),
                this.toggle("Show Scores", "Show numbers beside each name.", Configs.Generic.SCOREBOARD_SCORES_VISIBLE),
                this.toggle("Score Commas", "Display 100,000 instead of 100000.", Configs.Generic.SCOREBOARD_SCORE_COMMAS),
                this.toggle("Tab List Commas", "Add commas to scores shown in the player list.", Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS),
                this.toggle("Transparent Tab", "Remove the background behind the player list.", Configs.Generic.TRANSPARENT_TAB),
                this.toggle("Tier / Name Tags", "Show website totals before player names.", Configs.Generic.TIER_NAME_TAGS),
                this.toggle("Short Scores", "Display large values as 100k or 2.5M.", Configs.Generic.SCOREBOARD_SCORE_ABBREVIATED))));

        this.sections.add(new Section("LAYOUT", "Control row order, size, and position.", List.of(
                this.action("Move Scoreboard", "Drag and resize the live sidebar.", () -> this.client.setScreen(new ScoreboardMoveScreen(this))),
                this.option("Sort Rows", "Sort by score or player name.", Configs.Generic.SCOREBOARD_SORTING),
                this.slider("Rows Per Page", "Maximum rows visible at once.", Configs.Generic.SCOREBOARD_MAX_ENTRIES, ValueStyle.INTEGER),
                this.option("Position", "Anchor the sidebar to either side.", Configs.Generic.SCOREBOARD_POSITION),
                this.slider("Vertical Offset", "Fine-tune the sidebar height.", Configs.Generic.SCOREBOARD_Y_OFFSET, ValueStyle.SIGNED),
                this.slider("Scale", "Resize the complete sidebar.", Configs.Generic.SCOREBOARD_SCALE, ValueStyle.SCALE))));

        this.sections.add(new Section("APPEARANCE", "Adjust backgrounds and text clarity.", List.of(
                this.slider("Row Background", "Opacity behind scoreboard rows.", Configs.Generic.SCOREBOARD_BODY_OPACITY, ValueStyle.PERCENT),
                this.slider("Title Background", "Opacity behind the title.", Configs.Generic.SCOREBOARD_TITLE_OPACITY, ValueStyle.PERCENT),
                this.slider("Row Text", "Opacity of names and scores.", Configs.Generic.SCOREBOARD_TEXT_OPACITY, ValueStyle.PERCENT),
                this.slider("Title Text", "Opacity of the scoreboard title.", Configs.Generic.SCOREBOARD_TITLE_TEXT_OPACITY, ValueStyle.PERCENT))));

        this.sections.add(new Section("CHAT", "Optional team-chat shortcut.", List.of(
                this.toggle("Team Chat by Default", "Normal messages use /teammsg; # sends public chat.", Configs.Generic.SCOREBOARD_DEFAULT_TEAM_CHAT))));

        this.sections.add(new Section("TOOLS", "Work with the scoreboard currently on screen.", List.of(
                this.action("Previous Page", "Move back through long scoreboards.", this::previousPage),
                this.action("Next Page", "Move forward through long scoreboards.", this::nextPage),
                this.action("Export Current", "Save this objective as a CSV file.", this::exportCurrent),
                this.action("Record Snapshot", "Keep this objective for a combined export.", this::recordCurrent),
                this.action("Manage Records", "Reorder, remove, or export saved snapshots.", () -> this.client.setScreen(new ScoreboardRecordsScreen(this))),
                this.action("Edit Scores", "Edit rows when the server grants permission.", this::openEditor))));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        this.clickTargets.clear();
        this.sliderTargets.clear();
        MmmUi.backdrop(context, this.width, this.height);
        MmmUi.drawMmmScreensSidebar(context, this.textRenderer, this.width, this.height, mouseX, mouseY, "SCOREBOARD");
        MmmUi.drawMmmTopBar(context, this.textRenderer, this.width);

        int viewportX = MmmUi.contentLeft(this.width);
        int viewportY = TOP_HEIGHT;
        int viewportW = MmmUi.contentWidth(this.width);
        int viewportH = Math.max(1, this.height - viewportY - MmmUi.pagePad(this.width));
        context.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        this.layoutAndDraw(context, viewportX, viewportY, viewportW, viewportH, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        context.disableScissor();
        this.drawScrollbar(context, viewportX + viewportW - 3, viewportY, viewportH);
    }

    private void layoutAndDraw(DrawContext context, int x, int viewportY, int width, int viewportH, int mouseX, int mouseY)
    {
        int y = viewportY + 16 - (int) Math.round(this.scrollY);
        MmmUi.drawTextWithin(context, this.textRenderer, "SCOREBOARD", x, y, width, MmmUi.accent(), false);
        MmmUi.drawTextWithin(context, this.textRenderer, "Tune the vanilla sidebar and work with its live data.", x, y + 16, width, MmmUi.MUTED, false);
        y += 46;

        boolean twoColumns = width >= TWO_COLUMN_MIN_WIDTH;
        int columnWidth = twoColumns ? (width - GAP) / 2 : width;
        int leftY = y;
        int rightY = y;
        for (int index = 0; index < this.sections.size(); index++)
        {
            Section section = this.sections.get(index);
            boolean right = twoColumns && index >= 2;
            int sectionX = right ? x + columnWidth + GAP : x;
            int sectionY = right ? rightY : leftY;
            int sectionHeight = CARD_HEADER + section.rows().size() * ROW_HEIGHT + CARD_PAD;
            this.drawSection(context, section, sectionX, sectionY, columnWidth, sectionHeight, viewportY, viewportH, mouseX, mouseY);
            if (right)
            {
                rightY += sectionHeight + GAP;
            }
            else
            {
                leftY += sectionHeight + GAP;
            }
        }
        this.contentHeight = Math.max(leftY, rightY) - viewportY + (int) Math.round(this.scrollY);
    }

    private void drawSection(DrawContext context, Section section, int x, int y, int width, int height, int viewportY, int viewportH, int mouseX, int mouseY)
    {
        MmmUi.card(context, x, y, width, height, MmmUi.CARD, MmmUi.BORDER);
        MmmUi.drawSectionHeading(context, this.textRenderer, section.title(), x + CARD_PAD, y + 12, width - CARD_PAD * 2);
        MmmUi.drawTextWithin(context, this.textRenderer, section.description(), x + CARD_PAD, y + 28, width - CARD_PAD * 2, MmmUi.MUTED, false);

        int rowY = y + CARD_HEADER;
        for (ControlRow row : section.rows())
        {
            this.drawControlRow(context, row, x + CARD_PAD, rowY, width - CARD_PAD * 2,
                    viewportY, viewportH, mouseX, mouseY);
            rowY += ROW_HEIGHT;
        }
    }

    private void drawControlRow(DrawContext context, ControlRow row, int x, int y, int width,
                                int viewportY, int viewportH, int mouseX, int mouseY)
    {
        context.fill(x, y, x + width, y + 1, MmmUi.BORDER_SOFT);
        boolean visible = y + ROW_HEIGHT >= viewportY && y <= viewportY + viewportH;
        boolean configurable = row.config() != null;
        int resetX = x + width - RESET_WIDTH;
        int controlRight = configurable ? resetX - 8 : x + width;
        int controlW = Math.min(132, Math.max(72, width / 2));
        int controlX = Math.max(x, controlRight - controlW);
        int controlY = y + 7;
        int labelW = Math.max(30, controlX - x - 8);

        MmmUi.drawTextWithin(context, this.textRenderer, row.label(), x, y + 7, labelW, MmmUi.TEXT, false);
        MmmUi.drawTextWithin(context, this.textRenderer, row.description(), x, y + 18, labelW, MmmUi.MUTED, false);

        switch (row.kind())
        {
            case BOOLEAN -> this.drawBooleanControl(context, row.config(), controlX, controlY, controlW, mouseX, mouseY, visible);
            case OPTION -> this.drawOptionControl(context, row.config(), controlX, controlY, controlW, mouseX, mouseY, visible);
            case SLIDER -> this.drawSliderControl(context, row.config(), row.valueStyle(), controlX, controlY, controlW, mouseX, mouseY, visible);
            case ACTION -> this.drawActionControl(context, row, controlX, controlY, controlW, mouseX, mouseY, visible);
        }

        IConfigResettable resettable = (IConfigResettable) row.config();
        if (visible && resettable.isModified())
        {
            this.drawButtonShell(context, resetX, controlY, RESET_WIDTH, FIELD_HEIGHT, "R", mouseX, mouseY, true);
            this.clickTargets.add(new ClickTarget(resetX, controlY, RESET_WIDTH, FIELD_HEIGHT, () -> {
                resettable.resetToDefault();
                this.afterConfigChanged(row.config());
            }));
        }
    }

    private void drawBooleanControl(DrawContext context, IConfigBase config, int x, int y, int width,
                                    int mouseX, int mouseY, boolean visible)
    {
        boolean enabled = config instanceof IConfigBoolean booleanConfig && booleanConfig.getBooleanValue();
        boolean hovered = contains(mouseX, mouseY, x, y, width, FIELD_HEIGHT);
        context.fill(x, y, x + width, y + FIELD_HEIGHT, enabled ? MmmUi.accent() : MmmUi.INSET);
        context.drawBorder(x, y, width, FIELD_HEIGHT, hovered || enabled ? MmmUi.accent() : MmmUi.BORDER_SOFT);
        String label = enabled ? "ON" : "OFF";
        int labelX = x + Math.max(4, (width - this.textRenderer.getWidth(label)) / 2);
        context.drawText(this.textRenderer, Text.literal(label), labelX, y + 6,
                enabled ? MmmUi.TEXT : MmmUi.MUTED, false);
        if (visible)
        {
            this.clickTargets.add(new ClickTarget(x, y, width, FIELD_HEIGHT, () -> {
                if (config instanceof IConfigBoolean booleanConfig)
                {
                    booleanConfig.setBooleanValue(!booleanConfig.getBooleanValue());
                    this.afterConfigChanged(config);
                }
            }));
        }
    }

    private void drawOptionControl(DrawContext context, IConfigBase config, int x, int y, int width,
                                   int mouseX, int mouseY, boolean visible)
    {
        String label = config instanceof ConfigOptionList optionList
                ? optionText(optionList).getString() : "-";
        this.drawButtonShell(context, x, y, width, FIELD_HEIGHT, label, mouseX, mouseY, false);
        if (visible)
        {
            this.clickTargets.add(new ClickTarget(x, y, width, FIELD_HEIGHT, () -> {
                if (config instanceof ConfigOptionList optionList)
                {
                    IConfigOptionListEntry entry = optionList.getOptionListValue();
                    optionList.setValueFromString(entry.cycle(true).getStringValue());
                    this.afterConfigChanged(config);
                }
            }));
        }
    }

    private void drawSliderControl(DrawContext context, IConfigBase config, ValueStyle style, int x, int y, int width,
                                   int mouseX, int mouseY, boolean visible)
    {
        double min;
        double max;
        double current;
        if (config instanceof IConfigInteger integerConfig)
        {
            min = integerConfig.getMinIntegerValue();
            max = integerConfig.getMaxIntegerValue();
            current = integerConfig.getIntegerValue();
        }
        else if (config instanceof IConfigDouble doubleConfig)
        {
            min = doubleConfig.getMinDoubleValue();
            max = doubleConfig.getMaxDoubleValue();
            current = doubleConfig.getDoubleValue();
        }
        else
        {
            return;
        }

        double ratio = max <= min ? 0.0D : Math.max(0.0D, Math.min(1.0D, (current - min) / (max - min)));
        int trackY = y + FIELD_HEIGHT / 2 - 1;
        int fillWidth = (int) Math.round(width * ratio);
        boolean hovered = contains(mouseX, mouseY, x, y, width, FIELD_HEIGHT);
        context.fill(x, trackY, x + width, trackY + 3, MmmUi.INSET);
        context.fill(x, trackY, x + fillWidth, trackY + 3, MmmUi.accent());
        context.drawBorder(x, trackY, width, 3, hovered ? MmmUi.accent() : MmmUi.BORDER_SOFT);
        int thumbX = Math.max(x, Math.min(x + width - 4, x + fillWidth - 2));
        context.fill(thumbX, y + 3, thumbX + 4, y + FIELD_HEIGHT - 3, MmmUi.accent());

        String value = formatValue(current, style);
        int valueX = x + Math.max(0, (width - this.textRenderer.getWidth(value)) / 2);
        context.drawText(this.textRenderer, Text.literal(value), valueX, y + 5, MmmUi.TEXT, true);
        if (visible)
        {
            this.sliderTargets.add(new SliderTarget(x, y, width, FIELD_HEIGHT, config));
        }
    }

    private void drawActionControl(DrawContext context, ControlRow row, int x, int y, int width,
                                   int mouseX, int mouseY, boolean visible)
    {
        this.drawButtonShell(context, x, y, width, FIELD_HEIGHT, row.label(), mouseX, mouseY, false);
        if (visible && row.action() != null)
        {
            this.clickTargets.add(new ClickTarget(x, y, width, FIELD_HEIGHT, row.action()));
        }
    }

    private void drawButtonShell(DrawContext context, int x, int y, int width, int height, String label,
                                 int mouseX, int mouseY, boolean subtle)
    {
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        context.fill(x, y, x + width, y + height, subtle ? MmmUi.INSET : hovered ? MmmUi.accentHover() : MmmUi.INSET);
        context.drawBorder(x, y, width, height, hovered ? MmmUi.accent() : MmmUi.BORDER_SOFT);
        String clipped = MmmUi.truncate(this.textRenderer, label, width - 8);
        int labelX = x + Math.max(4, (width - this.textRenderer.getWidth(clipped)) / 2);
        context.drawText(this.textRenderer, Text.literal(clipped), labelX, y + 6,
                hovered ? MmmUi.TEXT : MmmUi.MUTED, false);
    }

    private void updateSlider(SliderTarget target, double mouseX)
    {
        double ratio = target.width() <= 0 ? 0.0D : (mouseX - target.x()) / target.width();
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        IConfigBase config = target.config();
        if (config instanceof IConfigInteger integerConfig)
        {
            int value = (int) Math.round(integerConfig.getMinIntegerValue()
                    + ratio * (integerConfig.getMaxIntegerValue() - integerConfig.getMinIntegerValue()));
            integerConfig.setIntegerValue(value);
        }
        else if (config instanceof IConfigDouble doubleConfig)
        {
            double value = doubleConfig.getMinDoubleValue()
                    + ratio * (doubleConfig.getMaxDoubleValue() - doubleConfig.getMinDoubleValue());
            doubleConfig.setDoubleValue(Math.round(value * 100.0D) / 100.0D);
        }
        this.afterConfigChanged(config);
    }

    private void afterConfigChanged(IConfigBase config)
    {
        if (config == Configs.Generic.SCOREBOARD_POSITION || config == Configs.Generic.SCOREBOARD_Y_OFFSET)
        {
            ScoreboardHudRenderer.resetPosition();
        }
        if (config == Configs.Generic.SCOREBOARD_MAX_ENTRIES
                || config == Configs.Generic.SCOREBOARD_SORTING
                || config == Configs.Generic.SCOREBOARD_POSITION)
        {
            ScoreboardState.resetPage();
        }
        Configs.saveToFile();
    }

    private ControlRow toggle(String label, String description, IConfigBoolean config)
    {
        return new ControlRow(label, description, config, ControlKind.BOOLEAN, null, null);
    }

    private ControlRow option(String label, String description, ConfigOptionList config)
    {
        return new ControlRow(label, description, config, ControlKind.OPTION, null, null);
    }

    private ControlRow slider(String label, String description, IConfigBase config, ValueStyle valueStyle)
    {
        return new ControlRow(label, description, config, ControlKind.SLIDER, valueStyle, null);
    }

    private ControlRow action(String label, String description, Runnable action)
    {
        return new ControlRow(label, description, null, ControlKind.ACTION, null, action);
    }

    private void exportCurrent()
    {
        try
        {
            MmmMessages.actionbar("Exported scoreboard to %s", ScoreboardService.exportCurrent().getFileName().toString());
        }
        catch (IOException exception)
        {
            MmmMessages.actionbar("No scoreboard is available to export");
        }
    }

    private void previousPage()
    {
        MmmMessages.actionbar(ScoreboardService.pageUp() ? "Previous scoreboard page" : "Already on the first scoreboard page");
    }

    private void nextPage()
    {
        MmmMessages.actionbar(ScoreboardService.pageDown() ? "Next scoreboard page" : "Already on the last scoreboard page");
    }

    private void recordCurrent()
    {
        try
        {
            ScoreboardState.Snapshot snapshot = ScoreboardService.recordCurrent();
            MmmMessages.actionbar("Recorded %s (%d rows)", snapshot.displayName(), snapshot.rows().size());
        }
        catch (IOException exception)
        {
            MmmMessages.actionbar("No scoreboard is available to record");
        }
    }

    private void openEditor()
    {
        ScoreboardService.getSidebarObjective(MinecraftClient.getInstance())
                .ifPresentOrElse(objective -> this.client.setScreen(new ScoreboardEditScreen(this, objective)),
                        () -> MmmMessages.actionbar("No scoreboard is available to edit"));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && MmmUi.handleMmmScreensSidebarClick(this, this.parent, mouseX, mouseY, "SCOREBOARD"))
        {
            return true;
        }

        if (button == 0)
        {
            for (SliderTarget target : List.copyOf(this.sliderTargets))
            {
                if (target.contains(mouseX, mouseY))
                {
                    this.draggingSlider = target;
                    this.updateSlider(target, mouseX);
                    return true;
                }
            }
            for (ClickTarget target : List.copyOf(this.clickTargets))
            {
                if (target.contains(mouseX, mouseY))
                {
                    target.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        if (button == 0 && this.draggingSlider != null)
        {
            this.updateSlider(this.draggingSlider, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (button == 0 && this.draggingSlider != null)
        {
            this.updateSlider(this.draggingSlider, mouseX);
            this.draggingSlider = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount)
    {
        int viewportH = Math.max(1, this.height - TOP_HEIGHT - MmmUi.pagePad(this.width));
        int maxScroll = Math.max(0, this.contentHeight - viewportH);
        this.scrollY = Math.max(0.0D, Math.min(maxScroll, this.scrollY - verticalAmount * 28.0D));
        return true;
    }

    private void drawScrollbar(DrawContext context, int x, int y, int height)
    {
        int maxScroll = Math.max(0, this.contentHeight - height);
        if (maxScroll <= 0)
        {
            return;
        }
        int thumbHeight = Math.max(18, height * height / Math.max(height, this.contentHeight));
        int travel = Math.max(1, height - thumbHeight);
        int thumbY = y + (int) Math.round((this.scrollY / maxScroll) * travel);
        context.fill(x, y, x + 2, y + height, MmmUi.BORDER);
        context.fill(x, thumbY, x + 2, thumbY + thumbHeight, MmmUi.accent());
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
    public void renderBackground(DrawContext context)
    {
    }

    private static Text optionText(ConfigOptionList config)
    {
        IConfigOptionListEntry entry = config.getOptionListValue();
        return entry == null ? Text.literal(config.getStringValue()) : Text.literal(entry.getDisplayName());
    }

    private static String formatValue(double current, ValueStyle style)
    {
        return switch (style)
        {
            case INTEGER -> Integer.toString((int) Math.round(current));
            case SIGNED -> String.format(Locale.US, "%+d", (int) Math.round(current));
            case SCALE -> String.format(Locale.US, "%.2fx", current);
            case PERCENT -> Math.round(current * 100.0D) + "%";
        };
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Section(String title, String description, List<ControlRow> rows)
    {
    }

    private record ControlRow(String label, String description, IConfigBase config, ControlKind kind,
                              ValueStyle valueStyle, Runnable action)
    {
    }

    private record ClickTarget(int x, int y, int width, int height, Runnable action)
    {
        private boolean contains(double mouseX, double mouseY)
        {
            return ScoreboardScreen.contains(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }

    private record SliderTarget(int x, int y, int width, int height, IConfigBase config)
    {
        private boolean contains(double mouseX, double mouseY)
        {
            return ScoreboardScreen.contains(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }

    private enum ControlKind
    {
        BOOLEAN,
        OPTION,
        SLIDER,
        ACTION
    }

    private enum ValueStyle
    {
        INTEGER,
        SIGNED,
        SCALE,
        PERCENT
    }

}
