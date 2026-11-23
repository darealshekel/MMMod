package com.mmm.ui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import com.mmm.config.Configs;
import com.mmm.gui.GuiConfigs;
import com.mmm.hud.SessionHistoryScreen;
import com.mmm.hud.SummaryScreen;
import com.mmm.scoreboard.ScoreboardScreen;
import com.mmm.tracker.MiningStats;

public final class MmmUi
{
    public static final int BLACK = 0xFF050505;
    public static final int RED = 0xFFE00000;
    public static final int OVERLAY = 0xFF050505;
    public static final int PANEL = 0xFF050505;
    public static final int CARD = BoardEditorStyle.SURFACE;
    public static final int CARD_SOFT = CARD;
    public static final int INSET = 0xFF121212;
    public static final int BORDER = BoardEditorStyle.LINE;
    public static final int BORDER_SOFT = BoardEditorStyle.LINE;
    public static final int ACCENT = RED;
    public static final int ACCENT_BRIGHT = RED;
    public static final int ACCENT_SOFT = 0x33E00000;
    public static final int ACCENT_ROW = 0x3DE00000;
    public static final int TEXT = 0xFFF6F3EF;
    public static final int LABEL = 0xD8C9CDD5;
    public static final int MUTED = BoardEditorStyle.MUTED;
    public static final int INACTIVE = 0xFF949494;
    public static final int SUCCESS = 0xFF43D483;
    public static final int WARNING = 0xFFFFC857;
    public static final int ERROR = 0xFFFF5965;
    public static final int BLUE = RED;
    public static final int ROW_SELECTED = 0x52E00000;
    public static final int ROW_HOVER = 0x26E00000;
    public static final int ROW_ALT = 0x24101010;
    public static final int GRAPH_FILL = 0xBFE00000;
    public static final int GRAPH_GRID = 0x28E00000;
    public static final int SCROLLBAR_TRACK = 0x44090909;
    public static final int SCROLLBAR_THUMB = 0xFFC20000;
    public static final int SCROLLBAR_THUMB_HOVER = RED;
    public static final int SCROLLBAR_THUMB_ACTIVE = RED;
    public static final int TOP_BAR_HEIGHT = BoardEditorStyle.HEADER_HEIGHT;
    public static final int PAGE_PAD = 14;


    private MmmUi()
    {
    }

    public static void ensureCursorVisible()
    {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.mouseHandler != null)
        {
            client.mouseHandler.releaseMouse();
        }
    }

    public static boolean shouldPauseGame()
    {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.isLocalServer();
    }

    public static int accent()
    {
        return Configs.getMenuColor();
    }

    public static int accentSoft()
    {
        return withAlpha(accent(), 0x33);
    }

    public static int accentHover()
    {
        return withAlpha(accent(), 0x22);
    }

    public static int rowSelected()
    {
        return withAlpha(accent(), 0x52);
    }

    public static int rowHover()
    {
        return withAlpha(accent(), 0x26);
    }

    public static int graphFill()
    {
        return withAlpha(accent(), 0xBF);
    }

    public static int graphGrid()
    {
        return withAlpha(accent(), 0x28);
    }

    public static int scrollbarThumb()
    {
        return accent();
    }

    private static int withAlpha(int color, int alpha)
    {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    public static int menuSurface(int color)
    {
        int configuredAlpha = Math.round(255.0F * Configs.Generic.MENU_OPACITY.getIntegerValue() / 100.0F);
        int baseAlpha = color >>> 24;
        int alpha = Math.round(baseAlpha * configuredAlpha / 255.0F);
        return withAlpha(color, alpha);
    }

    public static void backdrop(GuiGraphicsExtractor context, int width, int height)
    {
        context.fill(0, 0, width, height, menuSurface(BoardEditorStyle.SURFACE));
    }

    public static int pagePad(int screenWidth)
    {
        return screenWidth < 720 ? 6 : screenWidth < 900 ? 10 : PAGE_PAD;
    }

    public static int contentLeft(int screenWidth)
    {
        return centerContentX(screenWidth, contentWidth(screenWidth));
    }

    public static int contentWidth(int screenWidth)
    {
        return BoardEditorStyle.contentWidth(screenWidth, 840);
    }

    public static int centerContentX(int screenWidth, int contentWidth)
    {
        return Math.max(0, (screenWidth - contentWidth) / 2);
    }

    public static void drawSectionHeading(GuiGraphicsExtractor context, Font renderer, String title, int x, int y, int maxWidth)
    {
        drawTextWithin(context, renderer, title, x, y, maxWidth, BoardEditorStyle.ROSE, false);
    }

    public static void card(GuiGraphicsExtractor context, int x, int y, int width, int height, int fillColor, int borderColor)
    {
        BoardEditorStyle.plate(context::fill, x, y, width, height, menuSurface(fillColor), menuSurface(borderColor));
    }

    public static void drawBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color)
    {
        context.outline(x, y, width, height, color);
    }

    public static void fieldShell(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean focused)
    {
        card(context, x, y, width, height, INSET, focused ? accent() : BORDER_SOFT);
    }

    public static void pill(GuiGraphicsExtractor context, Font renderer, int x, int y, int width, int height, String text)
    {
        card(context, x, y, width, height, CARD, accent());
        String clipped = truncate(renderer, text, width - 8);
        int textX = x + Math.max(4, (width - renderer.width(clipped)) / 2);
        context.text(renderer, Component.literal(clipped), textX, y + 4, accent(), false);
    }

    public static void statusChip(GuiGraphicsExtractor context, Font renderer, int x, int y, String text, int borderColor)
    {
        int width = renderer.width(text) + 14;
        context.fill(x + 1, y + 5, x + 4, y + 11, borderColor);
        drawTextWithin(context, renderer, text, x + 7, y + 4, width - 14, TEXT, false);
    }

    public static void wrappedText(GuiGraphicsExtractor context, Font renderer, String text, int x, int y, int maxWidth, int color)
    {
        List<FormattedCharSequence> lines = renderer.split(Component.literal(text).setStyle(Style.EMPTY), maxWidth);
        int lineY = y;
        for (FormattedCharSequence line : lines)
        {
            context.text(renderer, line, x, lineY, color, false);
            lineY += 10;
        }
    }

    public static void drawTextWithin(GuiGraphicsExtractor context, Font renderer, String value, int x, int y, int maxWidth, int color, boolean shadow)
    {
        if (maxWidth <= 0)
        {
            return;
        }

        context.text(renderer, Component.literal(truncate(renderer, value, maxWidth)), x, y, color, shadow);
    }

    public static void drawTextRightWithin(GuiGraphicsExtractor context, Font renderer, String value, int rightX, int y, int maxWidth, int color, boolean shadow)
    {
        if (maxWidth <= 0)
        {
            return;
        }

        String clipped = truncate(renderer, value, maxWidth);
        context.text(renderer, Component.literal(clipped), rightX - renderer.width(clipped), y, color, shadow);
    }

    public static String truncate(Font renderer, String value, int maxWidth)
    {
        if (value == null)
        {
            return "";
        }
        if (maxWidth <= 0)
        {
            return "";
        }
        if (renderer.width(value) <= maxWidth)
        {
            return value;
        }

        String ellipsis = "...";
        if (renderer.width(ellipsis) > maxWidth)
        {
            return "";
        }

        String trimmed = value;
        while (trimmed.length() > 1 && renderer.width(trimmed + ellipsis) > maxWidth)
        {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (renderer.width(trimmed + ellipsis) > maxWidth)
        {
            return ellipsis;
        }
        return trimmed + ellipsis;
    }

    static List<ToolDirectory> availableTools()
    {
        return List.of(ToolDirectory.values());
    }

    static void openTool(Screen parent, ToolDirectory tool)
    {
        BoardPlatform.open(switch (tool) {
            case HOTKEYS -> new GuiConfigs(parent);
            case PROJECTS -> new ProjectManagerScreen(parent);
            case PROFILE -> new PlayerProfileScreen(parent);
            case HISTORY -> new SessionHistoryScreen(parent);
            case SUMMARY -> new SummaryScreen(MiningStats.getCurrentSession(), parent);
            case SCOREBOARD -> new ScoreboardScreen(parent);
            case LEADERBOARDS -> new LeaderboardScreen(parent);
        });
    }
}
