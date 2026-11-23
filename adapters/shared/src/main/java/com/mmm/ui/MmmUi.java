package com.mmm.ui;

import java.util.List;

import com.mmm.config.Configs;
import com.mmm.gui.GuiConfigs;
import com.mmm.hud.SessionHistoryScreen;
import com.mmm.hud.SummaryScreen;
import com.mmm.scoreboard.ScoreboardScreen;
import com.mmm.tracker.MiningStats;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class MmmUi
{
    public static final int BLACK = 0xFF050505;
    public static final int RED = 0xFFE00000;
    public static final int OVERLAY = 0xFF050505;
    public static final int PANEL = 0xFF050505;
    public static final int CARD = BoardEditorStyle.TILE;
    public static final int CARD_SOFT = CARD;
    public static final int INSET = BoardEditorStyle.SURFACE;
    public static final int BORDER = BoardEditorStyle.LINE;
    public static final int BORDER_SOFT = 0xff302e30;
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
    private static final int BOARD_MAX_WIDTH = 840;
    private static final int BACK_WIDTH = 48;
    public static final int PAGE_PAD = 14;


    private MmmUi()
    {
    }

    public static void ensureCursorVisible()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.mouse != null)
        {
            client.mouse.unlockCursor();
        }
    }

    public static boolean shouldPauseGame()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.isInSingleplayer();
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

    public static void backdrop(DrawContext context, int width, int height)
    {
        context.fill(0, 0, width, height, menuSurface(OVERLAY));
    }

    public static int pagePad(int screenWidth)
    {
        return screenWidth < 720 ? 6 : screenWidth < 900 ? 10 : PAGE_PAD;
    }

    public static int contentWidth(int screenWidth)
    {
        return BoardEditorStyle.contentWidth(screenWidth, BOARD_MAX_WIDTH);
    }

    public static int contentLeft(int screenWidth)
    {
        return BoardEditorStyle.contentX(screenWidth, BOARD_MAX_WIDTH);
    }

    public static int centerContentX(int screenWidth, int contentWidth)
    {
        return Math.max(0, (screenWidth - contentWidth) / 2);
    }

    public static ButtonWidget backButton(Screen screen, int left)
    {
        return ButtonWidget.builder(Text.literal("Back"), button -> screen.close())
                .dimensions(screen.width - left - BACK_WIDTH, 6, BACK_WIDTH, 19).build();
    }

    public static void toolBoard(DrawContext context, TextRenderer renderer, int width, int height, int left, String section)
    {
        context.fill(0, TOP_BAR_HEIGHT, width, height, menuSurface(CollectionBoardArt.BASE));
        toolHeader(context, renderer, width, left, section);
    }

    public static void toolHeader(DrawContext context, TextRenderer renderer, int width, int left, String section)
    {
        drawBoardHeader(context, renderer, width, section, left, width - left - BACK_WIDTH - 6);
    }

    static void drawBoardHeader(DrawContext context, TextRenderer renderer, int width, String section, int left)
    {
        drawBoardHeader(context, renderer, width, section, left, width - left);
    }

    private static void drawBoardHeader(DrawContext context, TextRenderer renderer, int width, String section, int left, int right)
    {
        int bottom = BoardEditorStyle.HEADER_HEIGHT;
        context.fill(0, 0, width, bottom, menuSurface(BoardEditorStyle.FLOOR));
        context.fill(0, bottom - 1, width, bottom, BoardEditorStyle.LINE);
        drawTextWithin(context, renderer, "MMM", left + 2, 13, width - left - 4, BoardEditorStyle.ROSE, false);
        int x = left + renderer.getWidth("MMM") + 8;
        if (!section.isEmpty()) drawTextWithin(context, renderer, "/ " + section, x, 13, right - x,
                BoardEditorStyle.TEXT, false);
    }

    public static void drawSectionHeading(DrawContext context, TextRenderer renderer, String title, int x, int y, int maxWidth)
    {
        drawTextWithin(context, renderer, title, x, y, maxWidth, TEXT, false);
    }

    public static void card(DrawContext context, int x, int y, int width, int height, int fillColor, int borderColor)
    {
        BoardEditorStyle.plate(context::fill, x, y, width, height, menuSurface(fillColor), borderColor);
    }

    public static void boardRow(DrawContext context, int x, int y, int w, int h, boolean hovered, boolean selected)
    {
        if (w <= 0 || h <= 0) return;
        context.fill(x, y, x + w, y + h - 1, menuSurface(selected ? CollectionBoardArt.ACTIVE
                : hovered ? BoardEditorStyle.TILE : BoardEditorStyle.SURFACE));
        context.fill(x, y + h - 1, x + w, y + h, BoardEditorStyle.LINE);
        if (selected || hovered) context.fill(x, y + 1, x + 2, y + h - 1,
                selected ? BoardEditorStyle.ROSE : BoardEditorStyle.MUTED);
    }

    public static void fieldShell(DrawContext context, int x, int y, int width, int height, boolean focused)
    {
        card(context, x, y, width, height, INSET, focused ? accent() : BORDER_SOFT);
    }

    public static void pill(DrawContext context, TextRenderer renderer, int x, int y, int width, int height, String text)
    {
        card(context, x, y, width, height, CARD, accent());
        String clipped = truncate(renderer, text, width - 8);
        int textX = x + Math.max(4, (width - renderer.getWidth(clipped)) / 2);
        context.drawText(renderer, Text.literal(clipped), textX, y + 4, accent(), false);
    }

    public static void statusChip(DrawContext context, TextRenderer renderer, int x, int y, String text, int borderColor)
    {
        int width = renderer.getWidth(text) + 14;
        card(context, x, y, width, 16, INSET, borderColor);
        drawTextWithin(context, renderer, text, x + 7, y + 4, width - 14, TEXT, false);
    }

    public static void wrappedText(DrawContext context, TextRenderer renderer, String text, int x, int y, int maxWidth, int color)
    {
        List<OrderedText> lines = renderer.wrapLines(Text.literal(text).setStyle(Style.EMPTY), maxWidth);
        int lineY = y;
        for (OrderedText line : lines)
        {
            context.drawText(renderer, line, x, lineY, color, false);
            lineY += 10;
        }
    }

    public static void drawTextWithin(DrawContext context, TextRenderer renderer, String value, int x, int y, int maxWidth, int color, boolean shadow)
    {
        if (maxWidth <= 0)
        {
            return;
        }

        context.drawText(renderer, Text.literal(truncate(renderer, value, maxWidth)), x, y, color, shadow);
    }

    public static void drawTextRightWithin(DrawContext context, TextRenderer renderer, String value, int rightX, int y, int maxWidth, int color, boolean shadow)
    {
        if (maxWidth <= 0)
        {
            return;
        }

        String clipped = truncate(renderer, value, maxWidth);
        context.drawText(renderer, Text.literal(clipped), rightX - renderer.getWidth(clipped), y, color, shadow);
    }

    public static String truncate(TextRenderer renderer, String value, int maxWidth)
    {
        if (value == null)
        {
            return "";
        }
        if (maxWidth <= 0)
        {
            return "";
        }
        if (renderer.getWidth(value) <= maxWidth)
        {
            return value;
        }

        String ellipsis = "...";
        if (renderer.getWidth(ellipsis) > maxWidth)
        {
            return "";
        }

        String trimmed = value;
        while (trimmed.length() > 1 && renderer.getWidth(trimmed + ellipsis) > maxWidth)
        {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (renderer.getWidth(trimmed + ellipsis) > maxWidth)
        {
            return ellipsis;
        }
        return trimmed + ellipsis;
    }

    public static void openTool(Screen parent, ToolDirectory tool)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen destination = switch (tool)
        {
            case HOTKEYS -> new GuiConfigs(parent);
            case PROJECTS -> new ProjectManagerScreen(parent);
            case PROFILE -> new PlayerProfileScreen(parent);
            case HISTORY -> new SessionHistoryScreen(parent);
            case SUMMARY -> new SummaryScreen(MiningStats.getCurrentSession(), parent);
            case SCOREBOARD -> new ScoreboardScreen(parent);
            case LEADERBOARDS -> new LeaderboardScreen(parent);
        };
        client.setScreen(destination);
    }
}
