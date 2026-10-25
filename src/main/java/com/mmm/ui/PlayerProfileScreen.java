package com.mmm.ui;

import com.mmm.config.Configs;
import com.mmm.storage.WorldSessionContext;
import com.mmm.sync.CloudSyncManager;
import com.mmm.sync.WebsiteProfileTotals;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;
import java.util.Comparator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public class PlayerProfileScreen extends CompatScreen
{
    private static final String WEBSITE_BASE_URL = "https://www.mmmaniacs.com/player/";
    private static final int PANEL_PADDING = 18;
    private static final int CARD_PADDING = 12;
    private static final int CARD_GAP = 10;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;

    public PlayerProfileScreen(Screen parent)
    {
        super(Component.literal("Player Profile"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        WebsiteProfileTotals.refresh(false);
        this.clearWidgets();
        Layout layout = computeLayout();
        Button profileButton = Button.builder(Component.literal("Open Website Profile"), button -> openWebsiteProfile())
                .bounds(layout.panelRight - 196, layout.headerY - 2, 118, BUTTON_HEIGHT)
                .build();
        profileButton.active = hasProfileName();
        this.addRenderableWidget(profileButton);
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(layout.panelRight - 70, layout.headerY - 2, 60, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        Layout layout = computeLayout();
        MmmUi.backdrop(context, this.width, this.height);
        MmmUi.drawMmmScreensSidebar(context, this.font, this.width, this.height, mouseX, mouseY, "PROFILE");
        MmmUi.card(context, layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight, MmmUi.PANEL, MmmUi.BORDER);

        MmmUi.drawTextWithin(context, this.font, this.title.getString(), layout.contentX, layout.headerY, Math.max(0, layout.contentWidth - 196), MmmUi.TEXT, true);
        if (!layout.compact)
        {
            MmmUi.statusChip(context, this.font, layout.contentX, layout.headerY + 18, syncLabel(), syncColor());
            MmmUi.drawTextWithin(context, this.font, "Local mining data is stored on this client and sent on your account cadence.", layout.contentX, layout.headerY + 42, layout.contentWidth, MmmUi.LABEL, false);
        }

        drawTotalsCard(context, layout.leftX, layout.cardsY, layout.cardWidth, layout.topHeight);
        drawRecordsCard(context, layout.rightX, layout.cardsY, layout.cardWidth, layout.topHeight);
        drawSourceCard(context, layout.leftX, layout.lowerY, layout.cardWidth, layout.lowerHeight);
        drawBreakdownCard(context, layout.rightX, layout.lowerY, layout.cardWidth, layout.lowerHeight);

        super.extractRenderState(context, mouseX, mouseY, delta);
        MmmUi.drawMmmTopBar(context, this.font, this.width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && MmmUi.handleMmmScreensSidebarClick(this, this.parent, mouseX, mouseY, "PROFILE"))
        {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose()
    {
        Minecraft.getInstance().setScreen(this.parent);
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

    private void drawTotalsCard(GuiGraphicsExtractor context, int x, int y, int width, int height)
    {
        long globalTotal = MiningStats.getGlobalTotalMinedForDisplay();
        long worldTotal = MiningStats.getCurrentSourceTotalMined();
        MmmUi.card(context, x, y, width, height, MmmUi.CARD, MmmUi.BORDER);
        drawCardTitle(context, x, y, width, "Totals");
        int firstRow = height < 90 ? 26 : 30;
        int step = height < 90 ? 20 : 26;
        drawBlocksMetric(context, x, y + firstRow, width, "Global Total", globalTotal);
        drawBlocksMetric(context, x, y + firstRow + step, width, "World Total", worldTotal);
        if (height >= 86)
        {
            MmmUi.drawTextWithin(context, this.font, lastGlobalUpdateText(), x + CARD_PADDING, y + height - 14, width - CARD_PADDING * 2, MmmUi.MUTED, false);
        }
    }

    private void drawRecordsCard(GuiGraphicsExtractor context, int x, int y, int width, int height)
    {
        long dailyBlocks = MiningStats.getDailyBlocksMined();
        long weeklyBlocks = MiningStats.getWeeklyBlocksMined();
        long dailyRecord = MiningStats.getPersonalRecordDailyBlocks();
        long weeklyRecord = MiningStats.getPersonalRecordWeeklyBlocks();
        MmmUi.card(context, x, y, width, height, MmmUi.CARD, MmmUi.BORDER);
        drawCardTitle(context, x, y, width, "Records");
        int firstRow = height < 90 ? 25 : 28;
        int step = height < 90 ? 19 : 24;
        drawDualBlocksMetric(context, x, y + firstRow, width, "Today / Week", dailyBlocks, weeklyBlocks);
        drawDualBlocksMetric(context, x, y + firstRow + step, width, "PR Day / Week", dailyRecord, weeklyRecord);
        String fastest100k = MiningStats.getFastest100kClock();
        drawMetric(context, x, y + firstRow + step * 2, width, "Fastest 100k", fastest100k, "--".equals(fastest100k) ? MmmUi.INACTIVE : MmmUi.TEXT);
    }

    private void drawSourceCard(GuiGraphicsExtractor context, int x, int y, int width, int height)
    {
        WorldSessionContext.WorldInfo world = WorldSessionContext.getCurrentWorldInfo();
        MmmUi.card(context, x, y, width, height, MmmUi.CARD_SOFT, MmmUi.BORDER);
        drawCardTitle(context, x, y, width, "Current Source");
        int firstRow = height < 130 ? 26 : 30;
        int step = height < 130 ? 19 : 26;
        drawMetric(context, x, y + firstRow, width, "Name", MmmUi.truncate(this.font, world.displayName(), width - 92), MmmUi.TEXT);
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

    private void drawBreakdownCard(GuiGraphicsExtractor context, int x, int y, int width, int height)
    {
        WorldSessionContext.WorldInfo world = WorldSessionContext.getCurrentWorldInfo();
        Configs.WorldStatsEntry stats = Configs.getOrCreateWorldStats(world.id(), world.displayName(), world.kind(), world.host());
        MmmUi.card(context, x, y, width, height, MmmUi.CARD_SOFT, MmmUi.BORDER);
        drawCardTitle(context, x, y, width, "Mined Blocks");

        if (stats.blockBreakdown == null || stats.blockBreakdown.isEmpty())
        {
            MmmUi.drawTextWithin(context, this.font, "No per-block data stored for this source yet.", x + CARD_PADDING, y + 34, width - CARD_PADDING * 2, MmmUi.MUTED, false);
            return;
        }

        int rowY = y + 30;
        int index = 0;
        for (Map.Entry<String, Long> entry : stats.blockBreakdown.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .limit(Math.max(1, (height - 42) / 16))
                .toList())
        {
            int rowColor = index % 2 == 0 ? MmmUi.ROW_ALT : MmmUi.INSET;
            context.fill(x + CARD_PADDING, rowY - 3, x + width - CARD_PADDING, rowY + 12, rowColor);
            String count = UiFormat.formatCompact(entry.getValue());
            int countWidth = this.font.width(count);
            int nameMaxWidth = Math.max(0, width - CARD_PADDING * 2 - countWidth - 20);
            MmmUi.drawTextWithin(context, this.font, formatBlockId(entry.getKey()), x + CARD_PADDING + 6, rowY, nameMaxWidth, MmmUi.TEXT, false);
            MmmUi.drawTextRightWithin(context, this.font, count, x + width - CARD_PADDING - 6, rowY, countWidth, Configs.getHudNumberColor(), false);
            rowY += 16;
            index++;
        }
    }

    private void drawCardTitle(GuiGraphicsExtractor context, int x, int y, int width, String title)
    {
        MmmUi.drawTextWithin(context, this.font, title, x + CARD_PADDING, y + 10, width - CARD_PADDING * 2, MmmUi.TEXT, false);
    }

    private void drawMetric(GuiGraphicsExtractor context, int x, int y, int width, String label, String value, int valueColor)
    {
        int labelWidth = Math.min(86, Math.max(58, (width - CARD_PADDING * 2) / 2));
        int valueWidth = Math.max(0, width - CARD_PADDING * 2 - labelWidth - 8);
        MmmUi.drawTextWithin(context, this.font, label, x + CARD_PADDING, y, labelWidth, MmmUi.MUTED, false);
        MmmUi.drawTextRightWithin(context, this.font, value, x + width - CARD_PADDING, y, valueWidth, valueColor, false);
    }

    private void drawBlocksMetric(GuiGraphicsExtractor context, int x, int y, int width, String label, long value)
    {
        int labelWidth = Math.min(86, Math.max(58, (width - CARD_PADDING * 2) / 2));
        int valueWidth = Math.max(0, width - CARD_PADDING * 2 - labelWidth - 8);
        MmmUi.drawTextWithin(context, this.font, label, x + CARD_PADDING, y, labelWidth, MmmUi.MUTED, false);
        String number = UiFormat.formatCompact(value);
        String suffix = " Blocks Mined";
        int suffixWidth = this.font.width(suffix);
        int numberWidth = this.font.width(number);
        if (numberWidth + suffixWidth > valueWidth)
        {
            suffix = "";
            suffixWidth = 0;
        }
        if (numberWidth > valueWidth)
        {
            MmmUi.drawTextRightWithin(context, this.font, number, x + width - CARD_PADDING, y, valueWidth, Configs.getHudNumberColor(), false);
            return;
        }
        int drawX = x + width - CARD_PADDING - numberWidth - suffixWidth;
        context.text(this.font, Component.literal(number), drawX, y, Configs.getHudNumberColor(), false);
        if (suffix.isEmpty() == false)
        {
            context.text(this.font, Component.literal(suffix), drawX + numberWidth, y, MmmUi.TEXT, false);
        }
    }

    private void drawDualBlocksMetric(GuiGraphicsExtractor context, int x, int y, int width, String label, long left, long right)
    {
        int labelWidth = Math.min(86, Math.max(58, (width - CARD_PADDING * 2) / 2));
        int valueWidth = Math.max(0, width - CARD_PADDING * 2 - labelWidth - 8);
        MmmUi.drawTextWithin(context, this.font, label, x + CARD_PADDING, y, labelWidth, MmmUi.MUTED, false);
        String leftText = UiFormat.formatCompact(left);
        String separator = " / ";
        String rightText = UiFormat.formatCompact(right);
        int totalWidth = this.font.width(leftText) + this.font.width(separator) + this.font.width(rightText);
        if (totalWidth > valueWidth)
        {
            separator = "/";
            totalWidth = this.font.width(leftText) + this.font.width(separator) + this.font.width(rightText);
        }
        if (totalWidth > valueWidth)
        {
            MmmUi.drawTextRightWithin(context, this.font, rightText, x + width - CARD_PADDING, y, valueWidth, Configs.getHudNumberColor(), false);
            return;
        }
        int drawX = x + width - CARD_PADDING - totalWidth;
        context.text(this.font, Component.literal(leftText), drawX, y, Configs.getHudNumberColor(), false);
        drawX += this.font.width(leftText);
        context.text(this.font, Component.literal(separator), drawX, y, MmmUi.TEXT, false);
        drawX += this.font.width(separator);
        context.text(this.font, Component.literal(rightText), drawX, y, Configs.getHudNumberColor(), false);
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
            Util.getPlatform().openUri(WEBSITE_BASE_URL + username.toLowerCase());
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
            Minecraft client = Minecraft.getInstance();
            username = client != null && client.getUser() != null ? client.getUser().getName() : "";
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
        boolean compact = this.height < 400 || MmmUi.contentWidth(this.width) < 480;
        int panelWidth = Math.min(620, Math.max(1, MmmUi.contentWidth(this.width) - 12));
        int availableHeight = Math.max(1, this.height - MmmUi.TOP_BAR_HEIGHT - 8);
        int panelHeight = Math.min(availableHeight, 336);
        int panelX = MmmUi.centerContentX(this.width, panelWidth);
        int panelY = MmmUi.TOP_BAR_HEIGHT + Math.max(4, (availableHeight - panelHeight) / 2);
        int padding = compact ? 10 : PANEL_PADDING;
        int contentX = panelX + padding;
        int contentWidth = panelWidth - padding * 2;
        int cardWidth = (contentWidth - CARD_GAP) / 2;
        int cardsY = panelY + (compact ? 38 : 74);
        int topHeight = compact ? 70 : 102;
        int lowerY = cardsY + topHeight + CARD_GAP;
        int lowerHeight = Math.max(48, panelY + panelHeight - lowerY - padding);
        return new Layout(panelX, panelY, panelWidth, panelHeight, panelX + panelWidth, contentX, panelY + (compact ? 10 : 16), contentWidth, contentX, contentX + cardWidth + CARD_GAP, cardWidth, cardsY, topHeight, lowerY, lowerHeight, compact);
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight, int panelRight, int contentX, int headerY, int contentWidth, int leftX, int rightX, int cardWidth, int cardsY, int topHeight, int lowerY, int lowerHeight, boolean compact)
    {
    }
}
