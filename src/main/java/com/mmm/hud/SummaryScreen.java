package com.mmm.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mmm.config.Configs;
import com.mmm.storage.SessionData;
import com.mmm.storage.EfficiencyStore;
import com.mmm.tracker.SessionEfficiency;
import com.mmm.storage.WorldSessionContext;
import com.mmm.tracker.MiningStats;
import com.mmm.ui.MmmUi;
import com.mmm.util.UiFormat;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import com.mmm.compat.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.Identifier;

public class SummaryScreen extends com.mmm.compat.MmmScreen
{
    private static final int PANEL_MARGIN = 20;
    private static final int PANEL_PADDING = 18;
    private static final int CARD_PADDING = 12;
    private static final int CARD_GAP = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SEARCH_HEIGHT = 20;
    private static final int BREAKDOWN_ROW_HEIGHT = 20;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_MIN_THUMB = 18;
    private static final int COLOR_PANEL = MmmUi.PANEL;
    private static final int COLOR_CARD = MmmUi.CARD;
    private static final int COLOR_CARD_SOFT = MmmUi.CARD_SOFT;
    private static final int COLOR_INSET = MmmUi.INSET;
    private static final int COLOR_BORDER = MmmUi.BORDER;
    private static final int COLOR_BORDER_SOFT = MmmUi.BORDER_SOFT;
    private static final int COLOR_VALUE = MmmUi.TEXT;
    private static final int COLOR_LABEL = MmmUi.LABEL;
    private static final int COLOR_MUTED = MmmUi.MUTED;
    private static final int COLOR_INACTIVE = MmmUi.INACTIVE;
    private static final int COLOR_SUCCESS = MmmUi.SUCCESS;
    private static final Map<String, String> NAME_CACHE = new LinkedHashMap<>();
    private static final Map<String, ItemStack> ICON_CACHE = new LinkedHashMap<>();
    private static final String SEARCH_PLACEHOLDER = "Search blocks...";

    private final SessionData session;
    private final Screen parent;
    private final String worldName;
    private final String heading;
    private final boolean showChrome;
    private final List<BlockBreakdownEntry> allEntries = new ArrayList<>();
    private final List<BlockBreakdownEntry> filteredEntries = new ArrayList<>();

    private TextFieldWidget searchField;
    private int contentScroll;
    private int maxContentScroll;
    private boolean efficiencyRequested;
    private boolean efficiencyLoading;
    private String efficiencyError;
    private boolean clipboardMessageVisible;
    private int breakdownScrollOffset;
    private boolean draggingScrollbar;
    private boolean draggingContentScrollbar;
    private long openedAtMs;

    public SummaryScreen(SessionData session, Screen parent)
    {
        this(session, parent, resolveWorldName(), "Session Summary", true);
    }

    public SummaryScreen(SessionData session, Screen parent, String worldName, String heading)
    {
        this(session, parent, worldName, heading, true);
    }

    private SummaryScreen(SessionData session, Screen parent, String worldName, String heading, boolean showChrome)
    {
        super(Text.literal(heading));
        this.session = session;
        this.parent = parent;
        this.worldName = worldName;
        this.heading = heading;
        this.showChrome = showChrome;
    }

    public static SummaryScreen worldExit(SessionData session, Screen parent, String worldName)
    {
        return new SummaryScreen(session, parent, worldName, "Session Summary", false);
    }

    @Override
    protected void init()
    {
        this.clearChildren();
        this.clipboardMessageVisible = false;
        this.openedAtMs = System.currentTimeMillis();
        ensureCursorVisible();
        buildBreakdownEntries();

        Layout layout = computeLayout();
        this.searchField = new TextFieldWidget(this.textRenderer, layout.breakdownX + CARD_PADDING, layout.breakdownY + 28, layout.breakdownWidth - CARD_PADDING * 2, SEARCH_HEIGHT, Text.empty());
        this.searchField.setMaxLength(64);
        this.searchField.setDrawsBackground(false);

        this.searchField.setEditableColor(COLOR_VALUE);
        this.searchField.setUneditableColor(COLOR_MUTED);
        this.searchField.setChangedListener(value -> refreshFilteredEntries());
        this.addDrawableChild(this.searchField);
        loadEfficiency();

        int actionY = layout.headerY;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Copy"), button ->
        {
            MinecraftClient.getInstance().keyboard.setClipboard(buildShareText());
            clipboardMessageVisible = true;
        }).dimensions(layout.panelRight - 148, actionY - 2, 64, BUTTON_HEIGHT).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close()).dimensions(layout.panelRight - 74, actionY - 2, 64, BUTTON_HEIGHT).build());

        refreshFilteredEntries();
        updateDynamicWidgetBounds(layout);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        ensureCursorVisible();
        Layout layout = computeLayout();
        float animation = getOpenAnimationProgress();
        int animatedPanelY = layout.panelY + Math.round((1.0F - animation) * 14.0F);
        Layout animatedLayout = layout.withPanelY(animatedPanelY);
        updateDynamicWidgetBounds(animatedLayout);

        MmmUi.backdrop(context, this.width, this.height);
        if (this.showChrome)
        {
            MmmUi.drawMmmScreensSidebar(context, this.textRenderer, this.width, this.height, mouseX, mouseY, "SUMMARY");
        }
        fillRoundedCard(context, layout.panelX, animatedPanelY, layout.panelWidth, layout.panelHeight, COLOR_PANEL, COLOR_BORDER);

        drawHeader(context, animatedLayout);
        context.enableScissor(layout.panelX + 1, contentTop(animatedLayout), layout.panelRight - 1, animatedLayout.panelBottom - 8);
        drawStatCards(context, animatedLayout);
        drawGraphCard(context, animatedLayout, mouseX, mouseY, animation);
        drawGoalCard(context, animatedLayout);
        drawEfficiency(context, animatedLayout);
        drawBreakdownCard(context, animatedLayout, mouseX, mouseY);
        drawSearchFieldShell(context);
        context.disableScissor();

        super.render(context, mouseX, mouseY, delta);

        if (this.searchField != null && this.searchField.visible && this.searchField.getText().isBlank() && this.searchField.isFocused() == false)
        {
            String placeholder = MmmUi.truncate(this.textRenderer, SEARCH_PLACEHOLDER, this.searchField.getWidth() - 12);
            int placeholderX = this.searchField.getX() + 6;
            context.drawText(this.textRenderer, Text.literal(placeholder), placeholderX, this.searchField.getY() + 6, COLOR_MUTED, false);
        }

        if (this.clipboardMessageVisible)
        {
            context.drawText(this.textRenderer, Text.literal("Summary copied to clipboard."), animatedLayout.breakdownX, animatedLayout.panelBottom - 14, COLOR_SUCCESS, false);
        }
        if (this.showChrome)
        {
            MmmUi.drawMmmTopBar(context, this.textRenderer, this.width);
        }
        drawContentScrollbar(context, animatedLayout);
        drawEfficiencyTooltip(context, animatedLayout, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount)
    {
        Layout layout = computeLayout();
        if (!isInsideContent(layout, mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, verticalAmount);
        if (isMouseInsideBreakdown(mouseX, mouseY) && this.contentScroll == this.maxContentScroll
                && (verticalAmount < 0 && this.breakdownScrollOffset < getMaxBreakdownScroll()
                    || verticalAmount > 0 && this.breakdownScrollOffset > 0))
        {
            setBreakdownScrollOffset(this.breakdownScrollOffset + (verticalAmount < 0 ? BREAKDOWN_ROW_HEIGHT : -BREAKDOWN_ROW_HEIGHT));
            return true;
        }
        this.contentScroll = MathHelper.clamp(this.contentScroll - (int) (verticalAmount * 30), 0, this.maxContentScroll);
        updateDynamicWidgetBounds(computeLayout());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (this.showChrome && button == 0 && MmmUi.handleMmmScreensSidebarClick(this, this.parent, mouseX, mouseY, "SUMMARY"))
        {
            return true;
        }

        Layout layout = computeLayout();
        if (button == 0 && this.maxContentScroll > 0 && isInsideContent(layout, mouseX, mouseY)
                && mouseX >= layout.panelRight - 8)
        {
            this.draggingContentScrollbar = true;
            updateContentScrollFromMouse(mouseY);
            return true;
        }

        if (button == 0 && isInsideContent(computeLayout(), mouseX, mouseY) && isOverScrollbar(mouseX, mouseY))
        {
            this.draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        if (this.draggingContentScrollbar)
        {
            updateContentScrollFromMouse(mouseY);
            return true;
        }
        if (this.draggingScrollbar)
        {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (button == 0)
        {
            this.draggingScrollbar = false;
            this.draggingContentScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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

    private void ensureCursorVisible()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.mouse != null)
        {
            client.mouse.unlockCursor();
        }
    }

    private void drawHeader(DrawContext context, Layout layout)
    {
        int titleWidth = layout.compact ? Math.max(0, layout.contentWidth - 156) : layout.contentWidth;
        MmmUi.drawTextWithin(context, this.textRenderer, this.heading, layout.contentX, layout.headerY, titleWidth, COLOR_VALUE, true);
        if (!layout.compact)
        {
            drawPill(context, layout.contentX, layout.headerY + 18, Math.min(200, layout.graphWidth - 20), 16, this.worldName, COLOR_CARD, MmmUi.accent());
            MmmUi.drawTextWithin(context, this.textRenderer, "Session pace, goals, and block mix in the MMM website style.", layout.contentX + 2, layout.headerY + 40, layout.contentWidth - 4, COLOR_LABEL, false);
        }
    }

    private void drawStatCards(DrawContext context, Layout layout)
    {
        int cardY = layout.statY;
        int columns = layout.contentWidth < 480 ? 2 : 5;
        int cardWidth = (layout.contentWidth - CARD_GAP * (columns - 1)) / columns;
        int cardHeight = layout.compact ? 40 : 54;
        String[] labels = {"Total Mined", "Session IGT", "Session Time", "Avg Rate", "Best Hour"};
        String[] values = {UiFormat.formatCompact(this.session.totalBlocks), formatClock(this.session.getActiveDurationMs()),
                formatClock(this.session.getWallDurationMs()), UiFormat.formatCompact(this.session.getAverageBlocksPerHour()),
                UiFormat.formatCompact(this.session.getBestHourBlocks())};
        String[] units = {"blocks", "pauses excluded", "pauses included", "blocks/hr", "blocks mined"};
        for (int i = 0; i < labels.length; i++)
            drawStatCard(context, layout.contentX + i % columns * (cardWidth + CARD_GAP),
                    cardY + i / columns * (cardHeight + CARD_GAP), cardWidth, cardHeight, labels[i], values[i], units[i]);
    }

    private void drawGraphCard(DrawContext context, Layout layout, int mouseX, int mouseY, float animation)
    {
        if (layout.compact)
        {
            return;
        }
        fillRoundedCard(context, layout.graphX, layout.graphY, layout.graphWidth, layout.graphHeight, COLOR_CARD, COLOR_BORDER);
        MmmUi.drawTextWithin(context, this.textRenderer, "Mining Rate", layout.graphX + CARD_PADDING, layout.graphY + 10, layout.graphWidth - CARD_PADDING * 2, COLOR_VALUE, false);
        MmmUi.drawTextWithin(context, this.textRenderer, "Blocks per hour over active session time", layout.graphX + CARD_PADDING, layout.graphY + 24, layout.graphWidth - CARD_PADDING * 2, COLOR_LABEL, false);

        int chartX = layout.graphX + CARD_PADDING;
        int chartY = layout.graphY + 42;
        int chartWidth = layout.graphWidth - CARD_PADDING * 2;
        int chartHeight = layout.graphHeight - 56;
        drawMiningRateGraph(context, chartX, chartY, chartWidth, chartHeight, animation);
    }

    private void drawGoalCard(DrawContext context, Layout layout)
    {
        if (layout.compact)
        {
            return;
        }
        fillRoundedCard(context, layout.goalX, layout.goalY, layout.goalWidth, layout.goalHeight, COLOR_CARD, COLOR_BORDER);
        MmmUi.drawTextWithin(context, this.textRenderer, "Daily Goal", layout.goalX + CARD_PADDING, layout.goalY + 10, layout.goalWidth - CARD_PADDING * 2, COLOR_VALUE, false);

        MiningStats.GoalProgress progress = MiningStats.getDailyGoalProgress();
        if (progress.enabled() == false)
        {
            context.drawText(this.textRenderer, Text.literal("Disabled"), layout.goalX + CARD_PADDING, layout.goalY + 32, COLOR_MUTED, false);
            return;
        }

        int goalColor = UiFormat.getGoalColor(progress);
        int barX = layout.goalX + CARD_PADDING;
        int barY = layout.goalY + 42;
        int barWidth = layout.goalWidth - CARD_PADDING * 2;
        String percentText = UiFormat.formatGoalPercent(progress);
        int percentWidth = this.textRenderer.getWidth(percentText);
        MmmUi.drawTextWithin(context, this.textRenderer, UiFormat.formatProgress(progress.current(), progress.target()), barX, layout.goalY + 27, Math.max(0, barWidth - percentWidth - 8), COLOR_VALUE, false);
        MmmUi.drawTextRightWithin(context, this.textRenderer, percentText, barX + barWidth, layout.goalY + 27, percentWidth, goalColor, false);

        int fillWidth = progress.target() <= 0 ? 0 : (int) Math.round(barWidth * Math.min(1.0D, progress.current() / (double) progress.target()));
        context.fill(barX, barY, barX + barWidth, barY + 8, MmmUi.INSET);
        context.fill(barX, barY, barX + fillWidth, barY + 8, goalColor);
        context.drawBorder(barX, barY, barWidth, 8, COLOR_BORDER);

        String etaText = "ETA: " + MiningStats.getEstimatedTimeToDailyGoal();
        String streakText = "Best Streak: " + this.session.bestStreakSeconds + "s";
        MmmUi.drawTextWithin(context, this.textRenderer, etaText, barX, barY + 16, barWidth, COLOR_LABEL, false);
        MmmUi.drawTextWithin(context, this.textRenderer, streakText, barX, barY + 30, barWidth, COLOR_MUTED, false);
    }

    private void drawBreakdownCard(DrawContext context, Layout layout, int mouseX, int mouseY)
    {
        fillRoundedCard(context, layout.breakdownX, layout.breakdownY, layout.breakdownWidth, layout.breakdownHeight, COLOR_CARD_SOFT, COLOR_BORDER);
        MmmUi.drawTextWithin(context, this.textRenderer, "Block Breakdown", layout.breakdownX + CARD_PADDING, layout.breakdownY + 10, layout.breakdownWidth - CARD_PADDING * 2, COLOR_VALUE, false);

        int listX = layout.breakdownX + CARD_PADDING;
        int listY = layout.breakdownY + 60;
        int listWidth = layout.breakdownWidth - CARD_PADDING * 2;
        int listHeight = layout.breakdownHeight - 72;
        int viewportWidth = listWidth - SCROLLBAR_WIDTH - 6;

        context.fill(listX, listY, listX + listWidth, listY + listHeight, MmmUi.INSET);
        context.drawBorder(listX, listY, listWidth, listHeight, COLOR_BORDER_SOFT);

        context.enableScissor(listX, listY, listX + viewportWidth, listY + listHeight);
        int drawY = listY + 6 - this.breakdownScrollOffset;
        if (this.filteredEntries.isEmpty())
        {
            context.drawText(this.textRenderer, Text.literal("No matching blocks found."), listX + 8, drawY + 4, COLOR_MUTED, false);
        }
        else
        {
            for (BlockBreakdownEntry entry : this.filteredEntries)
            {
                if (drawY + BREAKDOWN_ROW_HEIGHT >= listY && drawY <= listY + listHeight)
                {
                    drawBreakdownRow(context, listX + 6, drawY, viewportWidth - 12, entry);
                }
                drawY += BREAKDOWN_ROW_HEIGHT;
            }
        }
        context.disableScissor();
        drawScrollbar(context, listX + listWidth - SCROLLBAR_WIDTH, listY, listHeight, mouseX, mouseY);
    }

    private void drawBreakdownRow(DrawContext context, int x, int y, int width, BlockBreakdownEntry entry)
    {
        int rowColor = ((y / BREAKDOWN_ROW_HEIGHT) & 1) == 0 ? MmmUi.ROW_ALT : MmmUi.INSET;
        context.fill(x - 2, y - 1, x + width, y + BREAKDOWN_ROW_HEIGHT - 2, rowColor);
        context.drawItem(entry.icon(), x, y + 1);

        String countText = UiFormat.formatCompact(entry.count());
        int countWidth = this.textRenderer.getWidth(countText);
        int countX = x + width - countWidth - 4;
        int nameWidth = Math.max(40, countX - (x + 24) - 8);
        String blockName = truncateToWidth(this.textRenderer, entry.name(), nameWidth);

        context.drawText(this.textRenderer, Text.literal(blockName), x + 22, y + 6, COLOR_VALUE, false);
        context.drawText(this.textRenderer, Text.literal(countText), countX, y + 6, Configs.getHudNumberColor(), false);
    }

    private void drawMiningRateGraph(DrawContext context, int x, int y, int width, int height, float animation)
    {
        List<Double> rates = buildGraphRates();
        context.fill(x, y, x + width, y + height, MmmUi.INSET);

        for (int i = 0; i < 4; i++)
        {
            int lineY = y + (height * i) / 4;
            context.fill(x, lineY, x + width, lineY + 1, MmmUi.graphGrid());
        }

        if (rates.isEmpty())
        {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Mine blocks to build a pace graph"), x + width / 2, y + height / 2 - 4, COLOR_MUTED);
            return;
        }

        int columns = Math.max(1, Math.min(width / 4, rates.size()));
        double maxRate = Math.max(60.0D, this.session.getPeakBlocksPerHour());
        double[] columnRates = new double[columns];
        for (int column = 0; column < columns; column++)
        {
            int startIndex = (int) Math.floor((column * rates.size()) / (double) columns);
            int endIndex = (int) Math.floor(((column + 1) * rates.size()) / (double) columns);
            if (endIndex <= startIndex)
            {
                endIndex = Math.min(rates.size(), startIndex + 1);
            }

            double total = 0.0D;
            int count = 0;
            for (int i = startIndex; i < endIndex; i++)
            {
                total += rates.get(i);
                count++;
            }

            double rate = count <= 0 ? 0.0D : total / count;
            columnRates[column] = rate;
            maxRate = Math.max(maxRate, rate);
        }

        int chartBottom = y + height - 14;
        int usableHeight = Math.max(16, height - 22);
        int revealColumns = Math.max(1, (int) Math.ceil(columns * animation));
        float[] pointsY = new float[columns];
        int[] pointsX = new int[columns];
        for (int column = 0; column < columns; column++)
        {
            float normalized = (float) (columnRates[column] / maxRate);
            pointsY[column] = chartBottom - normalized * usableHeight;
            pointsX[column] = x + Math.round((column / (float) Math.max(1, columns - 1)) * (width - 1));
        }

        int fillBaseY = chartBottom;
        for (int column = 0; column < revealColumns; column++)
        {
            int pointX = pointsX[column];
            int pointY = Math.round(pointsY[column]);
            context.fill(pointX, pointY, pointX + 2, fillBaseY, MmmUi.accentSoft());
        }

        for (int column = 0; column < revealColumns - 1; column++)
        {
            float previousY = column > 0 ? pointsY[column - 1] : pointsY[column];
            float nextY = column + 2 < columns ? pointsY[column + 2] : pointsY[column + 1];
            drawCurveSegment(context, pointsX[column], pointsY[column], pointsX[column + 1], pointsY[column + 1], previousY, nextY);
        }

        int lastVisibleIndex = revealColumns - 1;
        if (lastVisibleIndex >= 0)
        {
            int markerX = pointsX[lastVisibleIndex];
            int markerY = Math.round(pointsY[lastVisibleIndex]);
            context.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, COLOR_VALUE);
            context.fill(markerX - 1, markerY - 1, markerX + 2, markerY + 2, MmmUi.accent());
        }

        String maxLabel = UiFormat.formatCompact(Math.round(maxRate)) + "/hr";
        context.drawText(this.textRenderer, Text.literal(maxLabel), x + 4, y + 4, COLOR_LABEL, false);
        context.drawText(this.textRenderer, Text.literal("0"), x + 4, chartBottom - 8, COLOR_MUTED, false);

        String startLabel = "0m";
        String endLabel = formatGraphTimeLabel(Math.max(1, rates.size()) * 60L);
        int endWidth = this.textRenderer.getWidth(endLabel);
        context.drawText(this.textRenderer, Text.literal(startLabel), x, y + height - 10, COLOR_MUTED, false);
        context.drawText(this.textRenderer, Text.literal(endLabel), x + width - endWidth, y + height - 10, COLOR_MUTED, false);
    }

    private void drawStatCard(DrawContext context, int x, int y, int width, int height, String label, String value, String suffix)
    {
        fillRoundedCard(context, x, y, width, height, COLOR_CARD_SOFT, COLOR_BORDER_SOFT);
        int textWidth = width - CARD_PADDING * 2;
        MmmUi.drawTextWithin(context, this.textRenderer, label, x + CARD_PADDING, y + 9, textWidth, COLOR_LABEL, false);
        int valueColor = inactiveValueColor(value) == COLOR_INACTIVE ? COLOR_INACTIVE : Configs.getHudNumberColor();
        MmmUi.drawTextWithin(context, this.textRenderer, value, x + CARD_PADDING, y + 24, textWidth, valueColor, false);
        if (height >= 50 && suffix.isBlank() == false)
        {
            MmmUi.drawTextWithin(context, this.textRenderer, suffix, x + CARD_PADDING, y + 38, textWidth, COLOR_MUTED, false);
        }
    }

    private void drawSearchFieldShell(DrawContext context)
    {
        if (this.searchField == null)
        {
            return;
        }

        int x = this.searchField.getX() - 1;
        int y = this.searchField.getY() - 1;
        int width = this.searchField.getWidth() + 2;
        int height = SEARCH_HEIGHT;
        fillRoundedCard(context, x, y, width, height, COLOR_INSET, this.searchField.isFocused() ? MmmUi.accent() : COLOR_BORDER_SOFT);
    }

    private void drawScrollbar(DrawContext context, int x, int y, int height, int mouseX, int mouseY)
    {
        int maxScroll = getMaxBreakdownScroll();
        if (maxScroll <= 0)
        {
            return;
        }

        int thumbHeight = getScrollbarThumbHeight(height);
        int thumbY = y + getScrollbarThumbOffset(height, thumbHeight);
        context.fill(x, y, x + SCROLLBAR_WIDTH, y + height, MmmUi.SCROLLBAR_TRACK);
        context.drawBorder(x, y, SCROLLBAR_WIDTH, height, COLOR_BORDER_SOFT);
        int thumbColor = MmmUi.scrollbarThumb();
        context.fill(x + 1, thumbY, x + SCROLLBAR_WIDTH - 1, thumbY + thumbHeight, thumbColor);
    }

    private void drawPill(DrawContext context, int x, int y, int width, int height, String text, int fillColor, int borderColor)
    {
        fillRoundedCard(context, x, y, width, height, fillColor, borderColor);
        String clipped = MmmUi.truncate(this.textRenderer, text, width - 8);
        int textX = x + Math.max(4, (width - this.textRenderer.getWidth(clipped)) / 2);
        context.drawText(this.textRenderer, Text.literal(clipped), textX, y + 4, MmmUi.accent(), false);
    }

    private void fillRoundedCard(DrawContext context, int x, int y, int width, int height, int fillColor, int borderColor)
    {
        MmmUi.card(context, x, y, width, height, fillColor, borderColor);
    }

    private void buildBreakdownEntries()
    {
        this.allEntries.clear();
        for (Map.Entry<String, Long> entry : MiningStats.getSortedBreakdown(this.session).entrySet())
        {
            String blockId = entry.getKey();
            this.allEntries.add(new BlockBreakdownEntry(blockId, getCachedBlockName(blockId), entry.getValue(), getCachedIcon(blockId)));
        }
        this.allEntries.sort(Comparator.comparingLong(BlockBreakdownEntry::count).reversed().thenComparing(BlockBreakdownEntry::name));
    }

    private void refreshFilteredEntries()
    {
        this.filteredEntries.clear();
        String query = this.searchField == null ? "" : this.searchField.getText().trim().toLowerCase(Locale.ROOT);
        for (BlockBreakdownEntry entry : this.allEntries)
        {
            if (query.isBlank() || entry.searchName().contains(query))
            {
                this.filteredEntries.add(entry);
            }
        }
        setBreakdownScrollOffset(0);
    }

    private void updateDynamicWidgetBounds(Layout layout)
    {
        if (this.searchField != null)
        {
            this.searchField.setX(layout.breakdownX + CARD_PADDING);
            this.searchField.setY(layout.breakdownY + 32);
            this.searchField.setWidth(layout.breakdownWidth - CARD_PADDING * 2);
            this.searchField.visible = this.searchField.getY() >= contentTop(layout)
                    && this.searchField.getY() + SEARCH_HEIGHT <= layout.panelBottom - 8;
            if (!this.searchField.visible) this.searchField.setFocused(false);
        }
    }

    private void loadEfficiency()
    {
        if (this.efficiencyRequested || this.session.efficiency.tracked() > 0
                || this.session == MiningStats.getCurrentSession()) return;
        this.efficiencyRequested = true;
        this.efficiencyLoading = true;
        var game = this.client;
        EfficiencyStore.load(this.session).whenComplete((data, failure) -> game.execute(() -> {
            this.efficiencyLoading = false;
            if (failure == null) this.session.efficiency = data;
            else this.efficiencyError = "Saved activity data unavailable";
        }));
    }

    private static int efficiencyHeight(int width)
    {
        return 42 + (width >= 480 ? 4 : 7) * 34;
    }

    private void drawEfficiency(DrawContext context, Layout layout)
    {
        int height = efficiencyHeight(layout.contentWidth);
        int top = layout.breakdownY - CARD_GAP - height;
        int x = layout.contentX + CARD_PADDING;
        int width = layout.contentWidth - CARD_PADDING * 2;
        fillRoundedCard(context, layout.contentX, top, layout.contentWidth, height, COLOR_CARD, COLOR_BORDER);
        MmmUi.drawTextWithin(context, this.textRenderer, "Mining Efficiency", x, top + 10, width, COLOR_VALUE, false);
        long duration = this.session.getActiveDurationMs();
        String status = this.efficiencyLoading ? "Loading activity..." : this.efficiencyError != null ? this.efficiencyError
                : this.session.efficiency.tracked() == 0 ? "No activity recorded; time is untracked"
                : "Session IGT breakdown / pauses excluded";
        MmmUi.drawTextWithin(context, this.textRenderer, status, x, top + 23, width, COLOR_LABEL, false);
        int columns = layout.contentWidth >= 480 ? 2 : 1;
        int cellWidth = (width - (columns - 1) * 20) / columns;
        var activities = SessionEfficiency.Activity.values();
        for (int i = 0; i <= activities.length; i++)
        {
            var activity = i < activities.length ? activities[i] : null;
            long time = activity == null ? this.session.efficiency.untracked(duration) : this.session.efficiency.time(activity);
            int cellX = x + (i % columns) * (cellWidth + 20);
            int y = top + 40 + (i / columns) * 34;
            MmmUi.drawTextWithin(context, this.textRenderer, activity == null ? "Untracked time" : activity.label,
                    cellX, y, cellWidth, COLOR_VALUE, false);
            String value = formatClock(time) + " / " + String.format(Locale.ROOT, "%.1f%%", duration <= 0 ? 0 : Math.min(100, time * 100.0 / duration));
            MmmUi.drawTextWithin(context, this.textRenderer, value, cellX, y + 11, cellWidth, COLOR_LABEL, false);
            context.fill(cellX, y + 24, cellX + cellWidth, y + 27, COLOR_INSET);
            int fill = duration <= 0 ? 0 : (int) (cellWidth * Math.min(1, time / (double) duration));
            context.fill(cellX, y + 24, cellX + fill, y + 27, activity == null ? COLOR_INACTIVE : activity.color);
        }
    }

    private static int contentTop(Layout layout)
    {
        return layout.headerY + (layout.compact ? 24 : 58);
    }

    private static boolean isInsideContent(Layout layout, double x, double y)
    {
        return x >= layout.contentX && x < layout.panelRight && y >= contentTop(layout) && y < layout.panelBottom - 8;
    }

    private void drawContentScrollbar(DrawContext context, Layout layout)
    {
        if (this.maxContentScroll == 0) return;
        int top = contentTop(layout);
        int height = layout.panelBottom - 8 - top;
        int thumb = Math.max(16, height * height / (height + this.maxContentScroll));
        int y = top + (height - thumb) * this.contentScroll / this.maxContentScroll;
        context.fill(layout.panelRight - 5, top, layout.panelRight - 3, top + height, COLOR_BORDER);
        context.fill(layout.panelRight - 5, y, layout.panelRight - 3, y + thumb, MmmUi.accent());
    }

    private void updateContentScrollFromMouse(double mouseY)
    {
        Layout layout = computeLayout();
        int top = contentTop(layout);
        int height = layout.panelBottom - 8 - top;
        int thumb = Math.max(16, height * height / (height + this.maxContentScroll));
        double progress = (mouseY - top - thumb / 2.0) / Math.max(1, height - thumb);
        this.contentScroll = (int) Math.round(MathHelper.clamp(progress, 0, 1) * this.maxContentScroll);
        updateDynamicWidgetBounds(computeLayout());
    }

    private void drawEfficiencyTooltip(DrawContext context, Layout layout, int mouseX, int mouseY)
    {
        if (!isInsideContent(layout, mouseX, mouseY)) return;
        int top = layout.breakdownY - CARD_GAP - efficiencyHeight(layout.contentWidth) + 40;
        int x = layout.contentX + CARD_PADDING;
        int columns = layout.contentWidth >= 480 ? 2 : 1;
        int cellWidth = (layout.contentWidth - CARD_PADDING * 2 - (columns - 1) * 20) / columns;
        int row = (mouseY - top) / 34;
        int column = (mouseX - x) / (cellWidth + 20);
        int index = row * columns + column;
        if (mouseY < top || mouseX < x || column >= columns || index < 0 || index >= 7
                || mouseX - x - column * (cellWidth + 20) >= cellWidth) return;
        String[] notes = {
                "Mining without an observed multi-tick break, including a short interval after an accepted harvest.",
                "Breaking a block that needs more than one tick with your current tool and effects.",
                "Movement without mining. This can include falling or knockback.",
                "Inventory or container open during unpaused Session IGT.",
                "Other menu or chat open during unpaused Session IGT.",
                "No mining, movement or menu activity observed. This does not necessarily mean AFK.",
                "Older session time and gaps longer than 250ms cannot be classified reliably. Pauses are excluded."
        };
        context.drawOrderedTooltip(this.textRenderer, this.textRenderer.wrapLines(Text.literal(notes[index]), Math.min(260, layout.contentWidth)), mouseX, mouseY);
    }

    private void drawCurveSegment(DrawContext context, float startX, float startY, float endX, float endY, float previousY, float nextY)
    {
        int steps = Math.max(6, Math.round(Math.abs(endX - startX) * 1.5F));
        for (int step = 0; step <= steps; step++)
        {
            float t = step / (float) steps;
            float pointX = MathHelper.lerp(t, startX, endX);
            float tangentStart = (endY - previousY) * 0.5F;
            float tangentEnd = (nextY - startY) * 0.5F;
            float t2 = t * t;
            float t3 = t2 * t;
            float pointY =
                    (2.0F * t3 - 3.0F * t2 + 1.0F) * startY +
                    (t3 - 2.0F * t2 + t) * tangentStart +
                    (-2.0F * t3 + 3.0F * t2) * endY +
                    (t3 - t2) * tangentEnd;
            int ix = Math.round(pointX);
            int iy = Math.round(pointY);
            context.fill(ix, iy, ix + 2, iy + 2, MmmUi.accent());
            context.fill(ix, iy + 2, ix + 2, iy + 3, MmmUi.graphFill());
        }
    }

    private List<Double> buildGraphRates()
    {
        List<Double> rates = new ArrayList<>();
        for (int bucket : this.session.miningRateBuckets)
        {
            rates.add((double) this.session.getBucketBlocksPerHour(bucket));
        }
        return rates;
    }

    private float getOpenAnimationProgress()
    {
        if (this.openedAtMs <= 0L)
        {
            return 1.0F;
        }

        long elapsed = System.currentTimeMillis() - this.openedAtMs;
        float normalized = MathHelper.clamp(elapsed / 280.0F, 0.0F, 1.0F);
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private Layout computeLayout()
    {
        boolean compact = this.height < 400 || (this.showChrome && MmmUi.contentWidth(this.width) < 540);
        int availableWidth = this.showChrome ? Math.max(1, MmmUi.contentWidth(this.width) - PANEL_MARGIN) : Math.max(1, this.width - PANEL_MARGIN * 2);
        int panelWidth = Math.min(760, availableWidth);
        int topY = this.showChrome ? MmmUi.TOP_BAR_HEIGHT + 10 : PANEL_MARGIN;
        int availableHeight = Math.max(1, this.height - topY - (this.showChrome ? 12 : PANEL_MARGIN));
        int panelHeight = Math.min(720, availableHeight);
        int panelX = this.showChrome ? MmmUi.centerContentX(this.width, panelWidth) : Math.max(PANEL_MARGIN, (this.width - panelWidth) / 2);
        int panelY = topY + Math.max(0, (availableHeight - panelHeight) / 2);
        int padding = compact ? 8 : PANEL_PADDING;
        int contentX = panelX + padding;
        int contentWidth = panelWidth - padding * 2;
        int headerY = panelY + padding;
        int statY = headerY + (compact ? 24 : 58);
        int cardWidth = (contentWidth - CARD_GAP * 3) / 4;
        int graphY = statY + (contentWidth < 480 ? 3 : 1) * ((compact ? 40 : 54) + CARD_GAP);
        int goalWidth = Math.min(190, Math.max(84, contentWidth / 4));
        int graphWidth = contentWidth - goalWidth - CARD_GAP;
        int graphHeight = compact ? 0 : 140;
        int efficiencyY = compact ? graphY : graphY + graphHeight + CARD_GAP;
        int breakdownY = efficiencyY + efficiencyHeight(contentWidth) + CARD_GAP;
        int breakdownHeight = Math.max(152, panelY + panelHeight - padding - breakdownY);
        this.maxContentScroll = Math.max(0, breakdownY + breakdownHeight - (panelY + panelHeight - 8));
        this.contentScroll = MathHelper.clamp(this.contentScroll, 0, this.maxContentScroll);
        statY -= this.contentScroll;
        graphY -= this.contentScroll;
        breakdownY -= this.contentScroll;

        return new Layout(
                panelX,
                panelY,
                panelWidth,
                panelHeight,
                panelX + panelWidth,
                panelY + panelHeight,
                contentX,
                contentWidth,
                headerY,
                statY,
                cardWidth,
                contentX,
                graphY,
                graphWidth,
                graphHeight,
                contentX + graphWidth + CARD_GAP,
                graphY,
                goalWidth,
                graphHeight,
                contentX,
                breakdownY,
                contentWidth,
                breakdownHeight,
                compact);
    }

    private boolean isMouseInsideBreakdown(double mouseX, double mouseY)
    {
        Layout layout = computeLayout();
        int listX = layout.breakdownX + CARD_PADDING;
        int listY = layout.breakdownY + 60;
        int listWidth = layout.breakdownWidth - CARD_PADDING * 2;
        int listHeight = layout.breakdownHeight - 72;
        return mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY)
    {
        int[] metrics = getScrollbarMetrics();
        return mouseX >= metrics[0] && mouseX <= metrics[0] + SCROLLBAR_WIDTH && mouseY >= metrics[1] && mouseY <= metrics[1] + metrics[2];
    }

    private void updateScrollFromMouse(double mouseY)
    {
        int[] metrics = getScrollbarMetrics();
        int trackY = metrics[1];
        int trackHeight = metrics[2];
        int thumbHeight = getScrollbarThumbHeight(trackHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        double thumbTop = mouseY - thumbHeight / 2.0D;
        double normalized = Math.max(0.0D, Math.min(1.0D, (thumbTop - trackY) / travel));
        setBreakdownScrollOffset((int) Math.round(normalized * getMaxBreakdownScroll()));
    }

    private void setBreakdownScrollOffset(int offset)
    {
        this.breakdownScrollOffset = Math.max(0, Math.min(getMaxBreakdownScroll(), offset));
    }

    private int getMaxBreakdownScroll()
    {
        int totalContentHeight = this.filteredEntries.size() * BREAKDOWN_ROW_HEIGHT + 12;
        return Math.max(0, totalContentHeight - getBreakdownListHeight());
    }

    private int getBreakdownListHeight()
    {
        Layout layout = computeLayout();
        return layout.breakdownHeight - 72;
    }

    private int[] getScrollbarMetrics()
    {
        Layout layout = computeLayout();
        int listX = layout.breakdownX + CARD_PADDING;
        int listY = layout.breakdownY + 60;
        int listWidth = layout.breakdownWidth - CARD_PADDING * 2;
        return new int[] { listX + listWidth - SCROLLBAR_WIDTH, listY, getBreakdownListHeight() };
    }

    private int getScrollbarThumbHeight(int trackHeight)
    {
        int totalContentHeight = this.filteredEntries.size() * BREAKDOWN_ROW_HEIGHT + 12;
        if (totalContentHeight <= 0)
        {
            return trackHeight;
        }

        int thumbHeight = (int) Math.round((getBreakdownListHeight() / (double) totalContentHeight) * trackHeight);
        return Math.max(SCROLLBAR_MIN_THUMB, Math.min(trackHeight, thumbHeight));
    }

    private int getScrollbarThumbOffset(int trackHeight, int thumbHeight)
    {
        int maxScroll = getMaxBreakdownScroll();
        if (maxScroll <= 0)
        {
            return 0;
        }
        return (int) Math.round((this.breakdownScrollOffset / (double) maxScroll) * (trackHeight - thumbHeight));
    }

    private String buildShareText()
    {
        StringBuilder builder = new StringBuilder();
        builder.append(this.heading).append('\n');
        builder.append("World/Server: ").append(this.worldName).append('\n');
        builder.append("Total Mined: ").append(UiFormat.formatBlocks(this.session.totalBlocks)).append('\n');
        builder.append("Session IGT: ").append(formatClock(this.session.getActiveDurationMs())).append('\n');
        builder.append("Session Time: ").append(formatClock(this.session.getWallDurationMs())).append('\n');
        builder.append("Average Rate: ").append(UiFormat.formatBlocksPerHour(this.session.getAverageBlocksPerHour())).append('\n');
        builder.append("Best Hour: ").append(UiFormat.formatBlocks(this.session.getBestHourBlocks())).append(" blocks\n");
        builder.append("Best Streak: ").append(this.session.bestStreakSeconds).append("s\n");

        MiningStats.GoalProgress dailyGoal = MiningStats.getDailyGoalProgress();
        if (dailyGoal.enabled())
        {
            builder.append(dailyGoal.label()).append(": ").append(UiFormat.formatProgress(dailyGoal.current(), dailyGoal.target())).append(" (").append(UiFormat.formatGoalPercent(dailyGoal)).append(")\n");
        }

        for (BlockBreakdownEntry entry : this.allEntries)
        {
            builder.append(entry.name()).append(": ").append(entry.count()).append('\n');
        }
        return builder.toString().trim();
    }

    private String formatClock(long durationMs)
    {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static int inactiveValueColor(String value)
    {
        String text = value == null ? "" : value.trim();
        return "--".equals(text) || "Paused".equals(text) || "00:00:00".equals(text) ? COLOR_INACTIVE : COLOR_VALUE;
    }

    private String formatGraphTimeLabel(long totalSeconds)
    {
        long minutes = totalSeconds / 60L;
        if (minutes < 60L)
        {
            return minutes + "m";
        }

        long hours = minutes / 60L;
        long remainingMinutes = minutes % 60L;
        if (remainingMinutes == 0L)
        {
            return hours + "h";
        }

        return hours + "h " + remainingMinutes + "m";
    }

    private String truncateToWidth(TextRenderer renderer, String value, int maxWidth)
    {
        if (renderer.getWidth(value) <= maxWidth)
        {
            return value;
        }

        String ellipsis = "...";
        String trimmed = value;
        while (trimmed.length() > 1 && renderer.getWidth(trimmed + ellipsis) > maxWidth)
        {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed + ellipsis;
    }

    private String getCachedBlockName(String id)
    {
        return NAME_CACHE.computeIfAbsent(id, key -> resolveBlock(key).getName().getString());
    }

    private ItemStack getCachedIcon(String id)
    {
        ItemStack cached = ICON_CACHE.get(id);
        if (cached != null)
        {
            return cached.copy();
        }

        Block block = resolveBlock(id);
        Item item = block.asItem();
        ItemStack stack = item == Items.AIR ? new ItemStack(Blocks.STONE) : new ItemStack(item);
        ICON_CACHE.put(id, stack.copy());
        return stack;
    }

    private Block resolveBlock(String id)
    {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null)
        {
            return Blocks.STONE;
        }

        Block block = Registries.BLOCK.get(identifier);
        return block == null ? Blocks.STONE : block;
    }

    private static String resolveWorldName()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        WorldSessionContext.update(client);
        return WorldSessionContext.getCurrentWorldName();
    }

    private record BlockBreakdownEntry(String id, String name, long count, ItemStack icon)
    {
        private String searchName()
        {
            return this.name.toLowerCase(Locale.ROOT);
        }
    }

    private record Layout(
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int panelRight,
            int panelBottom,
            int contentX,
            int contentWidth,
            int headerY,
            int statY,
            int statCardWidth,
            int graphX,
            int graphY,
            int graphWidth,
            int graphHeight,
            int goalX,
            int goalY,
            int goalWidth,
            int goalHeight,
            int breakdownX,
            int breakdownY,
            int breakdownWidth,
            int breakdownHeight,
            boolean compact)
    {
        private Layout withPanelY(int newPanelY)
        {
            int delta = newPanelY - this.panelY;
            return new Layout(
                    this.panelX,
                    newPanelY,
                    this.panelWidth,
                    this.panelHeight,
                    this.panelRight,
                    this.panelBottom + delta,
                    this.contentX,
                    this.contentWidth,
                    this.headerY + delta,
                    this.statY + delta,
                    this.statCardWidth,
                    this.graphX,
                    this.graphY + delta,
                    this.graphWidth,
                    this.graphHeight,
                    this.goalX,
                    this.goalY + delta,
                    this.goalWidth,
                    this.goalHeight,
                    this.breakdownX,
                    this.breakdownY + delta,
                    this.breakdownWidth,
                    this.breakdownHeight,
                    this.compact);
        }
    }
}
