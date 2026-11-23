package com.mmm.ui;

import com.mmm.config.Configs;
import com.mmm.storage.WorldSessionContext;
import com.mmm.sync.CloudSyncManager;
import com.mmm.sync.WebsiteProfileTotals;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;
import java.util.Comparator;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class PlayerProfileScreen extends Screen
{
    private static final String WEBSITE_BASE_URL = "https://www.mmmaniacs.com/player/";
    private static final int TOP_SECTION = 102;
    private static final int LOWER_SECTION = 180;
    private static final int BODY_TOP = MmmUi.TOP_BAR_HEIGHT + 42;
    private static final int CARD_PADDING = 12;
    private static final int CARD_GAP = 10;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private int scroll;

    public PlayerProfileScreen(Screen parent)
    {
        super(Text.literal("Player Profile"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        WebsiteProfileTotals.refresh(false);
        this.clearChildren();
        Layout layout = computeLayout();
        ButtonWidget profileButton = ButtonWidget.builder(Text.literal("Open Website Profile"), button -> openWebsiteProfile())
                .dimensions(layout.left + layout.width - 118, MmmUi.TOP_BAR_HEIGHT + 9, 118, BUTTON_HEIGHT)
                .build();
        profileButton.active = hasProfileName();
        this.addDrawableChild(profileButton);
        this.addDrawableChild(MmmUi.backButton(this, layout.left));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        Layout layout = computeLayout();
        MmmUi.toolBoard(context, this.textRenderer, this.width, this.height, layout.left, "Tools / Profile");
        String player = resolveProfileName();
        int y = MmmUi.TOP_BAR_HEIGHT + 8;
        MmmUi.drawTextWithin(context, this.textRenderer, player.isBlank() ? "Player profile" : player,
                layout.left, y, layout.width - 124, MmmUi.TEXT, false);
        context.fill(layout.left, y + 15, layout.left + 3, y + 23, syncColor());
        MmmUi.drawTextWithin(context, this.textRenderer, syncLabel(), layout.left + 8, y + 15,
                layout.width - 132, BoardEditorStyle.MUTED, false);

        this.scroll = Math.min(this.scroll, layout.maxScroll);
        int top = BODY_TOP - this.scroll;
        int w = layout.columnWidth();
        int secondX = layout.stacked ? layout.left : layout.left + w + CARD_GAP;
        int secondY = layout.stacked ? top + TOP_SECTION + CARD_GAP : top;
        int lower = secondY + TOP_SECTION + CARD_GAP;
        int bottom = Math.max(BODY_TOP, this.height - 6);
        context.enableScissor(layout.left, BODY_TOP, layout.left + layout.width, bottom);
        drawTotalsCard(context, layout.left, top, w, TOP_SECTION);
        drawRecordsCard(context, secondX, secondY, w, TOP_SECTION);
        drawSourceCard(context, layout.left, lower, w, LOWER_SECTION);
        drawBreakdownCard(context, secondX, layout.stacked ? lower + LOWER_SECTION + CARD_GAP : lower, w, LOWER_SECTION);
        context.disableScissor();
        if (layout.maxScroll > 0 && bottom > BODY_TOP)
        {
            int track = bottom - BODY_TOP;
            int thumb = Math.max(6, track * track / (track + layout.maxScroll));
            int thumbY = BODY_TOP + (track - thumb) * this.scroll / layout.maxScroll;
            context.fill(layout.left + layout.width + 2, thumbY, layout.left + layout.width + 4, thumbY + thumb, BoardEditorStyle.ROSE);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical)
    {
        if (y < BODY_TOP) return super.mouseScrolled(x, y, horizontal, vertical);
        this.scroll = Math.clamp(this.scroll - (int) (vertical * 24), 0, computeLayout().maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        if (key == 267 || key == 266)
        {
            int page = Math.max(24, this.height - BODY_TOP - 6);
            this.scroll = Math.clamp(this.scroll + (key == 267 ? page : -page), 0, computeLayout().maxScroll);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
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

    private void drawTotalsCard(DrawContext context, int x, int y, int width, int height)
    {
        long globalTotal = MiningStats.getGlobalTotalMinedForDisplay();
        long worldTotal = MiningStats.getCurrentSourceTotalMined();
        drawCardTitle(context, x, y, width, "Totals");
        int firstRow = height < 90 ? 26 : 30;
        int step = height < 90 ? 20 : 26;
        drawBlocksMetric(context, x, y + firstRow, width, "Global Total", globalTotal);
        drawBlocksMetric(context, x, y + firstRow + step, width, "World Total", worldTotal);
        if (height >= 86)
        {
            MmmUi.drawTextWithin(context, this.textRenderer, lastGlobalUpdateText(), x + CARD_PADDING, y + height - 14, width - CARD_PADDING * 2, BoardEditorStyle.MUTED, false);
        }
    }

    private void drawRecordsCard(DrawContext context, int x, int y, int width, int height)
    {
        long dailyBlocks = MiningStats.getDailyBlocksMined();
        long weeklyBlocks = MiningStats.getWeeklyBlocksMined();
        long dailyRecord = MiningStats.getPersonalRecordDailyBlocks();
        long weeklyRecord = MiningStats.getPersonalRecordWeeklyBlocks();
        drawCardTitle(context, x, y, width, "Records");
        int firstRow = height < 90 ? 25 : 28;
        int step = height < 90 ? 14 : 24;
        drawDualBlocksMetric(context, x, y + firstRow, width, "Today / Week", dailyBlocks, weeklyBlocks);
        drawDualBlocksMetric(context, x, y + firstRow + step, width, "PR Day / Week", dailyRecord, weeklyRecord);
        String fastest100k = MiningStats.getFastest100kClock();
        drawMetric(context, x, y + firstRow + step * 2, width, "Fastest 100k", fastest100k, "--".equals(fastest100k) ? MmmUi.INACTIVE : MmmUi.TEXT);
    }

    private void drawSourceCard(DrawContext context, int x, int y, int width, int height)
    {
        WorldSessionContext.WorldInfo world = WorldSessionContext.getCurrentWorldInfo();
        drawCardTitle(context, x, y, width, "Current Source");
        int firstRow = height < 130 ? 26 : 30;
        int step = height < 130 ? 19 : 26;
        drawMetric(context, x, y + firstRow, width, "Name", MmmUi.truncate(this.textRenderer, world.displayName(), width - 92), MmmUi.TEXT);
        if (firstRow + step + 12 < height)
        {
            drawMetric(context, x, y + firstRow + step, width, "Type", world.kind(), MmmUi.TEXT);
        }
        if (firstRow + step * 2 + 12 < height)
        {
            drawMetric(context, x, y + firstRow + step * 2, width, "Estimated Pace", UiFormat.formatDetailedBlocksPerHour(MiningStats.getEstimatedBlocksPerHour()), MmmUi.TEXT);
        }
        if (firstRow + step * 3 + 12 < height)
        {
            drawMetric(context, x, y + firstRow + step * 3, width, "Sync Every", UiFormat.formatDuration(CloudSyncManager.getSyncIntervalMs() / 1000L), MmmUi.TEXT);
        }
    }

    private void drawBreakdownCard(DrawContext context, int x, int y, int width, int height)
    {
        WorldSessionContext.WorldInfo world = WorldSessionContext.getCurrentWorldInfo();
        Configs.WorldStatsEntry stats = Configs.getOrCreateWorldStats(world.id(), world.displayName(), world.kind(), world.host());
        drawCardTitle(context, x, y, width, "Mined Blocks");

        if (stats.blockBreakdown == null || stats.blockBreakdown.isEmpty())
        {
            MmmUi.drawTextWithin(context, this.textRenderer, "No per-block data stored for this source yet.", x + CARD_PADDING, y + 34, width - CARD_PADDING * 2, BoardEditorStyle.MUTED, false);
            return;
        }

        int rowY = y + 30;
        for (Map.Entry<String, Long> entry : stats.blockBreakdown.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .limit(Math.max(1, (height - 42) / 16))
                .toList())
        {
            MmmUi.boardRow(context, x, rowY - 3, width, 16, false, false);
            String count = UiFormat.formatCompact(entry.getValue());
            int countWidth = this.textRenderer.getWidth(count);
            int nameMaxWidth = Math.max(0, width - CARD_PADDING * 2 - countWidth - 20);
            MmmUi.drawTextWithin(context, this.textRenderer, formatBlockId(entry.getKey()), x + CARD_PADDING + 6, rowY, nameMaxWidth, MmmUi.TEXT, false);
            MmmUi.drawTextRightWithin(context, this.textRenderer, count, x + width - CARD_PADDING - 6, rowY, countWidth, Configs.getHudNumberColor(), false);
            rowY += 16;
        }
    }

    private void drawCardTitle(DrawContext context, int x, int y, int width, String title)
    {
        context.fill(x, y, x + width, y + 1, BoardEditorStyle.LINE);
        context.fill(x, y, x + Math.min(22, width), y + 2, BoardEditorStyle.ROSE);
        MmmUi.drawTextWithin(context, this.textRenderer, title, x + CARD_PADDING, y + 10, width - CARD_PADDING * 2, BoardEditorStyle.TEXT, false);
    }

    private void drawMetric(DrawContext context, int x, int y, int width, String label, String value, int valueColor)
    {
        context.fill(x, y + 12, x + width, y + 13, BoardEditorStyle.LINE);
        int labelWidth = Math.min(86, Math.max(58, (width - CARD_PADDING * 2) / 2));
        int valueWidth = Math.max(0, width - CARD_PADDING * 2 - labelWidth - 8);
        MmmUi.drawTextWithin(context, this.textRenderer, label, x + CARD_PADDING, y, labelWidth, BoardEditorStyle.MUTED, false);
        MmmUi.drawTextRightWithin(context, this.textRenderer, value, x + width - CARD_PADDING, y, valueWidth, valueColor, false);
    }

    private void drawBlocksMetric(DrawContext context, int x, int y, int width, String label, long value)
    {
        context.fill(x, y + 12, x + width, y + 13, BoardEditorStyle.LINE);
        int labelWidth = Math.min(86, Math.max(58, (width - CARD_PADDING * 2) / 2));
        int valueWidth = Math.max(0, width - CARD_PADDING * 2 - labelWidth - 8);
        MmmUi.drawTextWithin(context, this.textRenderer, label, x + CARD_PADDING, y, labelWidth, BoardEditorStyle.MUTED, false);
        String number = UiFormat.formatCompact(value);
        String suffix = " Blocks Mined";
        int suffixWidth = this.textRenderer.getWidth(suffix);
        int numberWidth = this.textRenderer.getWidth(number);
        if (numberWidth + suffixWidth > valueWidth)
        {
            suffix = "";
            suffixWidth = 0;
        }
        if (numberWidth > valueWidth)
        {
            MmmUi.drawTextRightWithin(context, this.textRenderer, number, x + width - CARD_PADDING, y, valueWidth, Configs.getHudNumberColor(), false);
            return;
        }
        int drawX = x + width - CARD_PADDING - numberWidth - suffixWidth;
        context.drawText(this.textRenderer, Text.literal(number), drawX, y, Configs.getHudNumberColor(), false);
        if (suffix.isEmpty() == false)
        {
            context.drawText(this.textRenderer, Text.literal(suffix), drawX + numberWidth, y, MmmUi.TEXT, false);
        }
    }

    private void drawDualBlocksMetric(DrawContext context, int x, int y, int width, String label, long left, long right)
    {
        context.fill(x, y + 12, x + width, y + 13, BoardEditorStyle.LINE);
        int labelWidth = Math.min(86, Math.max(58, (width - CARD_PADDING * 2) / 2));
        int valueWidth = Math.max(0, width - CARD_PADDING * 2 - labelWidth - 8);
        MmmUi.drawTextWithin(context, this.textRenderer, label, x + CARD_PADDING, y, labelWidth, BoardEditorStyle.MUTED, false);
        String leftText = UiFormat.formatCompact(left);
        String separator = " / ";
        String rightText = UiFormat.formatCompact(right);
        int totalWidth = this.textRenderer.getWidth(leftText) + this.textRenderer.getWidth(separator) + this.textRenderer.getWidth(rightText);
        if (totalWidth > valueWidth)
        {
            separator = "/";
            totalWidth = this.textRenderer.getWidth(leftText) + this.textRenderer.getWidth(separator) + this.textRenderer.getWidth(rightText);
        }
        if (totalWidth > valueWidth)
        {
            MmmUi.drawTextRightWithin(context, this.textRenderer, rightText, x + width - CARD_PADDING, y, valueWidth, Configs.getHudNumberColor(), false);
            return;
        }
        int drawX = x + width - CARD_PADDING - totalWidth;
        context.drawText(this.textRenderer, Text.literal(leftText), drawX, y, Configs.getHudNumberColor(), false);
        drawX += this.textRenderer.getWidth(leftText);
        context.drawText(this.textRenderer, Text.literal(separator), drawX, y, MmmUi.TEXT, false);
        drawX += this.textRenderer.getWidth(separator);
        context.drawText(this.textRenderer, Text.literal(rightText), drawX, y, Configs.getHudNumberColor(), false);
    }

    private String syncLabel()
    {
        String tier = CloudSyncManager.getSyncTier();
        return tier.replace('_', ' ').toUpperCase() + " sync";
    }

    private int syncColor()
    {
        return switch (CloudSyncManager.getSyncTier())
        {
            case "owner", "supporter_plus" -> MmmUi.ACCENT;
            case "supporter" -> MmmUi.BLUE;
            default -> MmmUi.BORDER;
        };
    }

    private String lastGlobalUpdateText()
    {
        long updated = Math.max(Configs.websiteGlobalTotalUpdatedAtMs, Configs.websiteLastSuccessfulSyncMs);
        if (updated <= 0L)
        {
            return "Website profile total not loaded yet";
        }
        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - updated) / 1000L);
        return "Website profile update " + UiFormat.formatDuration(ageSeconds) + " ago";
    }

    private void openWebsiteProfile()
    {
        String username = resolveProfileName();
        if (username != null && username.isBlank() == false)
        {
            Util.getOperatingSystem().open(WEBSITE_BASE_URL + username.toLowerCase());
        }
    }

    private boolean hasProfileName()
    {
        String username = resolveProfileName();
        return username != null && username.isBlank() == false;
    }

    private String resolveProfileName()
    {
        String username = Configs.websiteLinkedMinecraftUsername;
        if (username == null || username.isBlank())
        {
            MinecraftClient client = MinecraftClient.getInstance();
            username = client != null && client.getSession() != null ? client.getSession().getUsername() : "";
        }
        return username;
    }

    private static String formatBlockId(String blockId)
    {
        String value = blockId == null ? "" : blockId;
        int separator = value.indexOf(':');
        if (separator >= 0 && separator + 1 < value.length())
        {
            value = value.substring(separator + 1);
        }
        return value.replace('_', ' ');
    }

    private Layout computeLayout()
    {
        int width = BoardEditorStyle.contentWidth(this.width, 620);
        boolean stacked = width < 480;
        int contentHeight = stacked ? 2 * (TOP_SECTION + LOWER_SECTION) + 3 * CARD_GAP
                : TOP_SECTION + CARD_GAP + LOWER_SECTION;
        return new Layout((this.width - width) / 2, width, stacked,
                Math.max(0, contentHeight - Math.max(0, this.height - BODY_TOP - 6)));
    }

    private record Layout(int left, int width, boolean stacked, int maxScroll)
    {
        int columnWidth() { return stacked ? width : (width - CARD_GAP) / 2; }
    }
}
