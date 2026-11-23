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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.util.ARGB;

public class MmmSettingsScreen extends CompatScreen
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
    private final Map<IConfigBase, EditBox> textFields = new IdentityHashMap<>();
    private IConfigBase movingToggle;
    private long toggleAt;
    private final SettingsPage home;
    private final List<NavigationButton> categoryRail = new ArrayList<>();
    private long transitionAt;
    private final SettingsPage toolsPage;
    private NavigationButton hotkeysButton;
    private SettingsPage currentPage;
    private final List<PageLocation> pageHistory = new ArrayList<>();
    private final List<NavigationButton> navigationButtons = new ArrayList<>();
    private NavigationButton previewTopic;
    private long previewSince;
    private Button backButton;
    private double browseScrollY;
    private String hoveredDescription;
    private double scrollY = 0.0D;
    private int contentHeight = 0;
    private SliderTarget draggingSlider;
    private EditBox searchField;
    private String searchQuery = "";
    private boolean suppressAutomaticSearchInput;
    private final long openedAtMs;

    public MmmSettingsScreen(Screen parent)
    {
        this(parent, false);
    }

    public MmmSettingsScreen(Screen parent, boolean openedFromHotkey)
    {
        super(Component.literal("MMM Mod Settings"));
        this.parent = parent;
        this.suppressAutomaticSearchInput = openedFromHotkey;
        this.openedAtMs = System.currentTimeMillis();
        this.sections.addAll(settingsInventory());
        this.home = buildSettingsPages(this.sections);
        this.toolsPage = this.home.children().stream().filter(page -> page.title().equals("Tools")).findFirst().orElseThrow();
        this.currentPage = this.home;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.clearWidgets();
        this.textFields.clear();
        this.navigationButtons.clear();
        this.categoryRail.clear();
        this.transitionAt = CollectionBoardArt.now();

        this.searchField = new EditBox(this.font, 0, 0, 220, FIELD_HEIGHT, Component.literal("Search settings"));
        this.searchField.setBordered(false);
        this.searchField.setTextColor(TEXT);
        this.searchField.setMaxLength(64);
        this.searchField.setHint(Component.literal("Search settings..."));
        this.searchField.setValue(this.searchQuery);
        this.searchField.setResponder(value -> {
            boolean wasSearching = !this.searchQuery.isBlank();
            if (!wasSearching && !value.isBlank()) this.browseScrollY = this.scrollY;
            this.searchQuery = value;
            this.scrollY = value.isBlank() ? this.browseScrollY : 0.0D;
            this.draggingSlider = null;
            this.refreshVisibleSections();
        });
        this.addRenderableWidget(this.searchField);
        var website = this.addRenderableWidget(new NavigationButton("Website Link", 82, 19, button -> openWebsiteLink()));
        website.setPosition(workspaceX() + workspaceWidth() - 107, 7);
        this.hotkeysButton = this.addRenderableWidget(new NavigationButton(ToolDirectory.HOTKEYS.label, 62, 18,
                button -> MmmUi.openTool(this, ToolDirectory.HOTKEYS)));
        this.backButton = this.addRenderableWidget(new NavigationButton("< Back", 48, 18, button -> this.goBack()));
        for (SettingsPage page : this.home.children()) {
            this.categoryRail.add(this.addRenderableWidget(topicButton(page, true, () -> {
                this.pageHistory.clear();
                this.pageHistory.add(new PageLocation(this.home, 0));
                this.searchField.setValue("");
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
                    EditBox field = new EditBox(this.font, 0, 0, CONTROL_WIDTH, FIELD_HEIGHT, Component.empty());
                    field.setBordered(false);

                    field.setTextColor(TEXT);
                    field.setTextColorUneditable(MUTED);
                    field.setMaxLength(row.kind() == ControlKind.COLOR ? 9 : 64);
                    field.setValue(this.getConfigString(row.config()));
                    field.setResponder(value -> this.commitTextValue(row, value));
                    this.textFields.put(row.config(), field);
                    this.addRenderableWidget(field);
                }
            }
        }
    }

    protected boolean acceptsAutomaticSearchInput() { return searchField != null && searchField.isVisible(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        this.clickTargets.clear();
        this.scrollTargets.clear();
        this.sliderTargets.clear();
        this.hoveredDescription = null;
        for (EditBox field : this.textFields.values())
        {
            field.setVisible(false);
        }
        this.searchField.setVisible(true);
        this.updateLayout();

        context.fill(0, 0, this.width, this.height, MmmUi.menuSurface(BG));
        this.drawTopBar(context, mouseX, mouseY);

        int pagePad = MmmUi.pagePad(this.width);
        int viewportX = contentX();
        int viewportY = TOP_HEIGHT;
        int viewportW = contentWidth();
        int viewportH = Math.max(1, this.height - TOP_HEIGHT - pagePad);

        layoutRail(context);
        this.drawMainContent(context, viewportX, viewportY, viewportW, mouseX, mouseY);
        super.extractRenderState(context, mouseX, mouseY, delta);

        this.drawScrollbar(context, viewportX + viewportW - 4, viewportY, viewportH);
        if (this.hoveredDescription != null)
        {
            context.setTooltipForNextFrame(this.font, this.font.split(Component.literal(this.hoveredDescription),
                    Math.min(280, Math.max(80, this.width - 32))), mouseX, mouseY);
        }
        for (EditBox field : this.textFields.values())
        {
            if (!field.isVisible() && field.isFocused())
            {
                field.setFocused(false);
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

        return false;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick)
    {
        SettingsPage previousPage = currentPage;
        boolean handled = super.mouseClicked(click, doubleClick);
        // Container dispatch may focus a button after its callback has removed it.
        if (previousPage != currentPage) resetPageFocus();
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

        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE)
        {
            if (this.searchField != null && !this.searchQuery.isBlank())
            {
                this.searchField.setValue("");
                return true;
            }
            if (!this.pageHistory.isEmpty()) this.goBack();
            else this.onClose();
            return true;
        }

        boolean pageKey = keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN;
        if ((!(this.getFocused() instanceof EditBox) || (pageKey && this.getFocused() == this.searchField))
                && (pageKey || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_HOME || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_END))
        {
            int maxScroll = Math.max(0, this.contentHeight - (this.height - TOP_HEIGHT - MmmUi.pagePad(this.width)));
            int step = Math.max(ROW_HEIGHT, this.height - TOP_HEIGHT - CONTENT_HEADER_HEIGHT - 32);
            this.scrollY = switch (keyCode) {
                case com.mojang.blaze3d.platform.InputConstants.KEY_HOME -> 0;
                case com.mojang.blaze3d.platform.InputConstants.KEY_END -> maxScroll;
                case com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP -> Math.max(0, this.scrollY - step);
                default -> Math.min(maxScroll, this.scrollY + step);
            };
            return true;
        }

        if (this.searchField != null
                && this.searchField.isVisible()
                && this.searchField.isFocused()
                && this.searchField.keyPressed(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers)))
        {
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input)
    {
        SettingsPage previousPage = currentPage;
        boolean handled = super.keyPressed(input);
        if (previousPage != currentPage) resetPageFocus();
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
        for (EditBox field : this.textFields.values())
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
            this.searchField.setFocused(true);
            return this.searchField.charTyped(new net.minecraft.client.input.CharacterEvent(chr));
        }
        return false;
    }

    @Override
    public void onClose()
    {
        BoardPlatform.open(this.parent);
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

    private int workspaceWidth() { return Math.min(650, width - 16); }
    private int workspaceX() { return (width - workspaceWidth()) / 2; }
    private boolean railVisible() { return width >= 530 && height >= 254 && currentPage != home && currentPage != toolsPage; }
    private int contentX() { return workspaceX() + (railVisible() ? 134 : 0); }
    private int contentWidth() { return workspaceWidth() - (railVisible() ? 134 : 0); }
    private int navColumns() { return contentWidth() >= 300 ? 2 : 1; }
    private boolean hasDossier() { return currentPage == home && contentWidth() >= 500 && height >= 276; }
    private int dossierWidth() { return Math.min(218, contentWidth() / 3); }
    private int topicStep() { return hasDossier() ? 54 : 41; }

    private void drawTopBar(GuiGraphicsExtractor c, int mx, int my) {
        int left = workspaceX(), right = left + workspaceWidth();
        c.fill(0, 0, width, TOP_HEIGHT, MmmUi.menuSurface(TOP_BAR));
        c.fill(0, TOP_HEIGHT - 1, width, TOP_HEIGHT, CollectionBoardArt.LINE);
        c.text(font, "MMM", left + 2, 13, CollectionBoardArt.ROSE, true);
        c.text(font, "/ Settings", left + 29, 13, TEXT, false);
        if (width >= 510) {
            String status = syncStatusText();
            MmmUi.drawTextRightWithin(c, font, status, right - 115, 13, workspaceWidth() / 3, MUTED, false);
        }
        drawButtonShell(c, right - 20, 7, 20, 19, "X", mx, my, false);
        clickTargets.add(new ClickTarget(right - 20, 7, 20, 19, this::onClose));
    }

    private void layoutRail(GuiGraphicsExtractor c) {
        boolean visible = railVisible();
        int left = workspaceX(), top = TOP_HEIGHT + 15;
        if (visible) {
            c.text(font, "CATEGORIES", left + 6, top, MUTED, false);
            c.fill(left + 125, top, left + 126, height - 10, CollectionBoardArt.LINE);
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
        BoardPlatform.open(new WebsiteLinkScreen(this));
    }

    private void drawMainContent(GuiGraphicsExtractor context, int x, int viewportY, int width, int mouseX, int mouseY)
    {
        int y = viewportY + 10;
        boolean searching = !this.searchQuery.isBlank();
        this.backButton.visible = searching || !this.pageHistory.isEmpty();
        this.backButton.setPosition(x, y - 5);
        hotkeysButton.visible = !searching && currentPage == home;
        hotkeysButton.setPosition(x + width - hotkeysButton.getWidth() - 10, y - 5);
        int titleX = x + (this.backButton.visible ? 53 : 0);
        String title = searching ? "Search results" : this.currentPage == home ? "Controls & preferences" : this.currentPage.title();
        if (!searching && pageHistory.size() > 1 && contentWidth() > 370)
            title = pageHistory.getLast().page().title() + " / " + title;
        int titleWidth = Math.max(0, (hotkeysButton.visible ? hotkeysButton.getX() - 4 : x + width - 8) - titleX);
        MmmUi.drawTextWithin(context, this.font, title, titleX, y, titleWidth, TEXT, false);
        if (this.font.width(title) > titleWidth
                && this.contains(mouseX, mouseY, titleX, y, titleWidth, 12)) this.hoveredDescription = title;

        int searchY = y + 18;
        int searchWidth = width - 10;
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
                this.searchField.setValue("");
                this.setFocused(this.searchField);
                this.searchField.setFocused(true);
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
            if (showDossier && button.visible && (minecraft.getLastInputType().isKeyboard()
                    ? button.isFocused() : button.isMouseOver(mouseX, mouseY))) {
                if (previewTopic != button) { previewTopic = button; previewSince = CollectionBoardArt.now(); }
            }
            if (!button.visible && button.isFocused())
            {
                button.setFocused(false);
                if (this.getFocused() == button) this.setFocused(null);
            }
        }
        if (showDossier && !navigationButtons.isEmpty()) {
            if (previewTopic == null) { previewTopic = navigationButtons.getFirst(); previewSince = transitionAt; }
            drawDossier(context,x + width - dossierWidth() - 6,y + CONTENT_HEADER_HEIGHT,dossierWidth(),previewTopic);
        }
        if (!searching && !this.currentPage.children().isEmpty()) return;
        if (this.visibleSections.isEmpty())
        {
            MmmUi.drawTextWithin(context, this.font, "No settings match your search.", x, gridY + 8, width, MUTED, false);
            return;
        }

        int rowY = gridY;

        context.enableScissor(x, y + CONTENT_HEADER_HEIGHT, x + width, this.height - MmmUi.pagePad(this.width));
        for (VisibleSection section : this.visibleSections)
        {
            int rowHeight = this.sectionHeight(section);
            this.drawSection(context, section, x, rowY, width, rowHeight, mouseX, mouseY);
            rowY += rowHeight + GAP;
        }
        context.disableScissor();
    }

    private void drawSection(GuiGraphicsExtractor context, VisibleSection visibleSection, int x, int y, int width, int height, int mouseX, int mouseY)
    {
        SettingsSection section = visibleSection.section();
        if (!this.searchQuery.isBlank())
        {
            boolean hovered = this.contains(mouseX, mouseY, x, y, width - 8, 44);
            context.fill(x, y, x + width - 8, y + 44, MmmUi.menuSurface(hovered ? INSET : CARD));
            context.fill(x, y + 43, x + width - 8, y + 44, MmmUi.accent());
            MmmUi.drawTextWithin(context, this.font, section.title(), x + CARD_PAD, y + 9, width - 32, TEXT, false);
            MmmUi.drawTextWithin(context, this.font, section.description(), x + CARD_PAD, y + 25, width - 32, MUTED, false);
        }

        int rowY = y + (this.searchQuery.isBlank() ? 4 : 48);
        for (SettingRow row : visibleSection.rows())
        {
            this.drawSettingRow(context, row, x + CARD_PAD, rowY, width - CARD_PAD * 2, mouseX, mouseY);
            rowY += this.settingRowHeight();
        }
    }

    private void drawSettingRow(GuiGraphicsExtractor context, SettingRow row, int x, int y, int width, int mouseX, int mouseY)
    {
        int rowHeight = this.settingRowHeight();
        int clipTop = TOP_HEIGHT + 10 + CONTENT_HEADER_HEIGHT;
        int clipBottom = this.height - MmmUi.pagePad(this.width);
        if (visibleHeight(y, rowHeight, clipTop, clipBottom) == 0) return;
        if (contains(mouseX, mouseY, x, y, width, rowHeight) && mouseY >= clipTop && mouseY < clipBottom)
            context.fill(x - 4, y, x + width + 3, y + rowHeight, MmmUi.menuSurface(CollectionBoardArt.TILE));
        context.fill(x, y + rowHeight - 1, x + width, y + rowHeight, BORDER);
        if (row.kind() == ControlKind.HEADING)
        {
            context.fill(x, y + 8, x + 3, y + 24, MmmUi.accent());
            MmmUi.drawTextWithin(context, this.font, row.label(), x + 10, y + 12, width - 10, TEXT, false);
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
                && (this.font.width(row.description()) > labelW || this.font.width(row.label()) > labelW))
        {
            this.hoveredDescription = row.label() + ": " + row.description();
        }
        MmmUi.drawTextWithin(context, this.font, row.label(), x, y + 7, labelW, TEXT, false);
        MmmUi.drawTextWithin(context, this.font, row.description(), x, y + 18, labelW, MUTED, false);

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
                    BoardPlatform.open(new HudMoveScreen(this));
                }
                else if ("Custom Sounds".equals(row.label()))
                {
                    BoardPlatform.open(new GoalSoundSettingsScreen(this));
                }
                else if ("Perimeter Block List".equals(row.label()))
                {
                    BoardPlatform.open(new PerimeterBlockListScreen(this));
                }
                else if ("Collections".equals(row.label())) {
                    BoardPlatform.open(new CollectionBookScreen(this));
                }
                else if ("Ignored Players".equals(row.label()))
                {
                    BoardPlatform.open(BoardPlatform.ignoreList(this));
                }
                else if ("Sync Scoreboard".equals(row.label()))
                {
                    BoardPlatform.open(new SyncScoreboardScreen(this));
                }
                else if ("Generate Dev Run".equals(row.label()))
                {
                    MmmMessages.actionbar("Generated preview mining run");
                    BoardPlatform.open(new SummaryScreen(MiningStats.simulateDevFinishedSession(), this));
                }
                else {
                    ToolDirectory tool = ToolDirectory.find(row.label());
                    if (tool != null) openTool(tool);
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
        // Partially visible rows still draw under the scissor, but clipped controls cannot intercept input.
        if (visibleHeight(controlY, FIELD_HEIGHT, clipTop, clipBottom) != FIELD_HEIGHT)
        {
            this.clickTargets.subList(firstClick, this.clickTargets.size()).clear();
            this.scrollTargets.subList(firstScroll, this.scrollTargets.size()).clear();
            this.sliderTargets.subList(firstSlider, this.sliderTargets.size()).clear();
        }
        EditBox field = this.textFields.get(row.config());
        if (field != null && visibleHeight(field.getY(), field.getHeight(), clipTop, clipBottom) != field.getHeight())
            field.setVisible(false);
    }

    static int visibleHeight(int y, int height, int clipTop, int clipBottom)
    {
        return SettingsNavigation.visibleHeight(y, height, clipTop, clipBottom);
    }

    private void drawBooleanControl(GuiGraphicsExtractor context, IConfigBase config, int x, int y, int width, int mouseX, int mouseY)
    {
        boolean enabled = config instanceof IConfigBoolean booleanConfig && booleanConfig.getBooleanValue();
        boolean hovered = this.contains(mouseX, mouseY, x, y, width, FIELD_HEIGHT);
        int edge = x + width - 2;
        int rail = edge - 23;
        context.fill(rail, y + 3, edge, y + 15, enabled ? MmmUi.accent() : CollectionBoardArt.LINE);
        float t = config == movingToggle ? CollectionBoardArt.reveal(CollectionBoardArt.now() - toggleAt, 0) : 1f;
        int knob = rail + 2 + Math.round(11 * (enabled ? t : 1f - t));
        context.fill(knob, y + 5, knob + 8, y + 13, enabled || hovered ? TEXT : MUTED);
        MmmUi.drawTextRightWithin(context, font, enabled ? "On" : "Off", rail - 5, y + 5, width - 32, enabled ? TEXT : MUTED, false);
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

    private void drawOptionControl(GuiGraphicsExtractor context, IConfigBase config, int x, int y, int width, int mouseX, int mouseY)
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

    private void drawTextControl(GuiGraphicsExtractor context, SettingRow row, int x, int y, int width, int mouseX, int mouseY)
    {
        EditBox field = this.textFields.get(row.config());
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
            context.outline(x, y, FIELD_HEIGHT, FIELD_HEIGHT, BORDER_SOFT);
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
        field.setTextColor(TEXT);
        field.setTextColorUneditable(MUTED);
        MmmUi.fieldShell(context, fieldX, y, fieldW, FIELD_HEIGHT, field.isFocused());
    }

    private void drawSliderControl(GuiGraphicsExtractor context, IConfigBase config, int x, int y, int width, int mouseX, int mouseY)
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
        context.outline(x, trackY, width, 3, hovered ? MmmUi.accent() : BORDER_SOFT);
        int thumbX = Math.max(x, Math.min(x + width - 4, x + fillWidth - 2));
        context.fill(thumbX, y + 3, thumbX + 4, y + FIELD_HEIGHT - 3, MmmUi.accent());

        int valueWidth = this.font.width(value);
        context.text(this.font, Component.literal(value), x + Math.max(0, (width - valueWidth) / 2), y + 5, TEXT, true);
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

        BoardPlatform.open(new MmmColorEditorScreen(this, colorConfig, () -> this.syncField(config)));
    }
    private void drawActionButton(GuiGraphicsExtractor context, int x, int y, int width, int height, String label, int mouseX, int mouseY, Runnable action)
    {
        this.drawButtonShell(context, x, y, width, height, label, mouseX, mouseY, false);
        this.clickTargets.add(new ClickTarget(x, y, width, height, action));
    }

    private String actionButtonLabel(SettingRow row)
    {
        return "Generate Dev Run".equals(row.label()) ? "RUN" : "Compact HUD".equals(row.label()) ? "APPLY" : "OPEN";
    }

    private void drawButtonShell(GuiGraphicsExtractor context, int x, int y, int width, int height, String label, int mouseX, int mouseY, boolean subtle)
    {
        boolean hovered = this.contains(mouseX, mouseY, x, y, width, height);
        int fill = subtle ? INSET : hovered ? MmmUi.accentHover() : INSET;
        int border = hovered ? MmmUi.accent() : BORDER_SOFT;
        context.fill(x, y, x + width, y + height, MmmUi.menuSurface(fill));
        if (hovered) context.outline(x, y, width, height, border);
        String clipped = MmmUi.truncate(this.font, label, width - 8);
        int textX = x + Math.max(4, (width - this.font.width(clipped)) / 2);
        context.text(this.font, Component.literal(clipped), textX, y + 6, hovered ? TEXT : MUTED, false);
    }

    private void drawScrollbar(GuiGraphicsExtractor context, int x, int y, int height)
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
        for (Button button : this.navigationButtons) this.removeWidget(button);
        this.navigationButtons.clear();
        for (SettingsPage child : this.currentPage.children())
        {
            this.navigationButtons.add(this.addRenderableWidget(topicButton(child, false, () -> {
                ToolDirectory tool = toolForPage(child);
                if (tool != null) {
                    openTool(tool);
                    return;
                }
                this.pageHistory.add(new PageLocation(this.currentPage, this.scrollY));
                this.openPage(child);
            })));
        }
    }

    private static ToolDirectory toolForPage(SettingsPage page) {
        return page.source() != null && "Tools".equals(page.source().title()) && page.rows().size() == 1
                ? ToolDirectory.find(page.rows().getFirst().label()) : null;
    }

    private void openTool(ToolDirectory tool) {
        if (!searchQuery.isEmpty()) searchField.setValue("");
        if (currentPage != toolsPage) {
            pageHistory.clear();
            pageHistory.add(new PageLocation(home, currentPage == home ? scrollY : 0));
            openPage(toolsPage);
        }
        MmmUi.openTool(this, tool);
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
            case "Tools" -> "Hotkeys and mining tools";
            case "Everyday settings" -> "Tracking, goal, and chat";
            case "Digging & goals" -> "Helpers and milestones";
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
        Item icon = switch (category) {
            case TOOLS -> Items.CRAFTING_TABLE;
            case GENERAL -> page.title().equals("Everyday settings") ? Items.COMPASS : Items.IRON_PICKAXE;
            case SYNC -> Items.ENDER_CHEST;
            case NOTIFICATIONS -> Items.BELL;
            case SOCIAL -> Items.WRITABLE_BOOK;
            case HUD -> Items.SPYGLASS;
            case COLORS -> Items.REDSTONE;
            case VISUALS, BLOCK_ESP -> Items.AMETHYST_SHARD;
            default -> Items.COMPARATOR;
        };
        if (tool != null) icon = switch (tool) {
            case HOTKEYS -> Items.TRIPWIRE_HOOK;
            case PROJECTS -> Items.IRON_PICKAXE;
            case PROFILE -> Items.PLAYER_HEAD;
            case HISTORY -> Items.CLOCK;
            case SUMMARY -> Items.PAPER;
            case SCOREBOARD -> Items.OAK_SIGN;
            case LEADERBOARDS -> Items.GOLD_INGOT;
        };
        String label = tool != null || page.title().equals("Tools") ? page.title() : page.title() + " >";
        var button = new NavigationButton(label, 120, rail ? 21 : topicStep() - 5, ignored -> action.run());
        button.caption = rail && page.title().equals("Everyday settings") ? "Everyday" : page.title();
        button.hint = rail ? "" : hint;
        button.icon = new BoardItemIcon(icon);
        button.order = rail ? 0 : navigationButtons.size();
        button.fileNumber = (button.order + 1 < 10 ? "0" : "") + (button.order + 1);
        if (!rail && hasDossier()) collectOptions(page,button.options);
        else button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(page.title() + ": " + hint)));
        button.setTooltipDelay(java.time.Duration.ofMillis(600));
        return button;
    }

    private static void collectOptions(SettingsPage page, List<SettingRow> result) {
        for (var row : page.rows()) if (row.config() != null) result.add(row);
        for (var child : page.children()) collectOptions(child,result);
    }

    private void drawDossier(GuiGraphicsExtractor c, int x, int y, int w, NavigationButton topic) {
        int h = 155;
        CollectionBoardArt.dossier(c,x,y,w,h);
        long elapsed = CollectionBoardArt.now() - previewSince;
        int tx = x+10, edge = x+w-10;
        c.text(font,"CATEGORY FILE",tx,y+12,MUTED,false);
        MmmUi.drawTextRightWithin(c,font,topic.fileNumber,edge,y+12,30,CollectionBoardArt.ROSE,false);
        int inset = Math.round(5*(1-CollectionBoardArt.reveal(elapsed,0)));
        topic.icon.draw(c,tx+inset,y+27);
        MmmUi.drawTextWithin(c,font,topic.caption,tx+23+inset,y+32,w-45,TEXT,true);
        c.fill(tx,y+48,edge,y+49,CollectionBoardArt.LINE);
        if (topic.caption.equals("Tools")) {
            int yy = y + 58;
            for (ToolDirectory tool : MmmUi.availableTools()) {
                MmmUi.drawTextWithin(c, font, tool.label, tx, yy, edge - tx, TEXT, false);
                yy += 13;
            }
            return;
        }
        int shown = 0, modified = 0;
        for (var row : topic.options) {
            if (row.config() instanceof IConfigResettable r && r.isModified()) modified++;
            if (shown == 4 || row.kind() == ControlKind.TEXT) continue;
            String value = row.config() instanceof IConfigBoolean b ? (b.getBooleanValue() ? "On" : "Off")
                    : row.config() instanceof ConfigOptionList list && list.getOptionListValue() instanceof IConfigOptionListEntry entry
                    ? entry.getDisplayName() : getConfigString(row.config());
            int valueW = Math.min(w/3,font.width(value));
            int yy = y+59+shown*17;
            int slide = Math.round(6*(1-CollectionBoardArt.reveal(elapsed,shown+1)));
            MmmUi.drawTextWithin(c,font,row.label(),tx+slide,yy,edge-tx-valueW-slide-6,TEXT,false);
            MmmUi.drawTextRightWithin(c,font,value,edge,yy,valueW,MUTED,false);
            shown++;
        }
        c.fill(tx,y+h-27,edge,y+h-26,CollectionBoardArt.LINE);
        c.text(font,modified == 0 ? "Default values" : modified + " customized",tx,y+h-17,CollectionBoardArt.ROSE,false);
    }

    private void resetPageFocus()
    {
        this.draggingSlider = null;
        if (this.getFocused() != null) this.getFocused().setFocused(false);
        this.setFocused(null);
        for (EditBox field : this.textFields.values())
        {
            field.setFocused(false);
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
            this.searchField.setValue("");
            this.resetPageFocus();
            return;
        }
        if (this.pageHistory.isEmpty()) return;
        PageLocation previous = this.pageHistory.removeLast();
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
                .map(MmmSettingsScreen::adaptPage).toList());
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
        everyday.add(new SettingRow("Collections", "Browse the collection book and tier records.", null, ControlKind.ACTION));
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
        EditBox field = this.textFields.get(config);
        if (field != null)
        {
            field.setValue(this.getConfigString(config));
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

    private void drawSyncStatusRow(GuiGraphicsExtractor context, int x, int y, int width)
    {
        String label = CloudSyncManager.getStatusLabel();
        int statusColor = switch (label)
        {
            case "Synced", "Ready", "Cooldown", "Connected" -> GREEN;
            case "Queued", "Retrying", "Link queued", "Link retrying" -> WARNING;
            case "Error", "Unavailable", "Wrong account" -> ERROR;
            default -> MUTED;
        };
        int labelWidth = Math.min(width / 2, this.font.width(label));
        MmmUi.drawTextWithin(context, this.font, "Current Status", x, y + 7, Math.max(0, width - labelWidth - 12), TEXT, false);
        MmmUi.drawTextRightWithin(context, this.font, label, x + width, y + 7, Math.max(0, width / 2), statusColor, false);
        MmmUi.drawTextWithin(context, this.font, CloudSyncManager.getStatusDetail(), x, y + 18, width, MUTED, false);
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
        createSections(sections);
        organizeSections(sections);
        return List.copyOf(sections);
    }

    private static void createSections(List<SettingsSection> sections)
    {
        sections.clear();
        sections.add(SettingsSection.helpers(
                new SettingRow("Flat Digger", "Stop digging below your feet.", FeatureToggle.MMM_FLAT_DIGGER, ControlKind.BOOLEAN),
                new SettingRow("Perimeter Wall Helper", "Protect the configured floor; sneak to bypass it.", FeatureToggle.MMM_PERIMETER_WALL_DIG_HELPER, ControlKind.BOOLEAN),
                new SettingRow("Perimeter Block List", "Choose which floor blocks are protected.", null, ControlKind.ACTION),
                new SettingRow("Toggle Tab", "Keep the player list open.", FeatureToggle.MMM_TOGGLE_TAB, ControlKind.BOOLEAN)
        ));
        sections.add(SettingsSection.sync(
                new SettingRow("Website Link", "Link your Minecraft account with a website code.", null, ControlKind.ACTION),
                new SettingRow("Sync Status", "", null, ControlKind.STATUS),
                new SettingRow("Website Sync", "Send mining updates.", Configs.Generic.WEBSITE_SYNC_ENABLED, ControlKind.BOOLEAN),
                new SettingRow("Sync Scoreboard", "Choose the total-mined scoreboard for this source.", null, ControlKind.ACTION),
                new SettingRow("Sync Debug", "Save detailed sync logs.", Configs.Generic.WEBSITE_SYNC_DEBUG, ControlKind.BOOLEAN),
                new SettingRow("Abbreviated Numbers", "Show 12k instead of 12,000.", Configs.Generic.ABBREVIATED_NUMBERS, ControlKind.BOOLEAN)
        ));
        sections.add(new SettingsSection(SidebarItem.TOOLS, "Tools", "Hotkeys and mining tools.",
                MmmUi.availableTools().stream().map(tool -> new SettingRow(tool.label, tool.description,
                        null, ControlKind.ACTION)).toList()));
        sections.add(SettingsSection.notifications(
                new SettingRow("Daily Goal", "Blocks to mine today.", Configs.Generic.DAILY_GOAL, ControlKind.NUMBER),
                new SettingRow("Decimal Percent", "Show decimals in goal progress.", Configs.Generic.GOAL_PERCENT_DECIMALS, ControlKind.BOOLEAN),
                new SettingRow("Decimal Places", "Choose 1, 2, or 3 decimal places.", Configs.Generic.GOAL_PERCENT_DECIMAL_PLACES, ControlKind.SLIDER),
                new SettingRow("Goal Tracking", "Track today's goal.", FeatureToggle.MMM_DAILY_GOAL, ControlKind.BOOLEAN),
                new SettingRow("Milestone Messages", "Post progress alerts in chat.", FeatureToggle.MMM_NOTIFICATIONS, ControlKind.BOOLEAN),
                new SettingRow("Sound Alerts", "Play milestone sounds.", FeatureToggle.MMM_SOUND_ALERTS, ControlKind.BOOLEAN),
                new SettingRow("Custom Sounds", "Choose sounds for 25%, 50%, 75%, and 100%.", null, ControlKind.ACTION),
                new SettingRow("Pickaxe Animation", "Show a pickaxe at each milestone.", Configs.Generic.GOAL_PICKAXE_ANIMATION, ControlKind.BOOLEAN)
        ));
        BoardPlatform.addSocial(sections);
        sections.add(SettingsSection.hud(
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
        sections.add(SettingsSection.hudContent(
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
        BoardPlatform.addRankEstimate(sections);
        sections.add(SettingsSection.colors(
                new SettingRow("MMM Menu", "Set the menu accent.", Configs.Generic.MENU_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Menu Opacity", "Change menu background transparency.", Configs.Generic.MENU_OPACITY, ControlKind.SLIDER),
                new SettingRow("HUD Title", "Set the title color.", Configs.Generic.HUD_TITLE_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("HUD Text", "Set the label color.", Configs.Generic.HUD_TEXT_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("HUD Numbers", "Set the number color.", Configs.Generic.HUD_NUMBER_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Inactive Text", "Set the paused color.", Configs.Generic.HUD_INACTIVE_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("HUD Background", "Set the panel color.", Configs.Generic.HUD_BACKGROUND_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Text Shadow", "Add a dark pixel shadow.", Configs.Generic.HUD_TEXT_SHADOW, ControlKind.BOOLEAN)
        ));
        sections.add(SettingsSection.blockEsp(
                new SettingRow("Block ESP", "Highlight the block you look at.", FeatureToggle.MMM_BLOCK_ESP, ControlKind.BOOLEAN),
                new SettingRow("Color Mode", "Use one color or rainbow.", Configs.Generic.BLOCK_ESP_COLOR_MODE, ControlKind.OPTION),
                new SettingRow("Custom Color", "Set the highlight color.", Configs.Generic.BLOCK_ESP_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Render Mode", "Choose the highlight shape.", Configs.Generic.BLOCK_ESP_RENDER_MODE, ControlKind.OPTION),
                new SettingRow("Opacity", "Change highlight strength.", Configs.Generic.BLOCK_ESP_OPACITY, ControlKind.NUMBER),
                new SettingRow("Rainbow Speed", "Change rainbow speed.", Configs.Generic.BLOCK_ESP_RAINBOW_SPEED, ControlKind.NUMBER)
        ));
        if (BoardPlatform.SPEED_GRAPH)
        {
            sections.add(SettingsSection.speedGraph(
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
        sections.add(SettingsSection.visuals(
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
            sections.add(SettingsSection.developer(
                    new SettingRow("Generate Dev Run", "Create a test run.", null, ControlKind.ACTION)
            ));
        }
    }

    enum SidebarItem
    {
        GENERAL("General"),
        TOOLS("Tools"),
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

    private final class NavigationButton extends Button {
        private final List<SettingRow> options = new ArrayList<>();
        private BoardItemIcon icon;
        private String caption, hint = "", fileNumber = "";
        private int order;
        private boolean chosen;
        private long hoverFrame;
        private float hover;

        private NavigationButton(String label, int width, int height, OnPress action) {
            super(0, 0, width, height, Component.literal(label), action, DEFAULT_NARRATION);
            caption = label;
        }
        @Override protected void extractContents(GuiGraphicsExtractor c, int mx, int my, float delta) {
            long now = CollectionBoardArt.now();
            boolean hot = minecraft.getLastInputType().isKeyboard() ? isFocused() : isHovered();
            float step = Math.min(1f, (now - hoverFrame) / 105f);
            hover = Math.clamp(hover + (hot ? step : -step), 0f, 1f);
            hoverFrame = now;
            float enter = CollectionBoardArt.reveal(now - transitionAt, order);
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean slip = !hint.isEmpty();
            boolean inspecting = slip && hasDossier() && previewTopic == this;
            var matrix = c.pose();
            matrix.pushMatrix();
            try {
                if (slip) {
                    c.fill(x+3,y+h-3,x+w-2,y+h,CollectionBoardArt.BLACK);
                    // Flip the face into its fixed hit area; never animate the widget bounds.
                    matrix.translate(x+w/2f,y+h/2f - (inspecting ? 1 : 0) - hover);
                    matrix.scale(1,.74f+.26f*enter);
                    matrix.translate(-x-w/2f,-y-h/2f);
                }
                int face = chosen ? MmmUi.accentSoft() : CollectionBoardArt.TILE;
                if (inspecting) face = CollectionBoardArt.ACTIVE;
                int surface = MmmUi.menuSurface(ARGB.srgbLerp(hover, face, 0xff443236));
                if (slip) {
                    CollectionBoardArt.plate(c,x,y+2,w,h-4,inspecting ? 0xff96545c : 0xff4c4245,surface);
                    c.fill(x+7,y,x+23,y+3,inspecting ? MmmUi.accent() : 0xff685156);
                } else c.fill(x,y,x+w,y+h,surface);
                if (chosen || hot) c.fill(x, y + 2, x + 2, y + h-2, MmmUi.accent());
                if (isFocused()) c.outline(x, y, w, h, CollectionBoardArt.ROSE);
                int tx = x + 6 + Math.round(4 * (1f - enter));
                if (icon != null) {
                    int iy = y + (h - 16) / 2;
                    if (!hint.isEmpty()) c.fill(tx, iy - 3, tx + 22, iy + 19, CollectionBoardArt.RAIL);
                    icon.draw(c, tx + (hint.isEmpty() ? 0 : 3), iy);
                    tx += hint.isEmpty() ? 20 : 28;
                }
                int room = Math.max(6, x + w - tx - (hint.isEmpty() ? 4 : 11));
                int ty = y + (hint.isEmpty() ? (h - 8) / 2 : (h - 20) / 2);
                c.text(font, MmmUi.truncate(font, caption, room), tx, ty,
                        chosen ? CollectionBoardArt.ROSE : TEXT, false);
                if (!hint.isEmpty()) {
                    c.text(font, MmmUi.truncate(font, hint, room), tx, ty + 12, MUTED, false);
                    c.text(font, ">", x + w - 9, y + (h - 8) / 2, hot ? TEXT : MUTED, false);
                }
            } finally { matrix.popMatrix(); }
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

        static SettingsSection social(SettingRow... rows)
        {
            return new SettingsSection(SidebarItem.SOCIAL, "SOCIAL", "Manage MMM chat, milestones, and active diggers.", List.of(rows));
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
