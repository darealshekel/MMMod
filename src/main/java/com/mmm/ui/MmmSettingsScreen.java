package com.mmm.ui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mmm.Reference;
import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.hud.HudMoveScreen;
import com.mmm.hud.SummaryScreen;
import com.mmm.sync.CloudSyncManager;
import com.mmm.tracker.MiningStats;

import com.mmm.config.value.IConfigBase;
import com.mmm.config.value.IConfigBoolean;
import com.mmm.config.value.IConfigDouble;
import com.mmm.config.value.IConfigInteger;
import com.mmm.config.value.IConfigOptionListEntry;
import com.mmm.config.value.IConfigResettable;
import com.mmm.config.value.IStringRepresentable;
import com.mmm.config.value.ConfigColor;
import com.mmm.config.value.ConfigOptionList;
import com.mmm.util.MmmMessages;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class MmmSettingsScreen extends Screen
{
    private static final int BG = 0xFF050505;
    private static final int TOP_BAR = 0xF0060606;
    private static final int CARD = 0xF20D0D0D;
    private static final int INSET = 0xFF121212;
    private static final int BORDER = 0xFF1F1F1F;
    private static final int BORDER_SOFT = 0xFF272727;
    private static final int TEXT = 0xFFF5F5F5;
    private static final int MUTED = 0xFF949494;
    private static final int GREEN = 0xFF43D483;
    private static final int ERROR = 0xFFFF5965;
    private static final int WARNING = 0xFFFFB84D;

    private static final int TOP_HEIGHT = 42;
    private static final int GAP = 12;
    private static final int CARD_PAD = 12;
    private static final int ROW_HEIGHT = 32;
    private static final int FIELD_HEIGHT = 18;
    private static final int CONTROL_WIDTH = 112;
    private static final int RESET_WIDTH = 18;
    private static final int TWO_COLUMN_MIN_WIDTH = 680;
    private static final int CONTENT_HEADER_HEIGHT = 68;
    private static final boolean SPEED_GRAPH_AVAILABLE = false;
    private static final long OPENING_HOTKEY_SUPPRESSION_TIMEOUT_MS = 750L;

    private final Screen parent;
    private final List<SettingsSection> sections = new ArrayList<>();
    private final List<VisibleSection> visibleSections = new ArrayList<>();
    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private final List<ScrollTarget> scrollTargets = new ArrayList<>();
    private final List<SliderTarget> sliderTargets = new ArrayList<>();
    private final Map<IConfigBase, TextFieldWidget> textFields = new IdentityHashMap<>();
    private double scrollY = 0.0D;
    private int contentHeight = 0;
    private SliderTarget draggingSlider;
    private TextFieldWidget searchField;
    private String searchQuery = "";
    private boolean suppressAutomaticSearchInput;
    private final long openedAtMs;

    public MmmSettingsScreen(Screen parent)
    {
        this(parent, false);
    }

    public MmmSettingsScreen(Screen parent, boolean openedFromHotkey)
    {
        super(Text.literal("MMM Mod Settings"));
        this.parent = parent;
        this.suppressAutomaticSearchInput = openedFromHotkey;
        this.openedAtMs = System.currentTimeMillis();
        this.createSections();
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.clearChildren();
        this.textFields.clear();

        this.searchField = new TextFieldWidget(this.textRenderer, 0, 0, 220, FIELD_HEIGHT, Text.literal("Search settings"));
        this.searchField.setDrawsBackground(false);
        this.searchField.setEditableColor(TEXT);
        this.searchField.setMaxLength(64);
        this.searchField.setPlaceholder(Text.literal("Search settings..."));
        this.searchField.setText(this.searchQuery);
        this.searchField.setChangedListener(value -> {
            this.searchQuery = value;
            this.scrollY = 0.0D;
        });
        this.addDrawableChild(this.searchField);

        for (SettingsSection section : this.sections)
        {
            for (SettingRow row : section.rows())
            {
                if (row.kind().usesTextField())
                {
                    TextFieldWidget field = new TextFieldWidget(this.textRenderer, 0, 0, CONTROL_WIDTH, FIELD_HEIGHT, Text.empty());
                    field.setDrawsBackground(false);

                    field.setEditableColor(TEXT);
                    field.setUneditableColor(MUTED);
                    field.setMaxLength(row.kind() == ControlKind.COLOR ? 9 : 64);
                    field.setText(this.getConfigString(row.config()));
                    field.setChangedListener(value -> this.commitTextValue(row, value));
                    this.textFields.put(row.config(), field);
                    this.addDrawableChild(field);
                }
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        this.clickTargets.clear();
        this.scrollTargets.clear();
        this.sliderTargets.clear();
        for (TextFieldWidget field : this.textFields.values())
        {
            field.setVisible(false);
        }
        this.searchField.setVisible(true);
        this.refreshVisibleSections();
        this.updateLayout();

        context.fill(0, 0, this.width, this.height, MmmUi.menuSurface(BG));
        this.drawSidebar(context, mouseX, mouseY);
        this.drawTopBar(context, mouseX, mouseY);

        int pagePad = MmmUi.pagePad(this.width);
        int viewportX = MmmUi.contentLeft(this.width);
        int viewportY = TOP_HEIGHT;
        int viewportW = MmmUi.contentWidth(this.width);
        int viewportH = Math.max(1, this.height - TOP_HEIGHT - pagePad);

        context.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        this.drawMainContent(context, viewportX, viewportY, viewportW, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        context.disableScissor();

        this.drawScrollbar(context, viewportX + viewportW - 4, viewportY, viewportH);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && this.searchField != null && this.searchField.isMouseOver(mouseX, mouseY))
        {
            this.suppressAutomaticSearchInput = false;
        }

        if (button == 0 && MmmUi.handleMmmScreensSidebarClick(this, this.parent, mouseX, mouseY, "SETTINGS"))
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
        }

        for (ClickTarget target : List.copyOf(this.clickTargets))
        {
            if (target.contains(mouseX, mouseY))
            {
                target.action().run(mouseX, mouseY);
                return true;
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        for (ScrollTarget target : List.copyOf(this.scrollTargets))
        {
            if (target.contains(mouseX, mouseY))
            {
                this.adjustNumberByScroll(target.config(), verticalAmount);
                return true;
            }
        }

        int viewportH = Math.max(1, this.height - TOP_HEIGHT - MmmUi.pagePad(this.width));
        int maxScroll = Math.max(0, this.contentHeight - viewportH);
        this.scrollY = Math.max(0.0D, Math.min(maxScroll, this.scrollY - verticalAmount * 28.0D));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (this.suppressAutomaticSearchInput
                && System.currentTimeMillis() - this.openedAtMs >= OPENING_HOTKEY_SUPPRESSION_TIMEOUT_MS)
        {
            this.suppressAutomaticSearchInput = false;
        }

        if (keyCode == 256)
        {
            if (this.searchField != null && this.searchField.isFocused() && this.searchField.getText().isBlank() == false)
            {
                this.searchField.setText("");
                return true;
            }
            this.close();
            return true;
        }

        if (this.searchField != null
                && this.searchField.isVisible()
                && this.searchField.isFocused()
                && this.searchField.keyPressed(keyCode, scanCode, modifiers))
        {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers)
    {
        this.suppressAutomaticSearchInput = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers)
    {
        if (this.suppressAutomaticSearchInput)
        {
            if (System.currentTimeMillis() - this.openedAtMs < OPENING_HOTKEY_SUPPRESSION_TIMEOUT_MS)
            {
                return true;
            }
            this.suppressAutomaticSearchInput = false;
        }

        if (super.charTyped(chr, modifiers))
        {
            return true;
        }
        for (TextFieldWidget field : this.textFields.values())
        {
            if (field.isFocused())
            {
                return false;
            }
        }
        if (this.searchField != null
                && this.searchField.isVisible()
                && (Character.isLetterOrDigit(chr) || Character.isWhitespace(chr)))
        {
            this.searchField.setFocused(true);
            return this.searchField.charTyped(chr, modifiers);
        }
        return false;
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

    private void drawTopBar(DrawContext context, int mouseX, int mouseY)
    {
        context.fill(0, 0, this.width, TOP_HEIGHT, MmmUi.menuSurface(TOP_BAR));
        context.drawBorder(0, 0, this.width, TOP_HEIGHT, BORDER);
        context.fill(14, 12, 18, 30, MmmUi.accent());
        MmmUi.drawTextWithin(context, this.textRenderer, "MMM", 26, 10, 40, MmmUi.accent(), false);

        String status = this.syncStatusText();
        int statusColor = Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue() ? GREEN : MUTED;
        int closeX = this.width - 32;
        int statusLeft = this.width >= 620 ? this.width / 2 : 68;
        if (this.width >= 620)
        {
            MmmUi.drawTextWithin(context, this.textRenderer, "Manual Mining Maniacs", 68, 10, Math.max(0, statusLeft - 80), TEXT, false);
        }
        MmmUi.drawTextRightWithin(context, this.textRenderer, status, closeX - 12, 10, Math.max(0, closeX - 12 - statusLeft), statusColor, false);
        this.drawButtonShell(context, closeX, 8, 20, 20, "X", mouseX, mouseY, false);
        this.clickTargets.add(new ClickTarget(closeX, 8, 20, 20, this::close));
    }

    private void drawSidebar(DrawContext context, int mouseX, int mouseY)
    {
        MmmUi.drawMmmScreensSidebar(context, this.textRenderer, this.width, this.height, mouseX, mouseY, "SETTINGS");
    }

    private void drawMainContent(DrawContext context, int x, int viewportY, int width, int mouseX, int mouseY)
    {
        int y = viewportY + 18 - (int) Math.round(this.scrollY);
        MmmUi.drawTextWithin(context, this.textRenderer, "MMM MOD SETTINGS", x, y, width, MmmUi.accent(), false);
        MmmUi.drawTextWithin(context, this.textRenderer, "Configure how MMM Mod works in Minecraft", x, y + 16, width, MUTED, false);

        int searchY = y + 34;
        int searchWidth = Math.min(260, width);
        boolean canClearSearch = this.searchQuery.isBlank() == false;
        int clearWidth = canClearSearch ? 20 : 0;
        this.searchField.setX(x + 5);
        this.searchField.setY(searchY + 5);
        this.searchField.setWidth(Math.max(40, searchWidth - 10 - clearWidth));
        MmmUi.fieldShell(context, x, searchY, searchWidth, FIELD_HEIGHT, this.searchField.isFocused());
        if (canClearSearch)
        {
            int clearX = x + searchWidth - 19;
            this.drawButtonShell(context, clearX, searchY, 18, FIELD_HEIGHT, "X", mouseX, mouseY, true);
            this.clickTargets.add(new ClickTarget(clearX, searchY, 18, FIELD_HEIGHT, () -> {
                this.searchField.setText("");
                this.searchField.setFocused(true);
            }));
        }

        int gridY = y + CONTENT_HEADER_HEIGHT;
        if (this.visibleSections.isEmpty())
        {
            MmmUi.drawTextWithin(context, this.textRenderer, "No settings match your search.", x, gridY + 8, width, MUTED, false);
            return;
        }

        boolean twoColumns = width >= TWO_COLUMN_MIN_WIDTH;
        int columnW = twoColumns ? (width - GAP) / 2 : width;
        int rowY = gridY;
        int sectionIndex = 0;

        while (sectionIndex < this.visibleSections.size())
        {
            VisibleSection left = this.visibleSections.get(sectionIndex++);
            VisibleSection right = twoColumns && sectionIndex < this.visibleSections.size() ? this.visibleSections.get(sectionIndex++) : null;
            int leftHeight = this.sectionHeight(left);
            int rightHeight = right == null ? 0 : this.sectionHeight(right);
            int rowHeight = Math.max(leftHeight, rightHeight);

            this.drawSection(context, left, x, rowY, columnW, rowHeight, mouseX, mouseY);

            if (right != null)
            {
                this.drawSection(context, right, x + columnW + GAP, rowY, columnW, rowHeight, mouseX, mouseY);
            }

            rowY += rowHeight + GAP;
        }
    }

    private void drawSection(DrawContext context, VisibleSection visibleSection, int x, int y, int width, int height, int mouseX, int mouseY)
    {
        SettingsSection section = visibleSection.section();
        context.fill(x, y, x + width, y + height, MmmUi.menuSurface(CARD));
        context.drawBorder(x, y, width, height, BORDER);
        MmmUi.drawSectionHeading(context, this.textRenderer, section.title(), x + CARD_PAD, y + 12, width - CARD_PAD * 2);
        MmmUi.drawTextWithin(context, this.textRenderer, section.description(), x + CARD_PAD, y + 28, width - CARD_PAD * 2, MUTED, false);

        int rowY = y + 48;
        for (SettingRow row : visibleSection.rows())
        {
            this.drawSettingRow(context, row, x + CARD_PAD, rowY, width - CARD_PAD * 2, mouseX, mouseY);
            rowY += ROW_HEIGHT;
        }
    }

    private void drawSettingRow(DrawContext context, SettingRow row, int x, int y, int width, int mouseX, int mouseY)
    {
        context.fill(x, y, x + width, y + 1, BORDER);
        if (row.kind() == ControlKind.HEADING)
        {
            context.fill(x, y + 8, x + 3, y + 24, MmmUi.accent());
            MmmUi.drawTextWithin(context, this.textRenderer, row.label(), x + 10, y + 12, width - 10, TEXT, false);
            return;
        }
        if (row.kind() == ControlKind.STATUS)
        {
            this.drawSyncStatusRow(context, x, y, width);
            return;
        }

        int resetX = x + width - RESET_WIDTH;
        int controlWidth = Math.min(CONTROL_WIDTH, Math.max(48, width / 2));
        int controlX = resetX - controlWidth - 8;
        int controlY = y + 7;
        int labelW = Math.max(0, controlX - x - 8);
        MmmUi.drawTextWithin(context, this.textRenderer, row.label(), x, y + 7, labelW, TEXT, false);
        MmmUi.drawTextWithin(context, this.textRenderer, row.description(), x, y + 18, labelW, MUTED, false);

        switch (row.kind())
        {
            case BOOLEAN -> this.drawBooleanControl(context, row.config(), controlX, controlY, controlWidth, mouseX, mouseY);
            case TEXT, NUMBER, COLOR -> this.drawTextControl(context, row, controlX, controlY, controlWidth, mouseX, mouseY);
            case OPTION -> this.drawOptionControl(context, row.config(), controlX, controlY, controlWidth, mouseX, mouseY);
            case SLIDER -> this.drawSliderControl(context, row.config(), controlX, controlY, controlWidth, mouseX, mouseY);
            case HEADING, STATUS -> {}
            case ACTION -> this.drawActionButton(context, controlX, controlY, controlWidth, FIELD_HEIGHT, this.actionButtonLabel(row), mouseX, mouseY, () -> {
                if ("Move HUD".equals(row.label()))
                {
                    MinecraftClient.getInstance().setScreen(new HudMoveScreen(this));
                }
                else if ("Custom Sounds".equals(row.label()))
                {
                    MinecraftClient.getInstance().setScreen(new GoalSoundSettingsScreen(this));
                }
                else if ("Perimeter Block List".equals(row.label()))
                {
                    MinecraftClient.getInstance().setScreen(new PerimeterBlockListScreen(this));
                }
                else if ("Ignored Players".equals(row.label()))
                {
                    MinecraftClient.getInstance().setScreen(new MmmChatIgnoreListScreen(this));
                }
                else if ("Sync Scoreboard".equals(row.label()))
                {
                    MinecraftClient.getInstance().setScreen(new SyncScoreboardScreen(this));
                }
                else if ("Generate Dev Run".equals(row.label()))
                {
                    MmmMessages.actionbar("Generated preview mining run");
                    MinecraftClient.getInstance().setScreen(new SummaryScreen(MiningStats.simulateDevFinishedSession(), this));
                }
            });
        }

        if (row.kind() != ControlKind.ACTION && row.config() instanceof IConfigResettable resettable && resettable.isModified())
        {
            this.drawButtonShell(context, resetX, controlY, RESET_WIDTH, FIELD_HEIGHT, "R", mouseX, mouseY, true);
            this.clickTargets.add(new ClickTarget(resetX, controlY, RESET_WIDTH, FIELD_HEIGHT, () -> {
                resettable.resetToDefault();
                this.syncField(row.config());
                Configs.saveToFile();
            }));
        }
    }

    private void drawBooleanControl(DrawContext context, IConfigBase config, int x, int y, int width, int mouseX, int mouseY)
    {
        boolean enabled = config instanceof IConfigBoolean booleanConfig && booleanConfig.getBooleanValue();
        boolean hovered = this.contains(mouseX, mouseY, x, y, width, FIELD_HEIGHT);
        int fill = enabled ? MmmUi.accent() : INSET;
        int border = hovered ? MmmUi.accent() : BORDER_SOFT;
        context.fill(x, y, x + width, y + FIELD_HEIGHT, enabled ? fill : MmmUi.menuSurface(fill));
        context.drawBorder(x, y, width, FIELD_HEIGHT, border);
        String label = enabled ? "ON" : "OFF";
        int labelW = this.textRenderer.getWidth(label);
        context.drawText(this.textRenderer, Text.literal(label), x + (width - labelW) / 2, y + 6, enabled ? TEXT : MUTED, false);
        this.clickTargets.add(new ClickTarget(x, y, width, FIELD_HEIGHT, () -> {
            if (config instanceof IConfigBoolean booleanConfig)
            {
                booleanConfig.setBooleanValue(!booleanConfig.getBooleanValue());
                Configs.saveToFile();
            }
        }));
    }

    private void drawOptionControl(DrawContext context, IConfigBase config, int x, int y, int width, int mouseX, int mouseY)
    {
        String label = this.getConfigString(config);
        if (config instanceof ConfigOptionList optionList && optionList.getOptionListValue() instanceof IConfigOptionListEntry entry)
        {
            label = entry.getDisplayName();
        }

        this.drawButtonShell(context, x, y, width, FIELD_HEIGHT, label, mouseX, mouseY, false);
        this.clickTargets.add(new ClickTarget(x, y, width, FIELD_HEIGHT, () -> {
            if (config instanceof ConfigOptionList optionList && optionList.getOptionListValue() instanceof IConfigOptionListEntry entry)
            {
                IConfigOptionListEntry next = entry.cycle(true);
                if (config instanceof IStringRepresentable representable)
                {
                    representable.setValueFromString(next.getStringValue());
                }
                Configs.saveToFile();
            }
        }));
    }

    private void drawTextControl(DrawContext context, SettingRow row, int x, int y, int width, int mouseX, int mouseY)
    {
        TextFieldWidget field = this.textFields.get(row.config());
        if (field == null)
        {
            return;
        }
        field.setVisible(true);

        int fieldX = x;
        int fieldW = width;
        if (row.kind() == ControlKind.COLOR)
        {
            fieldX = x + FIELD_HEIGHT + 4;
            fieldW = width - FIELD_HEIGHT - 4;
            int preview = this.getColorPreview(row.config(), MmmUi.accent());
            context.fill(x, y, x + FIELD_HEIGHT, y + FIELD_HEIGHT, preview);
            context.drawBorder(x, y, FIELD_HEIGHT, FIELD_HEIGHT, BORDER_SOFT);
            this.clickTargets.add(new ClickTarget(x, y, FIELD_HEIGHT, FIELD_HEIGHT, () -> {
                this.openColorEditor(row.config());
            }));
        }
        else if (row.kind() == ControlKind.NUMBER)
        {
            this.scrollTargets.add(new ScrollTarget(fieldX, y, width, FIELD_HEIGHT, row.config()));
        }

        field.setX(fieldX + 5);
        field.setY(y + 5);
        field.setWidth(Math.max(24, fieldW - 10));
        field.setEditableColor(TEXT);
        field.setUneditableColor(MUTED);
        MmmUi.fieldShell(context, fieldX, y, fieldW, FIELD_HEIGHT, field.isFocused());
    }

    private void drawSliderControl(DrawContext context, IConfigBase config, int x, int y, int width, int mouseX, int mouseY)
    {
        double min;
        double max;
        double current;
        String value;
        if (config instanceof IConfigDouble doubleConfig)
        {
            min = doubleConfig.getMinDoubleValue();
            max = doubleConfig.getMaxDoubleValue();
            current = doubleConfig.getDoubleValue();
            value = Math.round(current * 100.0D) + "%";
        }
        else if (config instanceof IConfigInteger integerConfig)
        {
            min = integerConfig.getMinIntegerValue();
            max = integerConfig.getMaxIntegerValue();
            current = integerConfig.getIntegerValue();
            value = config == Configs.Generic.MENU_OPACITY
                    ? integerConfig.getIntegerValue() + "%"
                    : Integer.toString(integerConfig.getIntegerValue());
        }
        else
        {
            return;
        }

        double ratio = max <= min ? 0.0D : (current - min) / (max - min);
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        int trackY = y + FIELD_HEIGHT / 2 - 1;
        int fillWidth = (int) Math.round(width * ratio);
        boolean hovered = this.contains(mouseX, mouseY, x, y, width, FIELD_HEIGHT);

        context.fill(x, trackY, x + width, trackY + 3, MmmUi.menuSurface(INSET));
        context.fill(x, trackY, x + fillWidth, trackY + 3, MmmUi.accent());
        context.drawBorder(x, trackY, width, 3, hovered ? MmmUi.accent() : BORDER_SOFT);
        int thumbX = Math.max(x, Math.min(x + width - 4, x + fillWidth - 2));
        context.fill(thumbX, y + 3, thumbX + 4, y + FIELD_HEIGHT - 3, MmmUi.accent());

        int valueWidth = this.textRenderer.getWidth(value);
        context.drawText(this.textRenderer, Text.literal(value), x + Math.max(0, (width - valueWidth) / 2), y + 5, TEXT, true);
        this.sliderTargets.add(new SliderTarget(x, y, width, FIELD_HEIGHT, config));
    }

    private void updateSlider(SliderTarget target, double mouseX)
    {
        double ratio = target.width() <= 0 ? 0.0D : (mouseX - target.x()) / target.width();
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        IConfigBase config = target.config();
        if (config instanceof IConfigDouble doubleConfig)
        {
            double value = doubleConfig.getMinDoubleValue() + ratio * (doubleConfig.getMaxDoubleValue() - doubleConfig.getMinDoubleValue());
            doubleConfig.setDoubleValue(Math.round(value * 100.0D) / 100.0D);
        }
        else if (config instanceof IConfigInteger integerConfig)
        {
            int value = (int) Math.round(integerConfig.getMinIntegerValue()
                    + ratio * (integerConfig.getMaxIntegerValue() - integerConfig.getMinIntegerValue()));
            integerConfig.setIntegerValue(value);
        }
        Configs.saveToFile();
    }

    private void openColorEditor(IConfigBase config)
    {
        if (!(config instanceof ConfigColor colorConfig))
        {
            return;
        }

        MinecraftClient.getInstance().setScreen(new MmmColorEditorScreen(this, colorConfig, () -> this.syncField(config)));
    }
    private void drawActionButton(DrawContext context, int x, int y, int width, int height, String label, int mouseX, int mouseY, Runnable action)
    {
        this.drawButtonShell(context, x, y, width, height, label, mouseX, mouseY, false);
        this.clickTargets.add(new ClickTarget(x, y, width, height, action));
    }

    private String actionButtonLabel(SettingRow row)
    {
        return "Generate Dev Run".equals(row.label()) ? "RUN" : "OPEN";
    }

    private void drawButtonShell(DrawContext context, int x, int y, int width, int height, String label, int mouseX, int mouseY, boolean subtle)
    {
        boolean hovered = this.contains(mouseX, mouseY, x, y, width, height);
        int fill = subtle ? INSET : hovered ? MmmUi.accentHover() : INSET;
        int border = hovered ? MmmUi.accent() : BORDER_SOFT;
        context.fill(x, y, x + width, y + height, MmmUi.menuSurface(fill));
        context.drawBorder(x, y, width, height, border);
        String clipped = MmmUi.truncate(this.textRenderer, label, width - 8);
        int textX = x + Math.max(4, (width - this.textRenderer.getWidth(clipped)) / 2);
        context.drawText(this.textRenderer, Text.literal(clipped), textX, y + 6, hovered ? TEXT : MUTED, false);
    }

    private void drawScrollbar(DrawContext context, int x, int y, int height)
    {
        int maxScroll = Math.max(0, this.contentHeight - height);
        if (maxScroll <= 0)
        {
            return;
        }

        context.fill(x, y, x + 3, y + height, 0x55090909);
        int thumbH = Math.max(28, (int) (height * (height / (double) this.contentHeight)));
        int thumbY = y + (int) ((height - thumbH) * (this.scrollY / maxScroll));
        context.fill(x, thumbY, x + 3, thumbY + thumbH, MmmUi.accent());
    }

    private void updateLayout()
    {
        int rowY = CONTENT_HEADER_HEIGHT;
        int index = 0;
        boolean twoColumns = MmmUi.contentWidth(this.width) >= TWO_COLUMN_MIN_WIDTH;

        while (index < this.visibleSections.size())
        {
            VisibleSection left = this.visibleSections.get(index++);
            VisibleSection right = twoColumns && index < this.visibleSections.size() ? this.visibleSections.get(index++) : null;
            int rowHeight = this.sectionHeight(left);
            if (right != null)
            {
                rowHeight = Math.max(rowHeight, this.sectionHeight(right));
            }
            if (right != null)
            {
            }
            rowY += rowHeight + GAP;
        }
        this.contentHeight = rowY + 26;

        int maxScroll = Math.max(0, this.contentHeight - (this.height - TOP_HEIGHT - MmmUi.pagePad(this.width)));
        this.scrollY = Math.max(0.0D, Math.min(maxScroll, this.scrollY));
    }

    private int sectionHeight(VisibleSection section)
    {
        return 58 + section.rows().size() * ROW_HEIGHT + CARD_PAD;
    }

    private void refreshVisibleSections()
    {
        this.visibleSections.clear();
        String query = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);
        for (SettingsSection section : this.sections)
        {
            if (query.isEmpty() || this.matchesSearch(section.title(), section.description(), section.sidebarItem().label(), query))
            {
                this.visibleSections.add(new VisibleSection(section, section.rows()));
                continue;
            }

            List<SettingRow> matches = new ArrayList<>();
            List<SettingRow> rows = section.rows();
            int index = 0;
            while (index < rows.size())
            {
                SettingRow row = rows.get(index);
                if (row.kind() != ControlKind.HEADING)
                {
                    if (this.matchesSearch(row, query))
                    {
                        matches.add(row);
                    }
                    index++;
                    continue;
                }

                SettingRow heading = row;
                int groupStart = ++index;
                while (index < rows.size() && rows.get(index).kind() != ControlKind.HEADING)
                {
                    index++;
                }

                boolean headingMatches = this.matchesSearch(heading, query);
                List<SettingRow> groupMatches = new ArrayList<>();
                for (int rowIndex = groupStart; rowIndex < index; rowIndex++)
                {
                    SettingRow groupRow = rows.get(rowIndex);
                    if (headingMatches || this.matchesSearch(groupRow, query))
                    {
                        groupMatches.add(groupRow);
                    }
                }
                if (headingMatches || groupMatches.isEmpty() == false)
                {
                    matches.add(heading);
                    matches.addAll(groupMatches);
                }
            }

            if (matches.isEmpty() == false)
            {
                this.visibleSections.add(new VisibleSection(section, List.copyOf(matches)));
            }
        }
    }

    private boolean matchesSearch(SettingRow row, String query)
    {
        String configName = row.config() == null ? "" : row.config().getName();
        return this.matchesSearch(row.label(), row.description(), configName, query);
    }

    private boolean matchesSearch(String first, String second, String third, String query)
    {
        return (first != null && first.toLowerCase(Locale.ROOT).contains(query))
                || (second != null && second.toLowerCase(Locale.ROOT).contains(query))
                || (third != null && third.toLowerCase(Locale.ROOT).contains(query));
    }

    private void commitTextValue(SettingRow row, String value)
    {
        try
        {
            if (row.kind() == ControlKind.COLOR && this.isCompleteHex(value) == false)
            {
                return;
            }

            if (row.kind() == ControlKind.NUMBER)
            {
                this.setNumberConfigValue(row.config(), value);
                return;
            }

            this.setConfigString(row.config(), value);
        }
        catch (RuntimeException ignored)
        {
            // Keep the previous valid config value while the user is typing an incomplete input.
        }
    }

    private void syncField(IConfigBase config)
    {
        TextFieldWidget field = this.textFields.get(config);
        if (field != null)
        {
            field.setText(this.getConfigString(config));
        }
    }

    private String getConfigString(IConfigBase config)
    {
        if (config instanceof IStringRepresentable representable)
        {
            return representable.getStringValue();
        }
        return "";
    }

    private void setConfigString(IConfigBase config, String value)
    {
        if (config instanceof IStringRepresentable representable)
        {
            representable.setValueFromString(value);
            Configs.saveToFile();
        }
    }

    private void setNumberConfigValue(IConfigBase config, String value)
    {
        if (config == null || value == null)
        {
            return;
        }

        String normalized = value.trim().replace(",", "").replace("_", "");
        if (normalized.isEmpty() || "-".equals(normalized) || ".".equals(normalized) || "-.".equals(normalized))
        {
            return;
        }

        if (config instanceof IConfigInteger integerConfig)
        {
            long parsed = Long.parseLong(normalized);
            int next = (int) Math.max(integerConfig.getMinIntegerValue(), Math.min(integerConfig.getMaxIntegerValue(), parsed));
            integerConfig.setIntegerValue(next);
            Configs.saveToFile();
            return;
        }

        if (config instanceof IConfigDouble doubleConfig)
        {
            double parsed = Double.parseDouble(normalized);
            double next = Math.max(doubleConfig.getMinDoubleValue(), Math.min(doubleConfig.getMaxDoubleValue(), parsed));
            doubleConfig.setDoubleValue(next);
            Configs.saveToFile();
            return;
        }

        this.setConfigString(config, value);
    }

    private void adjustNumberByScroll(IConfigBase config, double verticalAmount)
    {
        int direction = verticalAmount > 0 ? 1 : -1;
        if (config instanceof IConfigInteger integerConfig)
        {
            int step = this.integerScrollStep(integerConfig);
            int next = Math.max(integerConfig.getMinIntegerValue(), Math.min(integerConfig.getMaxIntegerValue(), integerConfig.getIntegerValue() + direction * step));
            integerConfig.setIntegerValue(next);
            this.syncField(config);
            Configs.saveToFile();
            return;
        }

        if (config instanceof IConfigDouble doubleConfig)
        {
            double step = this.doubleScrollStep(doubleConfig);
            double raw = doubleConfig.getDoubleValue() + direction * step;
            double next = Math.max(doubleConfig.getMinDoubleValue(), Math.min(doubleConfig.getMaxDoubleValue(), Math.round(raw * 1000.0D) / 1000.0D));
            doubleConfig.setDoubleValue(next);
            this.syncField(config);
            Configs.saveToFile();
        }
    }

    private int integerScrollStep(IConfigInteger config)
    {
        int range = Math.max(1, config.getMaxIntegerValue() - config.getMinIntegerValue());
        if (range > 100_000)
        {
            return 1_000;
        }
        if (range > 1_000)
        {
            return 10;
        }
        return 1;
    }

    private double doubleScrollStep(IConfigDouble config)
    {
        double range = Math.max(0.01D, config.getMaxDoubleValue() - config.getMinDoubleValue());
        if (range <= 2.0D)
        {
            return 0.05D;
        }
        if (range <= 10.0D)
        {
            return 0.1D;
        }
        return 1.0D;
    }

    private boolean isCompleteHex(String value)
    {
        if (value == null)
        {
            return false;
        }
        String hex = value.trim();
        if (hex.startsWith("#"))
        {
            hex = hex.substring(1);
        }
        if (hex.startsWith("0x") || hex.startsWith("0X"))
        {
            hex = hex.substring(2);
        }
        return hex.matches("(?i)[0-9a-f]{6}") || hex.matches("(?i)[0-9a-f]{8}");
    }

    private String syncStatusText()
    {
        return CloudSyncManager.getStatusSummary().toUpperCase(Locale.ROOT);
    }

    private void drawSyncStatusRow(DrawContext context, int x, int y, int width)
    {
        String label = CloudSyncManager.getStatusLabel();
        int statusColor = switch (label)
        {
            case "Synced", "Ready", "Cooldown", "Connected" -> GREEN;
            case "Queued", "Retrying", "Link queued", "Link retrying" -> WARNING;
            case "Error", "Unavailable", "Wrong account" -> ERROR;
            default -> MUTED;
        };
        int labelWidth = Math.min(width / 2, this.textRenderer.getWidth(label));
        MmmUi.drawTextWithin(context, this.textRenderer, "Current Status", x, y + 7, Math.max(0, width - labelWidth - 12), TEXT, false);
        MmmUi.drawTextRightWithin(context, this.textRenderer, label, x + width, y + 7, Math.max(0, width / 2), statusColor, false);
        MmmUi.drawTextWithin(context, this.textRenderer, CloudSyncManager.getStatusDetail(), x, y + 18, width, MUTED, false);
    }

    private int parseHexColor(String value, int fallback)
    {
        if (value == null)
        {
            return fallback;
        }
        String hex = value.trim();
        if (hex.startsWith("#"))
        {
            hex = hex.substring(1);
        }
        if (hex.startsWith("0x") || hex.startsWith("0X"))
        {
            hex = hex.substring(2);
        }
        if (hex.length() != 6 && hex.length() != 8)
        {
            try
            {
                return this.ensureOpaqueColor((int) Long.parseLong(hex));
            }
            catch (NumberFormatException ignored)
            {
                return fallback;
            }
        }
        try
        {
            long parsed = Long.parseLong(hex, 16);
            return hex.length() == 8 ? this.ensureOpaqueColor((int) parsed) : 0xFF000000 | (int) parsed;
        }
        catch (NumberFormatException exception)
        {
            return fallback;
        }
    }

    private int getColorPreview(IConfigBase config, int fallback)
    {
        if (config instanceof ConfigColor color)
        {
            return this.parseHexColor(color.getStringValue(), fallback);
        }
        if (config instanceof IConfigInteger integer)
        {
            return this.ensureOpaqueColor(integer.getIntegerValue());
        }
        return this.parseHexColor(this.getConfigString(config), fallback);
    }

    private int ensureOpaqueColor(int color)
    {
        return (color & 0xFF000000) == 0 ? 0xFF000000 | color : color;
    }

    private boolean contains(double mouseX, double mouseY, int x, int y, int width, int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void createSections()
    {
        this.sections.clear();
        this.sections.add(SettingsSection.helpers(
                new SettingRow("Flat Digger", "Stop digging below your feet.", FeatureToggle.MMM_FLAT_DIGGER, ControlKind.BOOLEAN),
                new SettingRow("Perimeter Wall Helper", "Protect the configured perimeter floor.", FeatureToggle.MMM_PERIMETER_WALL_DIG_HELPER, ControlKind.BOOLEAN),
                new SettingRow("Perimeter Block List", "Choose which floor blocks are protected.", null, ControlKind.ACTION),
                new SettingRow("Toggle Tab", "Keep the player list open.", FeatureToggle.MMM_TOGGLE_TAB, ControlKind.BOOLEAN)
        ));
        this.sections.add(SettingsSection.sync(
                new SettingRow("Sync Status", "", null, ControlKind.STATUS),
                new SettingRow("Website Sync", "Send mining updates.", Configs.Generic.WEBSITE_SYNC_ENABLED, ControlKind.BOOLEAN),
                new SettingRow("Sync Scoreboard", "Choose the total-mined scoreboard for this source.", null, ControlKind.ACTION),
                new SettingRow("Sync Debug", "Save detailed sync logs.", Configs.Generic.WEBSITE_SYNC_DEBUG, ControlKind.BOOLEAN),
                new SettingRow("Abbreviated Numbers", "Show 12k instead of 12,000.", Configs.Generic.ABBREVIATED_NUMBERS, ControlKind.BOOLEAN)
        ));
        this.sections.add(SettingsSection.notifications(
                new SettingRow("Daily Goal", "Blocks to mine today.", Configs.Generic.DAILY_GOAL, ControlKind.NUMBER),
                new SettingRow("Decimal Percent", "Show decimals in goal progress.", Configs.Generic.GOAL_PERCENT_DECIMALS, ControlKind.BOOLEAN),
                new SettingRow("Decimal Places", "Choose 1, 2, or 3 decimal places.", Configs.Generic.GOAL_PERCENT_DECIMAL_PLACES, ControlKind.SLIDER),
                new SettingRow("Goal Tracking", "Track today's goal.", FeatureToggle.MMM_DAILY_GOAL, ControlKind.BOOLEAN),
                new SettingRow("Milestone Messages", "Post progress alerts in chat.", FeatureToggle.MMM_NOTIFICATIONS, ControlKind.BOOLEAN),
                new SettingRow("Share Milestones", "Share progress with linked MMM players.", Configs.Generic.SHARE_GOAL_MILESTONES, ControlKind.BOOLEAN),
                new SettingRow("Receive Milestones", "Show global milestones from linked players.", Configs.Generic.RECEIVE_GOAL_MILESTONES, ControlKind.BOOLEAN),
                new SettingRow("MMM Chat Messages", "Show global MMM messages in Minecraft chat.", Configs.Generic.SHOW_MMM_CHAT_MESSAGES, ControlKind.BOOLEAN),
                new SettingRow("Censor MMM Chat", "Hide slurs in MMM chat messages.", Configs.Generic.CENSOR_MMM_CHAT, ControlKind.BOOLEAN),
                new SettingRow("Ignored Players", "Hide chat and milestones from selected players.", null, ControlKind.ACTION),
                new SettingRow("Sound Alerts", "Play milestone sounds.", FeatureToggle.MMM_SOUND_ALERTS, ControlKind.BOOLEAN),
                new SettingRow("Custom Sounds", "Choose sounds for 25%, 50%, 75%, and 100%.", null, ControlKind.ACTION),
                new SettingRow("Pickaxe Animation", "Show a pickaxe at each milestone.", Configs.Generic.GOAL_PICKAXE_ANIMATION, ControlKind.BOOLEAN)
        ));
        this.sections.add(SettingsSection.hud(
                new SettingRow("Move HUD", "Move and resize sections.", null, ControlKind.ACTION),
                SettingRow.heading("MAIN HUD POSITION"),
                new SettingRow("HUD X", "Move left or right.", Configs.Generic.HUD_X, ControlKind.NUMBER),
                new SettingRow("HUD Y", "Move up or down.", Configs.Generic.HUD_Y, ControlKind.NUMBER),
                new SettingRow("HUD Alignment", "Choose its screen corner.", Configs.Generic.HUD_ALIGNMENT, ControlKind.OPTION),
                new SettingRow("HUD Scale", "Change the HUD size.", Configs.Generic.HUD_SCALE, ControlKind.NUMBER),
                SettingRow.heading("BLOCK TIMER POSITION"),
                new SettingRow("Timer X", "Move left or right.", Configs.Generic.TIMER_HUD_X, ControlKind.NUMBER),
                new SettingRow("Timer Y", "Move up or down.", Configs.Generic.TIMER_HUD_Y, ControlKind.NUMBER),
                new SettingRow("Timer Scale", "Change the timer size.", Configs.Generic.TIMER_HUD_SCALE, ControlKind.NUMBER),
                SettingRow.heading("BLOCK STATS POSITION"),
                new SettingRow("Block Stats X", "Move left or right.", Configs.Generic.BLOCK_STATS_X, ControlKind.NUMBER),
                new SettingRow("Block Stats Y", "Move up or down.", Configs.Generic.BLOCK_STATS_Y, ControlKind.NUMBER),
                new SettingRow("Block Stats Scale", "Change block-stats size.", Configs.Generic.BLOCK_STATS_SCALE, ControlKind.NUMBER)
        ));
        this.sections.add(SettingsSection.hudContent(
                SettingRow.heading("TRACKING & SESSION"),
                new SettingRow("Mining Tracker", "Track mined blocks and sessions.", FeatureToggle.MMM_MINING_TRACKER, ControlKind.BOOLEAN),
                new SettingRow("Summary on Exit", "Open the summary when leaving.", FeatureToggle.MMM_SUMMARY_ON_EXIT, ControlKind.BOOLEAN),
                new SettingRow("Carry Goal Progress", "Keep today's progress between sessions.", FeatureToggle.MMM_CARRY_GOAL_PROGRESS, ControlKind.BOOLEAN),
                SettingRow.heading("MAIN HUD"),
                new SettingRow("Main HUD", "Show the stats HUD.", FeatureToggle.MMM_HUD, ControlKind.BOOLEAN),
                new SettingRow("MMM Header", "Show MMM and sync status.", Configs.Generic.HUD_TITLE_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Project", "Show your active project.", FeatureToggle.MMM_HUD_PROJECT, ControlKind.BOOLEAN),
                new SettingRow("Mining Totals", "Enable mining total rows.", FeatureToggle.MMM_HUD_TOTAL_MINED, ControlKind.BOOLEAN),
                new SettingRow("Global Total", "Show your website total.", Configs.Generic.HUD_GLOBAL_TOTAL_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("World Total", "Show this world's total.", Configs.Generic.HUD_WORLD_TOTAL_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Session Total", "Show this session's total.", Configs.Generic.HUD_SESSION_TOTAL_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Today / Week", "Show today and this week.", Configs.Generic.HUD_DAILY_WEEK_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Personal Records", "Show day and week bests.", Configs.Generic.HUD_RECORDS_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Fastest 100k", "Show your fastest 100k time.", Configs.Generic.HUD_FASTEST_100K_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Mining Speed", "Show Blocks/hr and Blocks/sec.", FeatureToggle.MMM_HUD_BLOCKS_PER_HOUR, ControlKind.BOOLEAN),
                new SettingRow("Blocks/min", "Also show Blocks/min.", Configs.Generic.BLOCKS_PER_MINUTE_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Hour / Best Hour", "Show current and best hour.", Configs.Generic.HOURLY_STATS_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Timer Status", "Show timer status.", Configs.Generic.HUD_TIMER_STATUS_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Session Times", "Show active time and time with pauses.", Configs.Generic.HUD_SESSION_TIME_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Daily Reset", "Show time until UTC reset.", Configs.Generic.HUD_DAILY_RESET_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Goal ETA", "Show time left to goal.", FeatureToggle.MMM_HUD_ETA, ControlKind.BOOLEAN),
                new SettingRow("Daily Goal Bar", "Show goal progress and % in the HUD.", Configs.Generic.HUD_DAILY_GOAL_BAR_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Override XP Bar", "Use goal progress in XP bar.", FeatureToggle.MMM_HUD_GOAL_PROGRESS, ControlKind.BOOLEAN),
                new SettingRow("Always Override XP Bar", "Always replace vanilla XP.", Configs.Generic.ALWAYS_OVERRIDE_XP_BAR, ControlKind.BOOLEAN),
                new SettingRow("Show Goal %", "Show goal % as XP level.", Configs.Generic.SHOW_GOAL_PERCENT, ControlKind.BOOLEAN),
                new SettingRow("Main Background", "Show one HUD background.", FeatureToggle.MMM_HUD_BOUNDING_BOX, ControlKind.BOOLEAN),
                new SettingRow("Text Background", "Show backgrounds per line.", Configs.Generic.HUD_TEXT_BACKGROUND, ControlKind.BOOLEAN),
                SettingRow.heading("BLOCK TIMER"),
                new SettingRow("Timer HUD", "Show the challenge timer.", Configs.Generic.TIMER_HUD_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Notifications", "Show timer milestones.", Configs.Generic.TIMER_NOTIFICATIONS, ControlKind.BOOLEAN),
                new SettingRow("Credits Screen", "Show results when finished.", Configs.Generic.TIMER_CREDITS, ControlKind.BOOLEAN),
                SettingRow.heading("BLOCK STATS"),
                new SettingRow("Block Stats", "Show blocks from this run.", Configs.Generic.BLOCK_STATS_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Background", "Show the stats panel.", Configs.Generic.BLOCK_STATS_BACKGROUND, ControlKind.BOOLEAN),
                new SettingRow("Block Icons", "Show each block icon.", Configs.Generic.BLOCK_STATS_ICONS, ControlKind.BOOLEAN)
        ));
        this.sections.add(SettingsSection.colors(
                new SettingRow("MMM Menu", "Set the menu accent.", Configs.Generic.MENU_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Menu Opacity", "Change menu background transparency.", Configs.Generic.MENU_OPACITY, ControlKind.SLIDER),
                new SettingRow("HUD Title", "Set the title color.", Configs.Generic.HUD_TITLE_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("HUD Text", "Set the label color.", Configs.Generic.HUD_TEXT_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("HUD Numbers", "Set the number color.", Configs.Generic.HUD_NUMBER_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Inactive Text", "Set the paused color.", Configs.Generic.HUD_INACTIVE_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("HUD Background", "Set the panel color.", Configs.Generic.HUD_BACKGROUND_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Text Shadow", "Add a dark pixel shadow.", Configs.Generic.HUD_TEXT_SHADOW, ControlKind.BOOLEAN)
        ));
        this.sections.add(SettingsSection.blockEsp(
                new SettingRow("Block ESP", "Highlight the block you look at.", FeatureToggle.MMM_BLOCK_ESP, ControlKind.BOOLEAN),
                new SettingRow("Color Mode", "Use one color or rainbow.", Configs.Generic.BLOCK_ESP_COLOR_MODE, ControlKind.OPTION),
                new SettingRow("Custom Color", "Set the highlight color.", Configs.Generic.BLOCK_ESP_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Render Mode", "Choose the highlight shape.", Configs.Generic.BLOCK_ESP_RENDER_MODE, ControlKind.OPTION),
                new SettingRow("Opacity", "Change highlight strength.", Configs.Generic.BLOCK_ESP_OPACITY, ControlKind.NUMBER),
                new SettingRow("Rainbow Speed", "Change rainbow speed.", Configs.Generic.BLOCK_ESP_RAINBOW_SPEED, ControlKind.NUMBER)
        ));
        if (SPEED_GRAPH_AVAILABLE)
        {
            this.sections.add(SettingsSection.speedGraph(
                    new SettingRow("Speed Graph", "Show the live speed graph.", FeatureToggle.MMM_HUD_SPEED_GRAPH, ControlKind.BOOLEAN),
                    new SettingRow("Background Opacity", "Change background strength.", Configs.Generic.GRAPH_BG_OPACITY, ControlKind.NUMBER),
                    new SettingRow("Line Color", "Set the graph line color.", Configs.Generic.GRAPH_LINE_HEX_COLOR, ControlKind.COLOR),
                    new SettingRow("Fill Color", "Set the graph fill color.", Configs.Generic.GRAPH_FILL_HEX_COLOR, ControlKind.COLOR),
                    new SettingRow("Fill Opacity", "Change fill strength.", Configs.Generic.GRAPH_FILL_OPACITY, ControlKind.NUMBER),
                    new SettingRow("Grid Color", "Set the grid color.", Configs.Generic.GRAPH_GRID_HEX_COLOR, ControlKind.COLOR),
                    new SettingRow("Grid Opacity", "Change grid strength.", Configs.Generic.GRAPH_GRID_OPACITY, ControlKind.NUMBER),
                    new SettingRow("Scale Step", "Set Blocks/hr grid gaps.", Configs.Generic.GRAPH_SCALE_STEP, ControlKind.NUMBER)
            ));
        }
        this.sections.add(SettingsSection.visuals(
                new SettingRow("Blocks/sec Smoothing", "Change speed response.", Configs.Generic.BPS_SMOOTHING, ControlKind.OPTION),
                new SettingRow("Small Dig Items", "Shrink tracked block items.", Configs.Generic.SMALL_DIG_ITEMS, ControlKind.BOOLEAN),
                new SettingRow("Item Size", "Choose the smaller size.", Configs.Generic.SMALL_DIG_ITEM_SCALE, ControlKind.SLIDER),
                new SettingRow("No Swinging Animation", "Keep your tool still.", Configs.Generic.NO_SWINGING_ANIMATION, ControlKind.BOOLEAN),
                SettingRow.heading("BREAKING INDICATORS"),
                new SettingRow("Breaking Indicators", "Show live block-breaking progress.", Configs.Generic.BREAKING_INDICATORS, ControlKind.BOOLEAN),
                new SettingRow("Start Color", "Color when breaking begins.", Configs.Generic.BREAKING_INDICATOR_START_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("End Color", "Color when the block is nearly broken.", Configs.Generic.BREAKING_INDICATOR_END_HEX_COLOR, ControlKind.COLOR),
                SettingRow.heading("TRANSLUCENT LAVA"),
                new SettingRow("Translucent Lava", "See through lava.", Configs.Generic.TRANSLUCENT_LAVA, ControlKind.BOOLEAN),
                new SettingRow("Lava Opacity", "Choose how visible lava looks.", Configs.Generic.LAVA_OPACITY, ControlKind.SLIDER)
        ));
        if (Configs.isDevToolsEnabled())
        {
            this.sections.add(SettingsSection.developer(
                    new SettingRow("Generate Dev Run", "Create a test run.", null, ControlKind.ACTION)
            ));
        }
    }

    private enum SidebarItem
    {
        GENERAL("General"),
        SYNC("Sync"),
        NOTIFICATIONS("Notifications"),
        HUD("HUD"),
        COLORS("Colors"),
        VISUALS("Visuals"),
        SPEED_GRAPH("Speed Graph"),
        BLOCK_ESP("Block ESP"),
        ABOUT("About");

        private final String label;

        SidebarItem(String label)
        {
            this.label = label;
        }

        private String label()
        {
            return this.label;
        }
    }

    private enum ControlKind
    {
        BOOLEAN,
        NUMBER,
        TEXT,
        OPTION,
        COLOR,
        SLIDER,
        HEADING,
        STATUS,
        ACTION;

        private boolean usesTextField()
        {
            return this == NUMBER || this == TEXT || this == COLOR;
        }
    }

    private record SettingRow(String label, String description, IConfigBase config, ControlKind kind)
    {
        private static SettingRow heading(String label)
        {
            return new SettingRow(label, "", null, ControlKind.HEADING);
        }
    }

    private record VisibleSection(SettingsSection section, List<SettingRow> rows)
    {
    }

    private interface ClickAction
    {
        void run(double mouseX, double mouseY);
    }

    private record ClickTarget(int x, int y, int width, int height, ClickAction action)
    {
        private ClickTarget(int x, int y, int width, int height, Runnable action)
        {
            this(x, y, width, height, (mouseX, mouseY) -> action.run());
        }

        private boolean contains(double mouseX, double mouseY)
        {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private record ScrollTarget(int x, int y, int width, int height, IConfigBase config)
    {
        private boolean contains(double mouseX, double mouseY)
        {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private record SliderTarget(int x, int y, int width, int height, IConfigBase config)
    {
        private boolean contains(double mouseX, double mouseY)
        {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private static final class SettingsSection
    {
        private final SidebarItem sidebarItem;
        private final String title;
        private final String description;
        private final List<SettingRow> rows;

        private SettingsSection(SidebarItem sidebarItem, String title, String description, List<SettingRow> rows)
        {
            this.sidebarItem = sidebarItem;
            this.title = title;
            this.description = description;
            this.rows = rows;
        }

        private static SettingsSection helpers(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.GENERAL, "MINING HELPERS", "Configure optional mining helpers.", List.of(rows));
        }

        private static SettingsSection sync(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.SYNC, "SYNC", "Choose what MMM sends to the website.", List.of(rows));
        }

        private static SettingsSection notifications(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.NOTIFICATIONS, "GOALS & NOTIFICATIONS", "Set your goal and milestone alerts.", List.of(rows));
        }

        private static SettingsSection hud(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.HUD, "HUD LAYOUT", "Move and resize the main HUD.", List.of(rows));
        }

        private static SettingsSection hudContent(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.HUD, "HUD CONTENT", "Choose exactly what appears on screen.", List.of(rows));
        }

        private static SettingsSection colors(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.COLORS, "HUD COLORS", "Choose colors for menus and HUD text.", List.of(rows));
        }

        private static SettingsSection visuals(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.VISUALS, "VISUALS", "Tune mining visuals and speed readings.", List.of(rows));
        }


        private static SettingsSection speedGraph(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.SPEED_GRAPH, "SPEED GRAPH", "Change how the live graph looks.", List.of(rows));
        }

        private static SettingsSection blockEsp(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.BLOCK_ESP, "BLOCK ESP", "Change how block highlights look.", List.of(rows));
        }

        private static SettingsSection developer(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.ABOUT, "DEV TOOLS", "Generate local test data for MMM screens.", List.of(rows));
        }

        private SidebarItem sidebarItem()
        {
            return this.sidebarItem;
        }

        private String title()
        {
            return this.title;
        }

        private String description()
        {
            return this.description;
        }

        private List<SettingRow> rows()
        {
            return this.rows;
        }
    }
}
