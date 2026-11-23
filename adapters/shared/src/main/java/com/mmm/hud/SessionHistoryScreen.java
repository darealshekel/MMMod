package com.mmm.hud;

import com.mmm.ui.BoardEditorStyle;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.mmm.config.Configs;
import com.mmm.storage.SessionData;
import com.mmm.storage.SessionHistory;
import com.mmm.storage.WorldSessionContext;
import com.mmm.ui.MmmUi;
import com.mmm.util.UiFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class SessionHistoryScreen extends Screen
{
    private static final int C = 12;
    private static final int G = 10;
    private static final int RH = 34;
    private static final int SBW = 8;
    private static final int SBM = 18;
    private static final int PACE_CARD_HEIGHT = 132;
    private static final int PACE_CHART_HEIGHT = 88;
    private static final int PACE_AXIS_LEFT_MIN = 30;
    private static final int PACE_AXIS_LEFT_MAX = 42;
    private static final int PACE_AXIS_TOP = 4;
    private static final int PACE_AXIS_RIGHT = 4;
    private static final int PACE_AXIS_BOTTOM = 13;
    private static final int BREAKDOWN_HEIGHT = 108;
    private static final int PANEL = MmmUi.PANEL;
    private static final int CARD = MmmUi.CARD;
    private static final int INSET = MmmUi.INSET;
    private static final int BORDER = MmmUi.BORDER;
    private static final int BORDER_SOFT = MmmUi.BORDER_SOFT;
    private static final int TEXT = MmmUi.TEXT;
    private static final int LABEL = BoardEditorStyle.MUTED;
    private static final int MUTED = BoardEditorStyle.MUTED;
    private static final int INACTIVE = MmmUi.INACTIVE;
    private static final int ROW_ALT = MmmUi.ROW_ALT;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yy  HH:mm");

    private final Screen parent;
    private final List<SessionHistory.WorldHistory> worlds;
    private List<SessionData> sessions = List.of();
    private String worldName = "";
    private int selectedWorldIndex;
    private int selectedIndex = -1;
    private int listScroll;
    private int detailScroll;
    private int breakdownScroll;
    private boolean draggingList;
    private boolean draggingDetail;
    private boolean draggingBreakdown;
    private ButtonWidget previousWorld, nextWorld;
    private int breakdownListX;
    private int breakdownListY;
    private int breakdownListHeight;
    private int breakdownViewportWidth;
    private int breakdownScrollbarX;
    private int breakdownEntryCount;

    public SessionHistoryScreen(Screen parent)
    {
        super(Text.literal("Session History"));
        this.parent = parent;
        this.worlds = SessionHistory.getWorldHistories();
        applyWorldSelection(resolveInitialWorldIndex());
    }

    @Override
    protected void init()
    {
        this.clearChildren();
        ensureCursorVisible();
        Layout l = layout();
        this.addDrawableChild(MmmUi.backButton(this, l.contentX));
        if (this.worlds.size() > 1)
        {
            int right = l.contentX + l.contentWidth;
            this.previousWorld = this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> applyWorldSelection(this.selectedWorldIndex - 1))
                    .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Previous world")))
                    .dimensions(right - 44, l.headerY, 20, 18).build());
            this.nextWorld = this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> applyWorldSelection(this.selectedWorldIndex + 1))
                    .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Next world")))
                    .dimensions(right - 20, l.headerY, 20, 18).build());
            updateWorldButtons();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        ensureCursorVisible();
        Layout l = layout();
        MmmUi.toolBoard(context, this.textRenderer, this.width, this.height, l.contentX, "Tools / History");
        String world = this.worlds.size() > 1 ? (this.selectedWorldIndex + 1) + "/" + this.worlds.size() + "  " + this.worldName : this.worldName;
        MmmUi.drawTextWithin(context, this.textRenderer, world, l.contentX, l.headerY + 5,
                l.contentWidth - (this.worlds.size() > 1 ? 52 : 0), TEXT, false);
        drawList(context, l, mouseX, mouseY);
        drawDetail(context, l, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        if (isInBreakdown(mouseX, mouseY))
        {
            setBreakdownScroll(this.breakdownScroll + (verticalAmount < 0 ? 16 : -16));
            return true;
        }
        if (isInDetail(mouseX, mouseY))
        {
            setDetailScroll(this.detailScroll + (verticalAmount < 0 ? 24 : -24));
            return true;
        }
        if (isInList(mouseX, mouseY))
        {
            setListScroll(this.listScroll + (verticalAmount < 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && isOverBreakdownBar(mouseX, mouseY))
        {
            this.draggingBreakdown = true;
            dragBreakdown(mouseY);
            return true;
        }
        if (button == 0 && isOverDetailBar(mouseX, mouseY))
        {
            this.draggingDetail = true;
            dragDetail(mouseY);
            return true;
        }
        if (button == 0 && isOverListBar(mouseX, mouseY))
        {
            this.draggingList = true;
            dragList(mouseY);
            return true;
        }
        Layout l = layout();
        int listX = l.contentX + C;
        int listY = l.contentY + 44;
        int listWidth = l.leftWidth - C * 2 - SBW - 6;
        int drawY = listY + 6;
        int visibleRows = getVisibleRows(l);
        for (int row = 0; row < visibleRows; row++)
        {
            int index = this.listScroll + row;
            if (index >= this.sessions.size()) break;
            int rowY = drawY + row * RH;
            if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= rowY && mouseY <= rowY + RH - 4)
            {
                select(index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY){ if(draggingBreakdown){dragBreakdown(mouseY); return true;} if(draggingDetail){dragDetail(mouseY); return true;} if(draggingList){dragList(mouseY); return true;} return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY); }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button){ if(button==0){draggingList=false; draggingDetail=false; draggingBreakdown=false;} return super.mouseReleased(mouseX, mouseY, button); }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers){ if(keyCode==256){close(); return true;} if(keyCode==264||keyCode==341){moveSelection(1); return true;} if(keyCode==265||keyCode==328){moveSelection(-1); return true;} return super.keyPressed(keyCode, scanCode, modifiers); }
    @Override public void close(){ MinecraftClient.getInstance().setScreen(this.parent); }
    @Override public boolean shouldPause(){ return MmmUi.shouldPauseGame(); }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta){}

    private void ensureCursorVisible(){ MinecraftClient client=MinecraftClient.getInstance(); if(client!=null&&client.mouse!=null) client.mouse.unlockCursor(); }

    private void updateWorldButtons()
    {
        if (this.previousWorld != null) this.previousWorld.active = this.selectedWorldIndex > 0;
        if (this.nextWorld != null) this.nextWorld.active = this.selectedWorldIndex + 1 < this.worlds.size();
    }

    private int resolveInitialWorldIndex()
    {
        String currentWorldId = SessionHistory.getCurrentWorldId();
        if (currentWorldId == null || currentWorldId.isBlank())
        {
            currentWorldId = WorldSessionContext.getCurrentWorldId();
        }

        for (int i = 0; i < this.worlds.size(); i++)
        {
            if (currentWorldId.equals(this.worlds.get(i).worldId()))
            {
                return i;
            }
        }
        return 0;
    }

    private void applyWorldSelection(int index)
    {
        if (this.worlds.isEmpty())
        {
            this.sessions = newestFirst(SessionHistory.getHistory());
            this.worldName = WorldSessionContext.getCurrentWorldName();
        }
        else
        {
            this.selectedWorldIndex = Math.max(0, Math.min(this.worlds.size() - 1, index));
            SessionHistory.WorldHistory world = this.worlds.get(this.selectedWorldIndex);
            this.sessions = newestFirst(world.sessions());
            this.worldName = world.displayName();
        }

        this.selectedIndex = this.sessions.isEmpty() ? -1 : 0;
        this.listScroll = 0;
        this.detailScroll = 0;
        this.breakdownScroll = 0;
        updateWorldButtons();
    }

    private static List<SessionData> newestFirst(List<SessionData> source)
    {
        List<SessionData> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingLong((SessionData session) -> session.startTimeMs).reversed());
        return sorted;
    }

    private void drawList(DrawContext context, Layout l, int mouseX, int mouseY)
    {
        sectionRule(context, l.contentX, l.contentY, l.leftWidth);
        MmmUi.drawTextWithin(context, this.textRenderer, "Runs", l.contentX + C, l.contentY + 10, l.leftWidth - C * 2, TEXT, false);
        int x = l.contentX + C;
        int y = l.contentY + 44;
        int width = l.leftWidth - C * 2;
        int height = l.contentHeight - 56;
        int viewportWidth = width - SBW - 6;
        context.fill(x, y, x + width, y + height, INSET);
        if (this.sessions.isEmpty()) { MmmUi.drawTextWithin(context, this.textRenderer, "No saved runs", x + 8, y + 8, width - 16, MUTED, false); return; }
        int visibleRows = getVisibleRows(l);
        this.listScroll = Math.max(0, Math.min(this.listScroll, Math.max(0, this.sessions.size() - visibleRows)));
        context.enableScissor(x, y, x + viewportWidth, y + height);
        int drawY = y + 6;
        for (int row = 0; row < visibleRows; row++)
        {
            int index = this.listScroll + row; if (index >= this.sessions.size()) break;
            SessionData session = this.sessions.get(index);
            int rowY = drawY + row * RH;
            boolean hovered = mouseX >= x && mouseX <= x + viewportWidth && mouseY >= rowY && mouseY <= rowY + RH - 4;
            MmmUi.boardRow(context, x, rowY, viewportWidth, RH - 2, hovered, index == this.selectedIndex);
            MmmUi.drawTextWithin(context, this.textRenderer, "#" + (index + 1) + "  " + DATE_FMT.format(new Date(session.startTimeMs)), x + 12, rowY + 6, viewportWidth - 24, TEXT, false);
            MmmUi.drawTextWithin(context, this.textRenderer, UiFormat.formatCompact(session.totalBlocks) + " blocks  |  IGT " + session.getDurationString() + "  |  Time " + session.getWallDurationString(), x + 12, rowY + 19, viewportWidth - 24, MUTED, false);
        }
        context.disableScissor();
        drawListBar(context, x + width - SBW, y, height, mouseX, mouseY, visibleRows);
    }

    private void drawDetail(DrawContext context, Layout l, int mouseX, int mouseY)
    {
        sectionRule(context, l.detailX, l.contentY, l.detailWidth);
        MmmUi.drawTextWithin(context, this.textRenderer, "Session Detail", l.detailX + C, l.contentY + 10, l.detailWidth - C * 2, TEXT, false);
        SessionData session = getSelected();
        if (session == null) { MmmUi.drawTextWithin(context, this.textRenderer, "Select a run to inspect it.", l.detailX + C, l.contentY + 34, l.detailWidth - C * 2, MUTED, false); return; }
        int vx = l.detailX + C;
        int vy = l.contentY + 28;
        int vw = l.detailWidth - C * 2 - SBW - 6;
        int vh = l.contentHeight - 40;
        int drawY = vy - this.detailScroll;
        context.enableScissor(vx, vy, vx + vw, vy + vh);
        MmmUi.drawTextWithin(context, this.textRenderer, DATE_FMT.format(new Date(session.startTimeMs)), vx, drawY, vw, MUTED, false);
        int cardWidth = (vw - G) / 2;
        int statY = drawY + 16;
        stat(context, vx, statY, cardWidth, 50, "Total Mined", UiFormat.formatCompact(session.totalBlocks), "blocks");
        stat(context, vx + cardWidth + G, statY, cardWidth, 50, "Best Hour", UiFormat.formatCompact(session.getBestHourBlocks()), "blocks mined");
        stat(context, vx, statY + 56, cardWidth, 50, "Session IGT", formatClock(session.getActiveDurationMs()), "pauses excluded");
        stat(context, vx + cardWidth + G, statY + 56, cardWidth, 50, "Session Time", formatClock(session.getWallDurationMs()), "pauses included");
        stat(context, vx, statY + 112, cardWidth, 50, "Avg Rate", UiFormat.formatCompact(session.getAverageBlocksPerHour()), "blocks/hr");
        stat(context, vx + cardWidth + G, statY + 112, cardWidth, 50, "Best Streak", session.bestStreakSeconds + "s", "active mining");
        int graphY = statY + 174;
        sectionRule(context, vx, graphY, vw);
        MmmUi.drawTextWithin(context, this.textRenderer, "Session Pace", vx + 10, graphY + 8, vw - 20, TEXT, false);
        MmmUi.drawTextWithin(context, this.textRenderer, "Blocks per hour across the session", vx + 10, graphY + 20, vw - 20, MUTED, false);
        String rateLabel = drawGraph(context, vx + 10, graphY + 34, vw - 20, PACE_CHART_HEIGHT, session, mouseX, mouseY);
        if (rateLabel != null)
        {
            MmmUi.drawTextRightWithin(context, this.textRenderer, rateLabel, vx + vw - 10, graphY + 8, Math.max(72, vw / 2), Configs.getHudNumberColor(), false);
        }
        int infoY = graphY + PACE_CARD_HEIGHT + 10;
        row(context, vx, vx + vw, infoY, "Top Block", getTopBlock(session));
        drawBreakdown(context, vx, infoY + 22, vw, session, mouseX, mouseY);
        context.disableScissor();
        drawDetailBar(context, l, mouseX, mouseY);
    }
    private void drawBreakdown(DrawContext context, int x, int y, int width, SessionData session, int mouseX, int mouseY)
    {
        context.drawText(this.textRenderer, Text.literal("Block Breakdown"), x, y, TEXT, false);
        int cardY = y + 14;
        context.fill(x, cardY, x + width, cardY + BREAKDOWN_HEIGHT, MmmUi.menuSurface(BoardEditorStyle.SURFACE));
        List<Map.Entry<String, Long>> entries = new ArrayList<>(session.blockBreakdown.entrySet());
        entries.sort((left, right) -> Long.compare(right.getValue(), left.getValue()));
        int listX = x + 8;
        int listY = cardY + 8;
        int listHeight = BREAKDOWN_HEIGHT - 16;
        int viewportWidth = width - 16 - SBW - 4;
        this.breakdownListX = listX;
        this.breakdownListY = listY;
        this.breakdownListHeight = listHeight;
        this.breakdownViewportWidth = viewportWidth;
        this.breakdownScrollbarX = x + width - 8;
        this.breakdownEntryCount = entries.size();
        if (entries.isEmpty()) { MmmUi.drawTextWithin(context, this.textRenderer, "No block breakdown recorded.", listX, listY + 2, viewportWidth, MUTED, false); return; }
        context.enableScissor(listX, listY, listX + viewportWidth, listY + listHeight);
        int rowY = listY - this.breakdownScroll;
        for (Map.Entry<String, Long> entry : entries)
        {
            if (rowY + 14 >= listY && rowY <= listY + listHeight)
            {
                String count = UiFormat.formatCompact(entry.getValue());
                int countWidth = this.textRenderer.getWidth(count);
                String name = truncate(resolveName(entry.getKey()), Math.max(40, viewportWidth - countWidth - 8));
                context.drawText(this.textRenderer, Text.literal(name), listX, rowY, LABEL, false);
                context.drawText(this.textRenderer, Text.literal(count), listX + viewportWidth - countWidth - 2, rowY, Configs.getHudNumberColor(), false);
            }
            rowY += 14;
        }
        context.disableScissor();
        if (getBreakdownMax(entries.size(), listHeight) > 0)
        {
            simpleBar(context, this.breakdownScrollbarX, listY, listHeight, getBreakdownThumb(listHeight, entries.size()), getBreakdownOffset(listHeight, entries.size()), this.draggingBreakdown || isOverBreakdownBar(mouseX, mouseY));
        }
    }

    private String drawGraph(DrawContext c, int x, int y, int w, int h, SessionData s, int mouseX, int mouseY)
    {
        c.fill(x, y, x + w, y + h, INSET);
        double axisMax = roundPaceCeiling(Math.max(60d, s.getPeakBlocksPerHour()));
        int axisLeft = getPaceAxisLeft(axisMax);
        int plotX = x + axisLeft;
        int plotY = y + PACE_AXIS_TOP;
        int plotW = Math.max(24, w - axisLeft - PACE_AXIS_RIGHT);
        int plotH = Math.max(18, h - PACE_AXIS_TOP - PACE_AXIS_BOTTOM);

        if (s.miningRateBuckets.isEmpty())
        {
            c.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No graph data saved for this run"), plotX + plotW / 2, plotY + plotH / 2 - 4, MUTED);
            drawGraphAxes(c, plotX, plotY, plotW, plotH, axisLeft, axisMax, s);
            return null;
        }

        int cols = Math.max(1, Math.min(plotW / 4, s.miningRateBuckets.size()));
        double[] rates = new double[cols];
        double maxRate = Math.max(60d, s.getPeakBlocksPerHour());
        for (int col = 0; col < cols; col++)
        {
            int start = (int) Math.floor((col * s.miningRateBuckets.size()) / (double) cols);
            int end = (int) Math.floor(((col + 1) * s.miningRateBuckets.size()) / (double) cols);
            if (end <= start)
            {
                end = Math.min(s.miningRateBuckets.size(), start + 1);
            }

            double total = 0d;
            int count = 0;
            for (int i = start; i < end; i++)
            {
                total += s.getBucketBlocksPerHour(s.miningRateBuckets.get(i));
                count++;
            }

            rates[col] = count <= 0 ? 0d : total / count;
            maxRate = Math.max(maxRate, rates[col]);
        }
        axisMax = roundPaceCeiling(maxRate);

        for (int i = 0; i <= 4; i++)
        {
            int ly = plotY + (plotH * i) / 4;
            c.fill(plotX, ly, plotX + plotW, ly + 1, MmmUi.graphGrid());
        }

        int bottom = plotY + plotH - 1;
        int usable = Math.max(12, plotH - 1);
        float[] py = new float[cols];
        int[] px = new int[cols];
        for (int col = 0; col < cols; col++)
        {
            float n = (float) (rates[col] / axisMax);
            py[col] = bottom - n * usable;
            px[col] = plotX + Math.round((col / (float) Math.max(1, cols - 1)) * (plotW - 1));
        }

        for (int col = 0; col < cols - 1; col++)
        {
            float prev = col > 0 ? py[col - 1] : py[col];
            float next = col + 2 < cols ? py[col + 2] : py[col + 1];
            curve(c, px[col], py[col], px[col + 1], py[col + 1], prev, next);
        }

        int hoveredColumn = -1;
        if (mouseX >= plotX && mouseX <= plotX + plotW && mouseY >= plotY && mouseY <= plotY + plotH)
        {
            int bestDistance = Integer.MAX_VALUE;
            for (int col = 0; col < cols; col++)
            {
                int distance = Math.abs(mouseX - px[col]);
                if (distance < bestDistance)
                {
                    bestDistance = distance;
                    hoveredColumn = col;
                }
            }
        }

        if (hoveredColumn >= 0)
        {
            int markerX = px[hoveredColumn];
            int markerY = Math.round(py[hoveredColumn]);
            c.fill(markerX, plotY, markerX + 1, plotY + plotH, MmmUi.graphGrid());
            c.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, TEXT);
            c.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, MmmUi.accent());
        }

        drawGraphAxes(c, plotX, plotY, plotW, plotH, axisLeft, axisMax, s);
        return (hoveredColumn >= 0
                ? UiFormat.formatCompact(Math.round(rates[hoveredColumn])) + "/hr @ " + formatGraphTimeLabel(timeForColumn(hoveredColumn, cols, s.getDurationMs()))
                : "Peak " + UiFormat.formatCompact(Math.round(maxRate)) + "/hr");
    }
    private void drawGraphAxes(DrawContext c, int plotX, int plotY, int plotW, int plotH, int axisLeft, double axisMax, SessionData s)
    {
        drawRateTick(c, plotX, plotY, plotH, axisLeft, 0, axisMax, LABEL);
        drawRateTick(c, plotX, plotY, plotH, axisLeft, 2, axisMax / 2.0D, MUTED);
        drawRateTick(c, plotX, plotY, plotH, axisLeft, 4, 0D, MUTED);

        int labelY = plotY + plotH + 3;
        String startLabel = "0m";
        String middleLabel = formatGraphTimeLabel(s.getDurationMs() / 2L);
        String endLabel = formatGraphTimeLabel(Math.max(1L, s.getDurationMs()));
        int startWidth = this.textRenderer.getWidth(startLabel);
        int middleWidth = this.textRenderer.getWidth(middleLabel);
        int endWidth = this.textRenderer.getWidth(endLabel);
        c.drawText(this.textRenderer, Text.literal(startLabel), plotX, labelY, MUTED, false);
        c.drawText(this.textRenderer, Text.literal(endLabel), plotX + plotW - endWidth, labelY, MUTED, false);

        int middleX = plotX + (plotW - middleWidth) / 2;
        int startRight = plotX + startWidth + 6;
        int endLeft = plotX + plotW - endWidth - 6;
        if (middleX >= startRight && middleX + middleWidth <= endLeft)
        {
            c.drawText(this.textRenderer, Text.literal(middleLabel), middleX, labelY, MUTED, false);
        }
    }
    private void drawRateTick(DrawContext c, int plotX, int plotY, int plotH, int axisLeft, int step, double value, int color)
    {
        String label = formatPaceTick(value);
        int labelY = Math.max(plotY, Math.min(plotY + plotH - 8, plotY + (plotH * step) / 4 - 4));
        MmmUi.drawTextRightWithin(c, this.textRenderer, label, plotX - 4, labelY, axisLeft - 7, color, false);
    }
    private int getPaceAxisLeft(double axisMax)
    {
        int labelWidth = Math.max(this.textRenderer.getWidth(formatPaceTick(axisMax)), this.textRenderer.getWidth("0"));
        return Math.max(PACE_AXIS_LEFT_MIN, Math.min(PACE_AXIS_LEFT_MAX, labelWidth + 8));
    }
    private String formatPaceTick(double value)
    {
        long rounded = Math.max(0L, Math.round(value));
        if (rounded >= 10_000L)
        {
            return Math.round(rounded / 1_000.0D) + "k";
        }
        if (rounded >= 1_000L)
        {
            long tenths = Math.round(rounded / 100.0D);
            long whole = tenths / 10L;
            long fraction = tenths % 10L;
            return fraction == 0L ? whole + "k" : whole + "." + fraction + "k";
        }
        return Long.toString(rounded);
    }
    private double roundPaceCeiling(double value)
    {
        double target = Math.max(1D, value / 4.0D);
        double magnitude = Math.pow(10D, Math.floor(Math.log10(target)));
        double normalized = target / magnitude;
        double step;
        if (normalized <= 1D)
        {
            step = 1D;
        }
        else if (normalized <= 2D)
        {
            step = 2D;
        }
        else if (normalized <= 5D)
        {
            step = 5D;
        }
        else
        {
            step = 10D;
        }

        return step * magnitude * 4.0D;
    }
    private long timeForColumn(int column, int columns, long durationMs)
    {
        if (columns <= 1)
        {
            return 0L;
        }

        return Math.round((column / (double) (columns - 1)) * Math.max(0L, durationMs));
    }
    private void row(DrawContext c,int lx,int rx,int y,String label,String value){ int width=rx-lx; int valueWidth=Math.min(this.textRenderer.getWidth(value), Math.max(32, width/2)); MmmUi.drawTextWithin(c,this.textRenderer,label,lx,y,Math.max(0,width-valueWidth-8),LABEL,false); MmmUi.drawTextRightWithin(c,this.textRenderer,value,rx,y,valueWidth,TEXT,false); }
    private void stat(DrawContext c,int x,int y,int w,int h,String l,String v,String s){ int tw=w-C*2; MmmUi.boardRow(c, x, y, w, h, false, false); MmmUi.drawTextWithin(c,this.textRenderer,l,x+C,y+9,tw,LABEL,false); int valueColor=inactiveValueColor(v)==INACTIVE?INACTIVE:Configs.getHudNumberColor(); MmmUi.drawTextWithin(c,this.textRenderer,v,x+C,y+24,tw,valueColor,false); MmmUi.drawTextWithin(c,this.textRenderer,s,x+C,y+38,tw,MUTED,false); }
    private static void sectionRule(DrawContext context, int x, int y, int width)
    {
        context.fill(x, y, x + width, y + 1, BoardEditorStyle.LINE);
        context.fill(x, y, x + Math.min(22, width), y + 2, BoardEditorStyle.ROSE);
    }
    private void drawListBar(DrawContext c,int x,int y,int h,int mx,int my,int vis){ int max=Math.max(0,this.sessions.size()-vis); if(max<=0) return; int th=getListThumb(h,vis), ty=y+getListOffset(h,th,max); c.fill(x,y,x+SBW,y+h,MmmUi.SCROLLBAR_TRACK); c.fill(x+1,ty,x+SBW-1,ty+th,MmmUi.scrollbarThumb()); }
    private void drawDetailBar(DrawContext c,Layout l,int mx,int my){ int max=Math.max(0,getDetailContentHeight()-(l.contentHeight-40)); if(max<=0) return; int x=l.detailX+l.detailWidth-C-SBW,y=l.contentY+28,h=l.contentHeight-40,th=Math.max(SBM,Math.min(h,(int)Math.round((h/(double)getDetailContentHeight())*h))), off=(int)Math.round((this.detailScroll/(double)max)*(h-th)); simpleBar(c,x,y,h,th,off,this.draggingDetail||isOverDetailBar(mx,my)); }
    private void simpleBar(DrawContext c,int x,int y,int h,int th,int off,boolean active){ c.fill(x,y,x+SBW,y+h,MmmUi.SCROLLBAR_TRACK); c.fill(x+1,y+off,x+SBW-1,y+off+th,MmmUi.scrollbarThumb()); }
    private int getVisibleRows(Layout l){ return Math.max(1, (l.contentHeight - 68) / RH); }
    private boolean isInList(double mx,double my){ Layout l=layout(); return mx>=l.contentX+C&&mx<=l.contentX+l.leftWidth-C&&my>=l.contentY+44&&my<=l.contentY+l.contentHeight-12; }
    private boolean isInDetail(double mx,double my){ Layout l=layout(); return mx>=l.detailX+C&&mx<=l.detailX+l.detailWidth-C&&my>=l.contentY+28&&my<=l.contentY+l.contentHeight-12; }
    private boolean isOverListBar(double mx,double my){ Layout l=layout(); int x=l.contentX+l.leftWidth-C-SBW,y=l.contentY+44,h=l.contentHeight-56; return mx>=x&&mx<=x+SBW&&my>=y&&my<=y+h; }
    private boolean isOverDetailBar(double mx,double my){ Layout l=layout(); int x=l.detailX+l.detailWidth-C-SBW,y=l.contentY+28,h=l.contentHeight-40; return mx>=x&&mx<=x+SBW&&my>=y&&my<=y+h; }
    private BreakdownMetrics metrics(){ return new BreakdownMetrics(this.breakdownListX,this.breakdownListY,this.breakdownListHeight,this.breakdownViewportWidth,this.breakdownScrollbarX); }
    private boolean isInBreakdown(double mx,double my){ BreakdownMetrics m=metrics(); return m.listHeight > 0 && mx>=m.listX&&mx<=m.listX+m.viewportWidth&&my>=m.listY&&my<=m.listY+m.listHeight; }
    private boolean isOverBreakdownBar(double mx,double my){ BreakdownMetrics m=metrics(); return m.listHeight > 0 && mx>=m.scrollbarX&&mx<=m.scrollbarX+SBW&&my>=m.listY&&my<=m.listY+m.listHeight; }
    private void dragList(double mouseY){ Layout l=layout(); int vis=getVisibleRows(l), max=Math.max(0,this.sessions.size()-vis); if(max<=0){this.listScroll=0; return;} int y=l.contentY+44,h=l.contentHeight-56,th=getListThumb(h,vis), travel=Math.max(1,h-th); double n=Math.max(0.0D, Math.min(1.0D, ((mouseY-th/2.0D)-y)/travel)); setListScroll((int)Math.round(n*max)); }
    private void dragDetail(double mouseY){ Layout l=layout(); int max=Math.max(0,getDetailContentHeight()-(l.contentHeight-40)); if(max<=0){this.detailScroll=0; return;} int y=l.contentY+28,h=l.contentHeight-40,th=Math.max(SBM,Math.min(h,(int)Math.round((h/(double)getDetailContentHeight())*h))), travel=Math.max(1,h-th); double n=Math.max(0.0D, Math.min(1.0D, ((mouseY-th/2.0D)-y)/travel)); setDetailScroll((int)Math.round(n*max)); }
    private void dragBreakdown(double mouseY){ BreakdownMetrics m=metrics(); int count=this.breakdownEntryCount, max=getBreakdownMax(count,m.listHeight); if(max<=0){this.breakdownScroll=0; return;} int th=getBreakdownThumb(m.listHeight,count), travel=Math.max(1,m.listHeight-th); double n=Math.max(0.0D, Math.min(1.0D, ((mouseY-th/2.0D)-m.listY)/travel)); setBreakdownScroll((int)Math.round(n*max)); }
    private int getListThumb(int h,int vis){ if(this.sessions.isEmpty()) return h; int th=(int)Math.round((vis/(double)this.sessions.size())*h); return Math.max(SBM, Math.min(h, th)); }
    private int getListOffset(int h,int th,int max){ return max<=0?0:(int)Math.round((this.listScroll/(double)max)*(h-th)); }
    private int getDetailContentHeight(){ return 634; }
    private int getBreakdownMax(int count,int h){ return Math.max(0, count*14-h); }
    private int getBreakdownThumb(int h,int count){ int ch=Math.max(1, count*14); return Math.max(SBM, Math.min(h, (int)Math.round((h/(double)ch)*h))); }
    private int getBreakdownOffset(int h,int count){ int max=getBreakdownMax(count,h), th=getBreakdownThumb(h,count); return max<=0?0:(int)Math.round((this.breakdownScroll/(double)max)*(h-th)); }
    private void moveSelection(int delta){ if(this.sessions.isEmpty()) return; int prev=this.selectedIndex; this.selectedIndex=Math.max(0, Math.min(this.sessions.size()-1, this.selectedIndex+delta)); Layout l=layout(); int vis=getVisibleRows(l); if(this.selectedIndex<this.listScroll) this.listScroll=this.selectedIndex; else if(this.selectedIndex>=this.listScroll+vis) this.listScroll=this.selectedIndex-vis+1; if(this.selectedIndex!=prev){ this.detailScroll=0; this.breakdownScroll=0; playClick(); } }
    private void setListScroll(int offset){ Layout l=layout(); int max=Math.max(0,this.sessions.size()-getVisibleRows(l)); this.listScroll=Math.max(0,Math.min(max,offset)); }
    private void setDetailScroll(int offset){ Layout l=layout(); int max=Math.max(0,getDetailContentHeight()-(l.contentHeight-40)); this.detailScroll=Math.max(0,Math.min(max,offset)); }
    private void setBreakdownScroll(int offset){ int count=this.breakdownEntryCount; this.breakdownScroll=Math.max(0,Math.min(getBreakdownMax(count,Math.max(0,this.breakdownListHeight)),offset)); }
    private void select(int index){ if(index<0||index>=this.sessions.size()) return; if(this.selectedIndex!=index){ this.selectedIndex=index; this.detailScroll=0; this.breakdownScroll=0; playClick(); } }
    private void playClick(){ MinecraftClient client=MinecraftClient.getInstance(); if(client!=null&&client.getSoundManager()!=null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK,1.0F)); }
    private void curve(DrawContext c,float sx,float sy,float ex,float ey,float py,float ny){ int steps=Math.max(6,Math.round(Math.abs(ex-sx)*1.5F)); for(int s=0;s<=steps;s++){ float t=s/(float)steps, px=MathHelper.lerp(t,sx,ex), ts=(ey-py)*0.5F, te=(ny-sy)*0.5F, t2=t*t, t3=t2*t; float yy=(2.0F*t3-3.0F*t2+1.0F)*sy+(t3-2.0F*t2+t)*ts+(-2.0F*t3+3.0F*t2)*ey+(t3-t2)*te; int ix=Math.round(px), iy=Math.round(yy); c.fill(ix,iy,ix+2,iy+2,MmmUi.accent()); c.fill(ix,iy+2,ix+2,iy+3,MmmUi.graphFill());} }
    private String formatGraphTimeLabel(long durationMs){ long totalSeconds=Math.max(0L,durationMs/1000L), minutes=totalSeconds/60L; if(totalSeconds<60L) return totalSeconds+"s"; if(minutes<60L) return minutes+"m"; long hours=minutes/60L, remainingMinutes=minutes%60L; return remainingMinutes==0L?hours+"h":hours+"h "+remainingMinutes+"m"; }
    private String formatClock(long ms){ long s=Math.max(0L,ms/1000L), h=s/3600L, m=(s%3600L)/60L, sec=s%60L; return String.format("%02d:%02d:%02d",h,m,sec); }
    private static int inactiveValueColor(String value){ String text=value==null?"":value.trim(); return "--".equals(text)||"Paused".equals(text)||"00:00:00".equals(text)?INACTIVE:TEXT; }
    private SessionData getSelected(){ return this.selectedIndex>=0&&this.selectedIndex<this.sessions.size()?this.sessions.get(this.selectedIndex):null; }
    private String getTopBlock(SessionData session){ String id=null; long count=0L; for(Map.Entry<String,Long> e:session.blockBreakdown.entrySet()) if(e.getValue()>count){ id=e.getKey(); count=e.getValue(); } return id==null?"No breakdown":resolveName(id)+" ("+UiFormat.formatCompact(count)+")"; }
    private String resolveName(String id){ try{ Identifier i=Identifier.tryParse(id); if(i!=null){ var b=net.minecraft.registry.Registries.BLOCK.get(i); if(b!=null) return b.getName().getString(); } }catch(Exception ignored){} return id; }
    private String truncate(String value,int maxWidth){ return MmmUi.truncate(this.textRenderer, value, maxWidth); }
    private Layout layout()
    {
        int width = MmmUi.contentWidth(this.width);
        int left = MmmUi.contentLeft(this.width);
        int headerY = MmmUi.TOP_BAR_HEIGHT + 8;
        int top = headerY + 26;
        int listWidth = (width - G) * 2 / 5;
        return new Layout(left, width, headerY, top, listWidth, left + listWidth + G,
                width - listWidth - G, Math.max(1, this.height - top - 8));
    }

    private record Layout(int contentX, int contentWidth, int headerY, int contentY, int leftWidth,
                          int detailX, int detailWidth, int contentHeight) {}
    private record BreakdownMetrics(int listX,int listY,int listHeight,int viewportWidth,int scrollbarX){}
}
