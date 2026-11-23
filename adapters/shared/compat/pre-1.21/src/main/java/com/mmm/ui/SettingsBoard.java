package com.mmm.ui;

import java.util.ArrayList;
import java.util.EnumMap;
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
import com.mmm.ui.BoardPlatform.Canvas;
import net.minecraft.client.gui.screen.Screen;
import com.mmm.ui.BoardPlatform.BoardField;
import com.mmm.ui.BoardPlatform.BoardButton;
import net.minecraft.text.Text;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

abstract class SettingsBoard extends BoardPlatform.BoardScreen
{
    private static final int BG = CollectionBoardArt.BASE;
    private static final int TOP_BAR = 0xF0060606;
    private static final int CARD = 0xF20D0D0D;
    private static final int INSET = 0xFF121212;
    private static final int BORDER = 0xFF1F1F1F;
    private static final int BORDER_SOFT = 0xFF272727;
    private static final int TEXT = 0xFFF5F5F5;
    private static final int MUTED = CollectionBoardArt.MUTED;
    private static final int GREEN = 0xFF43D483;
    private static final int ERROR = 0xFFFF5965;
    private static final int WARNING = 0xFFFFB84D;

    private static final int TOP_HEIGHT = 34;
    private static final int GAP = 12;
    private static final int CARD_PAD = 12;
    private static final int ROW_HEIGHT = 32;
    private static final int FIELD_HEIGHT = 18;
    private static final int CONTROL_WIDTH = 112;
    private static final int RESET_WIDTH = 18;
    private static final int CONTENT_HEADER_HEIGHT = 44;
    
    private static final long OPENING_HOTKEY_SUPPRESSION_TIMEOUT_MS = 750L;

    private final Screen parent;
    private final List<SettingsSection> sections = new ArrayList<>();
    private final List<VisibleSection> visibleSections = new ArrayList<>();
    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private final List<ScrollTarget> scrollTargets = new ArrayList<>();
    private final List<SliderTarget> sliderTargets = new ArrayList<>();
    private final Map<IConfigBase, BoardField> textFields = new IdentityHashMap<>();
    private IConfigBase movingToggle;
    private long toggleAt;
    private final SettingsPage home;
    private final List<NavigationButton> categoryRail = new ArrayList<>();
    private long transitionAt;
    private SettingsPage currentPage;
    private final List<PageLocation> pageHistory = new ArrayList<>();
    private final List<NavigationButton> navigationButtons = new ArrayList<>();
    private final Map<ToolDirectory, NavigationButton> searchTools = new EnumMap<>(ToolDirectory.class);
    private NavigationButton hotkeysButton;
    private NavigationButton previewTopic;
    private long previewSince;
    private BoardButton backButton;
    private double browseScrollY;
    private String hoveredDescription;
    private double scrollY = 0.0D;
    private int contentHeight = 0;
    private SliderTarget draggingSlider;
    private BoardField searchField;
    private String searchQuery = "";
    private boolean suppressAutomaticSearchInput;
    private final long openedAtMs;

    public SettingsBoard(Screen parent)
    {
        this(parent, false);
    }

    public SettingsBoard(Screen parent, boolean openedFromHotkey)
    {
        super(BoardPlatform.literal("MMM Mod Settings"));
        this.parent = parent;
        this.suppressAutomaticSearchInput = openedFromHotkey;
        this.openedAtMs = System.currentTimeMillis();
        this.sections.addAll(settingsInventory());
        this.home = buildSettingsPages(this.sections);
        this.currentPage = this.home;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.clearChildren();
        this.textFields.clear();
        this.navigationButtons.clear();
        this.searchTools.clear();
        this.categoryRail.clear();
        this.transitionAt = CollectionBoardArt.now();

        this.searchField = new BoardField(this.textRenderer, 0, 0, 220, FIELD_HEIGHT, BoardPlatform.literal("Search settings"));
        this.searchField.setDrawsBackground(false);
        this.searchField.setEditableColor(TEXT);
        this.searchField.setMaxLength(64);
        this.searchField.setPlaceholder(BoardPlatform.literal("Search settings..."));
        this.searchField.setText(this.searchQuery);
        this.searchField.setChangedListener(value -> {
            boolean wasSearching = !this.searchQuery.isBlank();
            if (!wasSearching && !value.isBlank()) this.browseScrollY = this.scrollY;
            this.searchQuery = value;
            this.scrollY = value.isBlank() ? this.browseScrollY : 0.0D;
            this.draggingSlider = null;
            this.refreshVisibleSections();
        });
        this.addDrawableChild(this.searchField);
        this.hotkeysButton = this.addDrawableChild(new NavigationButton(ToolDirectory.HOTKEYS.label, 56, FIELD_HEIGHT,
                button -> openTool(ToolDirectory.HOTKEYS)));
        var website = this.addDrawableChild(new NavigationButton("Website Link", 82, 19, button -> openWebsiteLink()));
        website.setPosition(workspaceX() + workspaceWidth() - 107, 7);
        this.backButton = this.addDrawableChild(new NavigationButton("< Back", 48, 18, button -> this.goBack()));
        for (ToolDirectory tool : ToolDirectory.values()) {
            if (!MmmUi.TOOL_SCREENS.containsKey(tool)) continue;
            NavigationButton result = new NavigationButton(tool.label, 120, ROW_HEIGHT - 2, button -> openTool(tool));
            result.hint = tool.description;
            result.icon = new ItemStack(toolIcon(tool));
            result.visible = false;
            searchTools.put(tool, this.addDrawableChild(result));
        }
        for (SettingsPage page : this.home.children()) {
            this.categoryRail.add(this.addDrawableChild(topicButton(page, true, () -> {
                this.pageHistory.clear();
                this.pageHistory.add(new PageLocation(this.home, 0));
                this.searchField.setText("");
                this.openPage(page);
            })));
        }
        this.rebuildNavigationButtons();
        this.refreshVisibleSections();

        for (SettingsSection section : this.sections)
        {
            for (SettingRow row : section.rows())
            {
                if (row.kind().usesTextField())
                {
                    BoardField field = new BoardField(this.textRenderer, 0, 0, CONTROL_WIDTH, FIELD_HEIGHT, BoardPlatform.literal(""));
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
    public void render(Canvas context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        this.clickTargets.clear();
        this.scrollTargets.clear();
        this.sliderTargets.clear();
        this.hoveredDescription = null;
        for (NavigationButton result : this.searchTools.values()) result.visible = false;
        for (BoardField field : this.textFields.values())
        {
            field.setVisible(false);
        }
        this.searchField.setVisible(true);
        this.updateLayout();

        context.raw().fill(0, 0, this.width, this.height, MmmUi.menuSurface(BG));
        this.drawTopBar(context, mouseX, mouseY);

        int pagePad = MmmUi.pagePad(this.width);
        int viewportX = contentX();
        int viewportY = TOP_HEIGHT;
        int viewportW = contentWidth();
        int viewportH = Math.max(1, this.height - TOP_HEIGHT - pagePad);

        layoutRail(context);
        this.drawMainContent(context, viewportX, viewportY, viewportW, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);

        this.drawScrollbar(context, viewportX + viewportW - 4, viewportY, viewportH);
        if (this.hoveredDescription != null)
        {
            context.raw().drawOrderedTooltip(this.textRenderer, this.textRenderer.wrapLines(BoardPlatform.literal(this.hoveredDescription),
                    Math.min(280, Math.max(80, this.width - 32))), mouseX, mouseY);
        }
        for (BoardField field : this.textFields.values())
        {
            if (!field.isVisible() && field.isFocused())
            {
                field.focus(false);
                if (this.getFocused() == field) this.setFocused(null);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && this.searchField != null && this.searchField.isMouseOver(mouseX, mouseY))
        {
            this.suppressAutomaticSearchInput = false;
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
            if (button == 0 && target.contains(mouseX, mouseY))
            {
                target.action().run(mouseX, mouseY);
                return true;
            }
        }

        SettingsPage previousPage = this.currentPage;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // ParentElement focuses the clicked child after its callback, which may have removed it.
        if (previousPage != this.currentPage) this.resetPageFocus();
        return handled;
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
            if (this.searchField != null && !this.searchQuery.isBlank())
            {
                this.searchField.setText("");
                return true;
            }
            if (!this.pageHistory.isEmpty()) this.goBack();
            else this.close();
            return true;
        }

        boolean pageKey = keyCode == 266 || keyCode == 267;
        if ((!(this.getFocused() instanceof BoardField) || (pageKey && this.getFocused() == this.searchField))
                && (pageKey || keyCode == 268 || keyCode == 269))
        {
            int maxScroll = Math.max(0, this.contentHeight - (this.height - TOP_HEIGHT - MmmUi.pagePad(this.width)));
            int step = Math.max(ROW_HEIGHT, this.height - TOP_HEIGHT - CONTENT_HEADER_HEIGHT - 32);
            this.scrollY = switch (keyCode) {
                case 268 -> 0;
                case 269 -> maxScroll;
                case 266 -> Math.max(0, this.scrollY - step);
                default -> Math.min(maxScroll, this.scrollY + step);
            };
            return true;
        }

        if (this.searchField != null
                && this.searchField.isVisible()
                && this.searchField.isFocused()
                && this.searchField.keyPressed(keyCode, scanCode, modifiers))
        {
            return true;
        }

        SettingsPage previousPage = this.currentPage;
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (previousPage != this.currentPage) this.resetPageFocus();
        return handled;
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
        for (BoardField field : this.textFields.values())
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
            this.setFocused(this.searchField);
            this.searchField.focus(true);
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
    public void renderBackground(Canvas context)
    {
    }

    private int workspaceWidth() { return Math.min(650, width - 16); }
    private int workspaceX() { return (width - workspaceWidth()) / 2; }
    private boolean railVisible() {
        return width >= 530 && height >= TOP_HEIGHT + 40 + home.children().size() * 23 && currentPage != home;
    }
    private int contentX() { return workspaceX() + (railVisible() ? 134 : 0); }
    private int contentWidth() { return workspaceWidth() - (railVisible() ? 134 : 0); }
    private int navColumns() { return contentWidth() >= 300 ? 2 : 1; }
    private boolean hasDossier() { return currentPage == home && contentWidth() >= 500 && height >= 276; }
    private int dossierWidth() { return Math.min(218, contentWidth() / 3); }
    private int topicStep() { return hasDossier() ? 54 : 41; }

    private void drawTopBar(Canvas c, int mx, int my) {
        int left = workspaceX(), right = left + workspaceWidth();
        c.raw().fill(0, 0, width, TOP_HEIGHT, MmmUi.menuSurface(TOP_BAR));
        c.raw().fill(0, TOP_HEIGHT - 1, width, TOP_HEIGHT, CollectionBoardArt.LINE);
        c.raw().drawText(textRenderer, "MMM", left + 2, 13, CollectionBoardArt.ROSE, true);
        c.raw().drawText(textRenderer, "/ Settings", left + 29, 13, TEXT, false);
        if (width >= 510) {
            String status = syncStatusText();
            MmmUi.drawTextRightWithin(c.raw(), textRenderer, status, right - 115, 13, workspaceWidth() / 3, MUTED, false);
        }
        drawButtonShell(c, right - 20, 7, 20, 19, "X", mx, my, false);
        clickTargets.add(new ClickTarget(right - 20, 7, 20, 19, this::close));
    }

    private void layoutRail(Canvas c) {
        boolean visible = railVisible();
        int left = workspaceX(), top = TOP_HEIGHT + 15;
        if (visible) {
            c.raw().drawText(textRenderer, "CATEGORIES", left + 6, top, MUTED, false);
            c.raw().fill(left + 125, top, left + 126, height - 10, CollectionBoardArt.LINE);
        }
        SettingsPage section = pageHistory.size() > 1 ? pageHistory.get(1).page() : currentPage;
        for (int i = 0; i < categoryRail.size(); i++) {
            NavigationButton button = categoryRail.get(i);
            button.visible = visible;
            button.chosen = section == home.children().get(i);
            button.setPosition(left, top + 17 + i * 23);
            button.setWidth(120);
        }
    }

    private void openWebsiteLink()
    {
        this.client.setScreen(new WebsiteLinkScreen(this));
    }

    private void openTool(ToolDirectory tool) {
        this.client.setScreen(MmmUi.TOOL_SCREENS.get(tool).apply(this));
    }

    private void drawMainContent(Canvas context, int x, int viewportY, int width, int mouseX, int mouseY)
    {
        int y = viewportY + 10;
        boolean searching = !this.searchQuery.isBlank();
        this.backButton.visible = searching || !this.pageHistory.isEmpty();
        this.backButton.setPosition(x, y - 5);
        int titleX = x + (this.backButton.visible ? 53 : 0);
        String title = searching ? "Search results" : this.currentPage == home ? "Controls & preferences" : this.currentPage.title();
        if (!searching && pageHistory.size() > 1 && contentWidth() > 370)
            title = pageHistory.get(pageHistory.size() - 1).page().title() + " / " + title;
        MmmUi.drawTextWithin(context.raw(), this.textRenderer, title, titleX, y, width - (titleX - x) - 8, TEXT, false);
        if (this.textRenderer.getWidth(title) > width - (titleX - x) - 8
                && this.contains(mouseX, mouseY, titleX, y, width - (titleX - x) - 8, 12)) this.hoveredDescription = title;

        int searchY = y + 18;
        this.hotkeysButton.visible = this.currentPage == home && !searching;
        this.hotkeysButton.setPosition(x + width - 10 - this.hotkeysButton.getWidth(), searchY);
        int searchWidth = width - 10 - (this.hotkeysButton.visible ? this.hotkeysButton.getWidth() + 4 : 0);
        boolean canClearSearch = this.searchQuery.isBlank() == false;
        int clearWidth = canClearSearch ? 20 : 0;
        this.searchField.setX(x + 5);
        this.searchField.setY(searchY + 5);
        this.searchField.setWidth(Math.max(40, searchWidth - 10 - clearWidth));
        MmmUi.fieldShell(context.raw(), x, searchY, searchWidth, FIELD_HEIGHT, this.searchField.isFocused());
        if (canClearSearch)
        {
            int clearX = x + searchWidth - 19;
            this.drawButtonShell(context, clearX, searchY, 18, FIELD_HEIGHT, "X", mouseX, mouseY, true);
            this.clickTargets.add(new ClickTarget(clearX, searchY, 18, FIELD_HEIGHT, () -> {
                this.searchField.setText("");
                this.setFocused(this.searchField);
                this.searchField.focus(true);
            }));
        }

        int gridY = y + CONTENT_HEADER_HEIGHT - (int) Math.round(this.scrollY);
        int cols = navColumns();
        boolean showDossier = !searching && hasDossier();
        int span = (width - 6 - (showDossier ? dossierWidth() + 6 : 0)) / cols;
        for (int i = 0; i < this.navigationButtons.size(); i++)
        {
            NavigationButton button = this.navigationButtons.get(i);
            int buttonY = gridY + i / cols * topicStep();
            button.setPosition(x + i % cols * span, buttonY);
            button.setWidth(span - 3);
            button.visible = !searching && buttonY >= y + CONTENT_HEADER_HEIGHT
                    && buttonY + button.getHeight() <= this.height - MmmUi.pagePad(this.width);
            if (showDossier && button.visible && (BoardPlatform.keyboard()
                    ? button.isFocused() : button.isMouseOver(mouseX, mouseY))) {
                if (previewTopic != button) { previewTopic = button; previewSince = CollectionBoardArt.now(); }
            }
            if (!button.visible && button.isFocused())
            {
                BoardPlatform.unfocus(button);
                if (this.getFocused() == button) this.setFocused(null);
            }
        }
        if (showDossier && !navigationButtons.isEmpty()) {
            if (previewTopic == null) { previewTopic = navigationButtons.get(0); previewSince = transitionAt; }
            drawDossier(context,x + width - dossierWidth() - 6,y + CONTENT_HEADER_HEIGHT,dossierWidth(),previewTopic);
        }
        if (!searching && !this.currentPage.children().isEmpty()) return;
        if (this.visibleSections.isEmpty())
        {
            MmmUi.drawTextWithin(context.raw(), this.textRenderer, "No settings match your search.", x, gridY + 8, width, MUTED, false);
            return;
        }

        int rowY = gridY;

        context.raw().enableScissor(x, y + CONTENT_HEADER_HEIGHT, x + width, this.height - MmmUi.pagePad(this.width));
        for (VisibleSection section : this.visibleSections)
        {
            int rowHeight = this.sectionHeight(section);
            this.drawSection(context, section, x, rowY, width, rowHeight, mouseX, mouseY);
            rowY += rowHeight + GAP;
        }
        context.raw().disableScissor();
    }

    private void drawSection(Canvas context, VisibleSection visibleSection, int x, int y, int width, int height, int mouseX, int mouseY)
    {
        SettingsSection section = visibleSection.section();
        if (!this.searchQuery.isBlank())
        {
            boolean hovered = this.contains(mouseX, mouseY, x, y, width - 8, 44);
            context.raw().fill(x, y, x + width - 8, y + 44, MmmUi.menuSurface(hovered ? INSET : CARD));
            context.raw().fill(x, y + 43, x + width - 8, y + 44, MmmUi.accent());
            MmmUi.drawTextWithin(context.raw(), this.textRenderer, section.title(), x + CARD_PAD, y + 9, width - 32, TEXT, false);
            MmmUi.drawTextWithin(context.raw(), this.textRenderer, section.description(), x + CARD_PAD, y + 25, width - 32, MUTED, false);
        }

        int rowY = y + (this.searchQuery.isBlank() ? 4 : 48);
        for (SettingRow row : visibleSection.rows())
        {
            if (section.title().equals("Tools")) {
                NavigationButton result = searchTools.get(ToolDirectory.find(row.label()));
                result.setPosition(x + CARD_PAD, rowY);
                result.setWidth(width - CARD_PAD * 2);
                result.visible = visibleHeight(rowY, result.getHeight(), TOP_HEIGHT + 10 + CONTENT_HEADER_HEIGHT,
                        this.height - MmmUi.pagePad(this.width)) == result.getHeight();
            } else {
                this.drawSettingRow(context, row, x + CARD_PAD, rowY, width - CARD_PAD * 2, mouseX, mouseY);
            }
            rowY += this.settingRowHeight();
        }
    }

    private void drawSettingRow(Canvas context, SettingRow row, int x, int y, int width, int mouseX, int mouseY)
    {
        int rowHeight = this.settingRowHeight();
        int clipTop = TOP_HEIGHT + 10 + CONTENT_HEADER_HEIGHT;
        int clipBottom = this.height - MmmUi.pagePad(this.width);
        if (visibleHeight(y, rowHeight, clipTop, clipBottom) == 0) return;
        if (contains(mouseX, mouseY, x, y, width, rowHeight) && mouseY >= clipTop && mouseY < clipBottom)
            context.raw().fill(x - 4, y, x + width + 3, y + rowHeight, MmmUi.menuSurface(CollectionBoardArt.TILE));
        context.raw().fill(x, y + rowHeight - 1, x + width, y + rowHeight, BORDER);
        if (row.kind() == ControlKind.HEADING)
        {
            context.raw().fill(x, y + 8, x + 3, y + 24, MmmUi.accent());
            MmmUi.drawTextWithin(context.raw(), this.textRenderer, row.label(), x + 10, y + 12, width - 10, TEXT, false);
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
        int controlY = y + (rowHeight > ROW_HEIGHT ? 29 : 7);
        int labelW = rowHeight > ROW_HEIGHT ? width : Math.max(0, controlX - x - 8);
        if (mouseY >= clipTop && mouseY < clipBottom
                && this.contains(mouseX, mouseY, x, y, labelW, rowHeight) && !row.description().isBlank()
                && (this.textRenderer.getWidth(row.description()) > labelW || this.textRenderer.getWidth(row.label()) > labelW))
        {
            this.hoveredDescription = row.label() + ": " + row.description();
        }
        MmmUi.drawTextWithin(context.raw(), this.textRenderer, row.label(), x, y + 7, labelW, TEXT, false);
        MmmUi.drawTextWithin(context.raw(), this.textRenderer, row.description(), x, y + 18, labelW, MUTED, false);

        int firstClick = this.clickTargets.size();
        int firstScroll = this.scrollTargets.size();
        int firstSlider = this.sliderTargets.size();
        switch (row.kind())
        {
            case BOOLEAN -> this.drawBooleanControl(context, row.config(), controlX, controlY, controlWidth, mouseX, mouseY);
            case TEXT, NUMBER, COLOR -> this.drawTextControl(context, row, controlX, controlY, controlWidth, mouseX, mouseY);
            case OPTION -> this.drawOptionControl(context, row.config(), controlX, controlY, controlWidth, mouseX, mouseY);
            case SLIDER -> this.drawSliderControl(context, row.config(), controlX, controlY, controlWidth, mouseX, mouseY);
            case HEADING, STATUS -> {}
            case ACTION -> this.drawActionButton(context, controlX, controlY, controlWidth, FIELD_HEIGHT, this.actionButtonLabel(row), mouseX, mouseY, () -> {
                if ("Website Link".equals(row.label())) {
                    openWebsiteLink();
                }
                else if ("Move HUD".equals(row.label()))
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
                    openIgnoreList();
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
                else if ("Collections".equals(row.label())) client.setScreen(new CollectionBookScreen(this));
            });
        }

        if (row.kind() != ControlKind.ACTION && row.config() != null && row.config().isModified())
        {
            this.drawButtonShell(context, resetX, controlY, RESET_WIDTH, FIELD_HEIGHT, "R", mouseX, mouseY, true);
            this.clickTargets.add(new ClickTarget(resetX, controlY, RESET_WIDTH, FIELD_HEIGHT, () -> {
                row.config().resetToDefault();
                this.syncField(row.config());
                Configs.saveToFile();
            }));
        }
        // Partially visible rows still draw under the scissor, but clipped controls cannot intercept input.
        if (visibleHeight(controlY, FIELD_HEIGHT, clipTop, clipBottom) != FIELD_HEIGHT)
        {
            this.clickTargets.subList(firstClick, this.clickTargets.size()).clear();
            this.scrollTargets.subList(firstScroll, this.scrollTargets.size()).clear();
            this.sliderTargets.subList(firstSlider, this.sliderTargets.size()).clear();
        }
        BoardField field = this.textFields.get(row.config());
        if (field != null && visibleHeight(field.getY(), field.getHeight(), clipTop, clipBottom) != field.getHeight())
            field.setVisible(false);
    }

    static int visibleHeight(int y, int height, int clipTop, int clipBottom)
    {
        return SettingsNavigation.visibleHeight(y, height, clipTop, clipBottom);
    }

    private void drawBooleanControl(Canvas context, IConfigBase config, int x, int y, int width, int mouseX, int mouseY)
    {
        boolean enabled = config instanceof IConfigBoolean booleanConfig && booleanConfig.getBooleanValue();
        boolean hovered = this.contains(mouseX, mouseY, x, y, width, FIELD_HEIGHT);
        int edge = x + width - 2;
        int rail = edge - 23;
        context.raw().fill(rail, y + 3, edge, y + 15, enabled ? MmmUi.accent() : CollectionBoardArt.LINE);
        float t = config == movingToggle ? CollectionBoardArt.reveal(CollectionBoardArt.now() - toggleAt, 0) : 1f;
        int knob = rail + 2 + Math.round(11 * (enabled ? t : 1f - t));
        context.raw().fill(knob, y + 5, knob + 8, y + 13, enabled || hovered ? TEXT : MUTED);
        MmmUi.drawTextRightWithin(context.raw(), textRenderer, enabled ? "On" : "Off", rail - 5, y + 5, width - 32, enabled ? TEXT : MUTED, false);
        this.clickTargets.add(new ClickTarget(x, y, width, FIELD_HEIGHT, () -> {
            if (config instanceof IConfigBoolean booleanConfig)
            {
                booleanConfig.setBooleanValue(!booleanConfig.getBooleanValue());
                movingToggle = config;
                toggleAt = CollectionBoardArt.now();
                Configs.saveToFile();
            }
        }));
    }

    private void drawOptionControl(Canvas context, IConfigBase config, int x, int y, int width, int mouseX, int mouseY)
    {
        String label = this.getConfigString(config);
        if (config instanceof ConfigOptionList optionList)
        {
            label = optionList.getOptionListValue().getDisplayName();
        }

        this.drawButtonShell(context, x, y, width, FIELD_HEIGHT, label, mouseX, mouseY, false);
        this.clickTargets.add(new ClickTarget(x, y, width, FIELD_HEIGHT, () -> {
            if (config instanceof ConfigOptionList optionList)
            {
                IConfigOptionListEntry next = optionList.getOptionListValue().cycle(true);
                if (config != null)
                {
                    config.setValueFromString(next.getStringValue());
                }
                Configs.saveToFile();
            }
        }));
    }

    private void drawTextControl(Canvas context, SettingRow row, int x, int y, int width, int mouseX, int mouseY)
    {
        BoardField field = this.textFields.get(row.config());
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
            context.raw().fill(x, y, x + FIELD_HEIGHT, y + FIELD_HEIGHT, preview);
            context.raw().drawBorder(x, y, FIELD_HEIGHT, FIELD_HEIGHT, BORDER_SOFT);
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
        MmmUi.fieldShell(context.raw(), fieldX, y, fieldW, FIELD_HEIGHT, field.isFocused());
    }

    private void drawSliderControl(Canvas context, IConfigBase config, int x, int y, int width, int mouseX, int mouseY)
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

        context.raw().fill(x, trackY, x + width, trackY + 3, MmmUi.menuSurface(INSET));
        context.raw().fill(x, trackY, x + fillWidth, trackY + 3, MmmUi.accent());
        context.raw().drawBorder(x, trackY, width, 3, hovered ? MmmUi.accent() : BORDER_SOFT);
        int thumbX = Math.max(x, Math.min(x + width - 4, x + fillWidth - 2));
        context.raw().fill(thumbX, y + 3, thumbX + 4, y + FIELD_HEIGHT - 3, MmmUi.accent());

        int valueWidth = this.textRenderer.getWidth(value);
        context.raw().drawText(this.textRenderer, BoardPlatform.literal(value), x + Math.max(0, (width - valueWidth) / 2), y + 5, TEXT, true);
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
    private void drawActionButton(Canvas context, int x, int y, int width, int height, String label, int mouseX, int mouseY, Runnable action)
    {
        this.drawButtonShell(context, x, y, width, height, label, mouseX, mouseY, false);
        this.clickTargets.add(new ClickTarget(x, y, width, height, action));
    }

    private String actionButtonLabel(SettingRow row)
    {
        return "Generate Dev Run".equals(row.label()) ? "RUN" : "OPEN";
    }

    private void drawButtonShell(Canvas context, int x, int y, int width, int height, String label, int mouseX, int mouseY, boolean subtle)
    {
        boolean hovered = this.contains(mouseX, mouseY, x, y, width, height);
        int fill = subtle ? INSET : hovered ? MmmUi.accentHover() : INSET;
        int border = hovered ? MmmUi.accent() : BORDER_SOFT;
        context.raw().fill(x, y, x + width, y + height, MmmUi.menuSurface(fill));
        if (hovered) context.raw().drawBorder(x, y, width, height, border);
        String clipped = MmmUi.truncate(this.textRenderer, label, width - 8);
        int textX = x + Math.max(4, (width - this.textRenderer.getWidth(clipped)) / 2);
        context.raw().drawText(this.textRenderer, BoardPlatform.literal(clipped), textX, y + 6, hovered ? TEXT : MUTED, false);
    }

    private void drawScrollbar(Canvas context, int x, int y, int height)
    {
        int maxScroll = Math.max(0, this.contentHeight - height);
        if (maxScroll <= 0)
        {
            return;
        }

        context.raw().fill(x, y, x + 3, y + height, 0x55090909);
        int thumbH = Math.max(28, (int) (height * (height / (double) this.contentHeight)));
        int thumbY = y + (int) ((height - thumbH) * (this.scrollY / maxScroll));
        context.raw().fill(x, thumbY, x + 3, thumbY + thumbH, MmmUi.accent());
    }

    private void updateLayout()
    {
        int rowY = CONTENT_HEADER_HEIGHT;
        if (this.searchQuery.isBlank()) rowY += (this.currentPage.children().size() + navColumns() - 1) / navColumns() * topicStep();
        for (VisibleSection section : this.visibleSections)
        {
            rowY += this.sectionHeight(section) + GAP;
        }
        this.contentHeight = rowY + 26;

        int maxScroll = Math.max(0, this.contentHeight - (this.height - TOP_HEIGHT - MmmUi.pagePad(this.width)));
        this.scrollY = Math.max(0.0D, Math.min(maxScroll, this.scrollY));
    }

    private int sectionHeight(VisibleSection section)
    {
        return (this.searchQuery.isBlank() ? 4 : 48) + section.rows().size() * this.settingRowHeight() + CARD_PAD;
    }

    private int settingRowHeight()
    {
        return contentWidth() - CARD_PAD * 2 < 280 ? 52 : ROW_HEIGHT;
    }

    private void rebuildNavigationButtons()
    {
        previewTopic = null;
        for (BoardButton button : this.navigationButtons) this.remove(button);
        this.navigationButtons.clear();
        for (SettingsPage child : this.currentPage.children())
        {
            this.navigationButtons.add(this.addDrawableChild(topicButton(child, false, () -> {
                ToolDirectory tool = toolForPage(child);
                if (tool != null) {
                    this.openTool(tool);
                    return;
                }
                this.pageHistory.add(new PageLocation(this.currentPage, this.scrollY));
                this.openPage(child);
            })));
        }
    }

    static ToolDirectory toolForPage(SettingsPage page) {
        return page.source() != null && page.source().title().equals("Tools") && page.rows().size() == 1
                ? ToolDirectory.find(page.title()) : null;
    }

    private static Item toolIcon(ToolDirectory tool) {
        return switch (tool) {
            case HOTKEYS -> Items.TRIPWIRE_HOOK;
            case PROJECTS -> Items.IRON_PICKAXE;
            case PROFILE -> Items.PLAYER_HEAD;
            case HISTORY -> Items.CLOCK;
            case SUMMARY -> Items.WRITTEN_BOOK;
            case SCOREBOARD -> Items.OAK_SIGN;
            case LEADERBOARDS -> Items.GOLD_INGOT;
        };
    }

    private void openPage(SettingsPage page) {
        this.currentPage = page;
        this.refreshVisibleSections();
        this.scrollY = 0;
        this.transitionAt = CollectionBoardArt.now();
        this.resetPageFocus();
        this.rebuildNavigationButtons();
    }

    private NavigationButton topicButton(SettingsPage page, boolean rail, Runnable action) {
        ToolDirectory tool = toolForPage(page);
        String hint = tool != null ? tool.description : switch (page.title()) {
            case "Tools" -> page.children().size() + " tools";
            case "Everyday settings" -> "Tracking, goal, and chat";
            case "Digging & goals" -> "Tools and milestones";
            case "HUD" -> "Layout, content, and colors";
            case "Mining visuals" -> "Indicators and highlights";
            case "Website sync" -> "Link and sync options";
            case "Social" -> "Messages and active diggers";
            default -> !page.children().isEmpty() ? page.children().size() + " topics"
                    : page.rows().size() + (page.rows().size() == 1 ? " setting" : " settings");
        };
        SidebarItem category = page.source() == null ? switch (page.title()) {
            case "HUD" -> SidebarItem.HUD;
            case "Mining visuals" -> SidebarItem.VISUALS;
            default -> SidebarItem.GENERAL;
        } : page.source().sidebarItem();
        Item icon = tool != null ? toolIcon(tool) : page.title().equals("Tools") ? Items.CRAFTING_TABLE : switch (category) {
            case GENERAL -> page.title().equals("Everyday settings") ? Items.COMPASS : Items.IRON_PICKAXE;
            case SYNC -> Items.ENDER_CHEST;
            case NOTIFICATIONS -> Items.BELL;
            case SOCIAL -> Items.WRITABLE_BOOK;
            case HUD -> Items.SPYGLASS;
            case COLORS -> Items.RED_DYE;
            case VISUALS, BLOCK_ESP -> Items.AMETHYST_SHARD;
            default -> Items.COMPARATOR;
        };
        var button = new NavigationButton(page.title(), 120, rail ? 21 : topicStep() - 5, ignored -> action.run());
        button.caption = rail && page.title().equals("Everyday settings") ? "Everyday" : page.title();
        button.hint = rail ? "" : hint;
        button.icon = new ItemStack(icon);
        button.order = rail ? 0 : navigationButtons.size();
        button.fileNumber = (button.order + 1 < 10 ? "0" : "") + (button.order + 1);
        if (!rail && hasDossier()) collectOptions(page,button.options);
        else button.hintText(page.title() + ": " + hint);
        return button;
    }

    private static void collectOptions(SettingsPage page, List<SettingRow> result) {
        for (var row : page.rows()) if (row.config() != null || toolForPage(page) != null) result.add(row);
        for (var child : page.children()) collectOptions(child,result);
    }

    private void drawDossier(Canvas c, int x, int y, int w, NavigationButton topic) {
        int h = 155;
        CollectionBoardArt.dossier(c,x,y,w,h);
        long elapsed = CollectionBoardArt.now() - previewSince;
        int tx = x+10, edge = x+w-10;
        c.raw().drawText(textRenderer,"CATEGORY FILE",tx,y+12,MUTED,false);
        MmmUi.drawTextRightWithin(c.raw(),textRenderer,topic.fileNumber,edge,y+12,30,CollectionBoardArt.ROSE,false);
        int inset = Math.round(5*(1-CollectionBoardArt.reveal(elapsed,0)));
        c.raw().drawItem(topic.icon,tx+inset,y+27);
        MmmUi.drawTextWithin(c.raw(),textRenderer,topic.caption,tx+23+inset,y+32,w-45,TEXT,true);
        c.raw().fill(tx,y+48,edge,y+49,CollectionBoardArt.LINE);
        int shown = 0, modified = 0;
        for (var row : topic.options) {
            if (row.config() != null && row.config().isModified()) modified++;
            if (shown == 4 || row.kind() == ControlKind.TEXT) continue;
            String value = row.config() instanceof IConfigBoolean b ? (b.getBooleanValue() ? "On" : "Off")
                    : row.config() instanceof ConfigOptionList list
                    ? list.getOptionListValue().getDisplayName() : getConfigString(row.config());
            int valueW = Math.min(w/3,textRenderer.getWidth(value));
            int yy = y+59+shown*17;
            int slide = Math.round(6*(1-CollectionBoardArt.reveal(elapsed,shown+1)));
            MmmUi.drawTextWithin(c.raw(),textRenderer,row.label(),tx+slide,yy,edge-tx-valueW-slide-6,TEXT,false);
            MmmUi.drawTextRightWithin(c.raw(),textRenderer,value,edge,yy,valueW,MUTED,false);
            shown++;
        }
        c.raw().fill(tx,y+h-27,edge,y+h-26,CollectionBoardArt.LINE);
        String footer = topic.caption.equals("Tools") ? topic.options.size() + " tools"
                : modified == 0 ? "Default values" : modified + " customized";
        c.raw().drawText(textRenderer,footer,tx,y+h-17,CollectionBoardArt.ROSE,false);
    }

    private void resetPageFocus()
    {
        this.draggingSlider = null;
        if (this.getFocused() != null) BoardPlatform.unfocus(this.getFocused());
        this.setFocused(null);
        for (BoardField field : this.textFields.values())
        {
            field.focus(false);
            field.setVisible(false);
        }
        this.clickTargets.clear();
        this.scrollTargets.clear();
        this.sliderTargets.clear();
    }

    private void goBack()
    {
        if (!this.searchQuery.isBlank())
        {
            this.searchField.setText("");
            this.resetPageFocus();
            return;
        }
        if (this.pageHistory.isEmpty()) return;
        PageLocation previous = this.pageHistory.remove(pageHistory.size() - 1);
        this.currentPage = previous.page();
        this.refreshVisibleSections();
        this.transitionAt = CollectionBoardArt.now();
        this.scrollY = previous.scrollY();
        this.resetPageFocus();
        this.rebuildNavigationButtons();
    }

    static SettingsPage buildSettingsPages(List<SettingsSection> sections)
    {
        return adaptPage(SettingsNavigation.<SettingRow, SettingsSection>build(sections));
    }

    private static SettingsPage adaptPage(SettingsNavigation.Page<SettingRow, SettingsSection> page)
    {
        return new SettingsPage(page.title(), page.source(), page.rows(), page.children().stream()
                .map(SettingsBoard::adaptPage).toList());
    }

    private static void organizeSections(List<SettingsSection> sections)
    {
        List<SettingRow> everyday = new ArrayList<>();
        java.util.Set<String> quickLabels = java.util.Set.of("Mining Tracker", "Main HUD", "Move HUD", "Daily Goal", "MMM Chat Messages");
        List<SettingsSection> grouped = new ArrayList<>();
        for (SettingsSection section : sections)
        {
            List<SettingRow> remaining = new ArrayList<>();
            for (SettingRow row : section.rows())
            {
                if (quickLabels.contains(row.label())) everyday.add(row);
                else remaining.add(row);
            }
            String title = switch (section.title())
            {
                case "MINING HELPERS" -> "Digging helpers";
                case "SYNC" -> "Website sync";
                case "GOALS & NOTIFICATIONS" -> "Goals & notifications";
                case "SOCIAL" -> "Social";
                default -> friendlyHeading(section.title());
            };
            grouped.add(new SettingsSection(section.sidebarItem(), title, section.description(), List.copyOf(remaining)));
        }
        List<String> order = List.of("Mining Tracker", "Main HUD", "Move HUD", "Daily Goal", "MMM Chat Messages");
        everyday.sort(java.util.Comparator.comparingInt(row -> order.indexOf(row.label())));
        everyday.add(new SettingRow("Collections", "Inspect block collections and milestones.", null, ControlKind.ACTION));
        sections.clear();
        sections.add(new SettingsSection(SidebarItem.GENERAL, "Everyday settings", "Tracking, HUD, daily goal and chat", List.copyOf(everyday)));
        sections.addAll(grouped);
    }

    private static String friendlyHeading(String title)
    {
        return SettingsNavigation.friendlyHeading(title);
    }

    private void refreshVisibleSections()
    {
        this.visibleSections.clear();
        String query = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty())
        {
            if (!this.currentPage.rows().isEmpty())
                this.visibleSections.add(new VisibleSection(this.currentPage.source(), this.currentPage.rows()));
            return;
        }
        this.visibleSections.addAll(searchSections(this.sections, query));
    }

    static List<VisibleSection> searchSections(List<SettingsSection> sections, String query)
    {
        return SettingsNavigation.<SettingRow, SettingsSection>search(sections, query).stream()
                .map(match -> new VisibleSection(match.section(), match.rows())).toList();
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
        BoardField field = this.textFields.get(config);
        if (field != null)
        {
            field.setText(this.getConfigString(config));
        }
    }

    private String getConfigString(IConfigBase config)
    {
        if (config != null)
        {
            return config.getStringValue();
        }
        return "";
    }

    private void setConfigString(IConfigBase config, String value)
    {
        if (config != null)
        {
            config.setValueFromString(value);
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

    private void drawSyncStatusRow(Canvas context, int x, int y, int width)
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
        MmmUi.drawTextWithin(context.raw(), this.textRenderer, "Current Status", x, y + 7, Math.max(0, width - labelWidth - 12), TEXT, false);
        MmmUi.drawTextRightWithin(context.raw(), this.textRenderer, label, x + width, y + 7, Math.max(0, width / 2), statusColor, false);
        MmmUi.drawTextWithin(context.raw(), this.textRenderer, CloudSyncManager.getStatusDetail(), x, y + 18, width, MUTED, false);
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

    // Inventory construction must not instantiate Screen: 1.21.11 requires a live client there.
    static List<SettingsSection> settingsInventory()
    {
        List<SettingsSection> sections = new ArrayList<>();
        MmmSettingsScreen.createSections(sections);
        organizeSections(sections);
        List<SettingRow> tools = new ArrayList<>();
        for (ToolDirectory tool : ToolDirectory.values())
            if (MmmUi.TOOL_SCREENS.containsKey(tool))
                tools.add(new SettingRow(tool.label, tool.description, null, ControlKind.ACTION));
        sections.add(1, new SettingsSection(SidebarItem.GENERAL, "Tools", "Mining tools and records", List.copyOf(tools)));
        return List.copyOf(sections);
    }

    void openIgnoreList() {}

    enum SidebarItem
    {
        GENERAL("General"),
        SYNC("Sync"),
        NOTIFICATIONS("Notifications"),
        SOCIAL("Social"),
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

    enum ControlKind
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

    record SettingRow(String label, String description, IConfigBase config, ControlKind kind) implements SettingsNavigation.Row
    {
        @Override public boolean heading() { return kind == ControlKind.HEADING; }
        @Override public String configName() { return config == null ? "" : config.getName(); }

        static SettingRow heading(String label)
        {
            return new SettingRow(label, "", null, ControlKind.HEADING);
        }
    }

    record VisibleSection(SettingsSection section, List<SettingRow> rows)
    {
    }

    record SettingsPage(String title, SettingsSection source, List<SettingRow> rows, List<SettingsPage> children)
    {
    }

    private record PageLocation(SettingsPage page, double scrollY)
    {
    }

    private final class NavigationButton extends BoardButton {
        private final List<SettingRow> options = new ArrayList<>();
        private ItemStack icon = ItemStack.EMPTY;
        private String caption, hint = "", fileNumber = "";
        private int order;
        private boolean chosen;
        private long hoverFrame;
        private float hover;

        private NavigationButton(String label, int width, int height, PressAction action) {
            super(0, 0, width, height, BoardPlatform.literal(label), action);
            caption = label;
        }
        @Override protected void renderFace(Canvas c, int mx, int my, float delta) {
            long now = CollectionBoardArt.now();
            boolean hot = BoardPlatform.keyboard() ? isFocused() : isMouseOver(mx, my);
            float step = Math.min(1f, (now - hoverFrame) / 105f);
            hover = net.minecraft.util.math.MathHelper.clamp(hover + (hot ? step : -step), 0f, 1f);
            hoverFrame = now;
            float enter = CollectionBoardArt.reveal(now - transitionAt, order);
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean slip = !hint.isEmpty();
            boolean inspecting = slip && hasDossier() && previewTopic == this;
            var matrix = c.raw().getMatrices();
            matrix.push();
            try {
                if (slip) {
                    c.raw().fill(x+3,y+h-3,x+w-2,y+h,CollectionBoardArt.BLACK);
                    // Flip the face into its fixed hit area; never animate the widget bounds.
                    matrix.translate(x+w/2f,y+h/2f - (inspecting ? 1 : 0) - hover,0);
                    matrix.scale(1,.74f+.26f*enter,1);
                    matrix.translate(-x-w/2f,-y-h/2f,0);
                }
                int face = chosen ? MmmUi.accentSoft() : CollectionBoardArt.TILE;
                if (inspecting) face = CollectionBoardArt.ACTIVE;
                int surface = MmmUi.menuSurface(CollectionBoardArt.lerp(hover, face, 0xff443236));
                if (slip) {
                    CollectionBoardArt.plate(c,x,y+2,w,h-4,inspecting ? 0xff96545c : 0xff4c4245,surface);
                    c.raw().fill(x+7,y,x+23,y+3,inspecting ? MmmUi.accent() : 0xff685156);
                } else c.raw().fill(x,y,x+w,y+h,surface);
                if (chosen || hot) c.raw().fill(x, y + 2, x + 2, y + h-2, MmmUi.accent());
                if (isFocused()) c.raw().drawBorder(x, y, w, h, CollectionBoardArt.ROSE);
                int tx = x + 6 + Math.round(4 * (1f - enter));
                if (!icon.isEmpty()) {
                    int iy = y + (h - 16) / 2;
                    if (!hint.isEmpty()) c.raw().fill(tx, iy - 3, tx + 22, iy + 19, CollectionBoardArt.RAIL);
                    c.raw().drawItem(icon, tx + (hint.isEmpty() ? 0 : 3), iy);
                    tx += hint.isEmpty() ? 20 : 28;
                }
                int room = Math.max(6, x + w - tx - (hint.isEmpty() ? 4 : 11));
                int ty = y + (hint.isEmpty() ? (h - 8) / 2 : (h - 20) / 2);
                c.raw().drawText(textRenderer, MmmUi.truncate(textRenderer, caption, room), tx, ty,
                        chosen ? CollectionBoardArt.ROSE : TEXT, false);
                if (!hint.isEmpty()) {
                    c.raw().drawText(textRenderer, MmmUi.truncate(textRenderer, hint, room), tx, ty + 12, MUTED, false);
                    c.raw().drawText(textRenderer, ">", x + w - 9, y + (h - 8) / 2, hot ? TEXT : MUTED, false);
                }
            } finally { matrix.pop(); }
        }
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

    static final class SettingsSection implements SettingsNavigation.Section<SettingRow>
    {
        @Override public String category() { return sidebarItem.name(); }
        @Override public String categoryLabel() { return sidebarItem.label(); }

        private final SidebarItem sidebarItem;
        private final String title;
        private final String description;
        private final List<SettingRow> rows;

        SettingsSection(SidebarItem sidebarItem, String title, String description, List<SettingRow> rows)
        {
            this.sidebarItem = sidebarItem;
            this.title = title;
            this.description = description;
            this.rows = rows;
        }

        static SettingsSection helpers(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.GENERAL, "MINING HELPERS", "Configure optional mining helpers.", List.of(rows));
        }

        static SettingsSection sync(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.SYNC, "SYNC", "Choose what MMM sends to the website.", List.of(rows));
        }

        static SettingsSection notifications(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.NOTIFICATIONS, "GOALS & NOTIFICATIONS", "Set your goal and milestone alerts.", List.of(rows));
        }

        static SettingsSection social(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.SOCIAL, "SOCIAL", "Manage MMM chat, milestones, and active diggers.", List.of(rows));
        }

        static SettingsSection hud(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.HUD, "HUD LAYOUT", "Move and resize the main HUD.", List.of(rows));
        }

        static SettingsSection hudContent(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.HUD, "HUD CONTENT", "Choose exactly what appears on screen.", List.of(rows));
        }

        static SettingsSection colors(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.COLORS, "HUD COLORS", "Choose colors for menus and HUD text.", List.of(rows));
        }

        static SettingsSection visuals(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.VISUALS, "VISUALS", "Tune mining visuals and speed readings.", List.of(rows));
        }


        static SettingsSection speedGraph(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.SPEED_GRAPH, "SPEED GRAPH", "Change how the live graph looks.", List.of(rows));
        }

        static SettingsSection blockEsp(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.BLOCK_ESP, "BLOCK ESP", "Change how block highlights look.", List.of(rows));
        }

        static SettingsSection developer(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.ABOUT, "DEV TOOLS", "Generate local test data for MMM screens.", List.of(rows));
        }

        private SidebarItem sidebarItem()
        {
            return this.sidebarItem;
        }

        public String title()
        {
            return this.title;
        }

        public String description()
        {
            return this.description;
        }

        public List<SettingRow> rows()
        {
            return this.rows;
        }
    }
}
