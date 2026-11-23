package com.mmm.ui;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class MmmUi
{
    public static final int BLACK = 0xFF050505;
    public static final int RED = 0xFFE00000;
    public static final int OVERLAY = 0xFF050505;
    public static final int PANEL = 0xFF050505;
    public static final int CARD = 0xF20D0D0D;
    public static final int CARD_SOFT = CARD;
    public static final int INSET = 0xFF121212;
    public static final int BORDER = 0xFF1F1F1F;
    public static final int BORDER_SOFT = 0xFF272727;
    public static final int ACCENT = RED;
    public static final int ACCENT_BRIGHT = RED;
    public static final int ACCENT_SOFT = 0x33E00000;
    public static final int ACCENT_ROW = 0x3DE00000;
    public static final int TEXT = 0xFFF6F3EF;
    public static final int LABEL = 0xD8C9CDD5;
    public static final int MUTED = 0x9A828893;
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

    public static int contentLeft(int width) { return BoardEditorStyle.contentX(width, 840); }
    public static int contentWidth(int width) { return BoardEditorStyle.contentWidth(width, 840); }
    public static int centerContentX(int width, int contentWidth) { return BoardEditorStyle.contentX(width, contentWidth); }

    /** A tool section is open on the board; only its leading rule is painted. */
    public static void editorSection(DrawContext c, int x, int y, int w) {
        c.fill(x, y, x + w, y + 1, BoardEditorStyle.LINE);
    }

    public static void editorRow(DrawContext c, int x, int y, int w, int h, int fill, int edge) {
        c.fill(x, y, x + w, y + h, menuSurface(fill));
        c.fill(x, y + h - 1, x + w, y + h, edge);
    }

    public static void editorHeading(DrawContext c, TextRenderer font, String title, int x, int y, int w) {
        drawTextWithin(c, font, title, x, y, w, BoardEditorStyle.ROSE, false);
    }

    public static void dossier(DrawContext c, int x, int y, int w, int h) {
        CollectionBoardArt.dossier(new BoardPlatform.Canvas(c), x, y, w, h);
    }

    public static void editorField(DrawContext c, int x, int y, int w, int h, boolean focused) {
        editorRow(c, x, y, w, h, BoardEditorStyle.TILE, focused ? BoardEditorStyle.ROSE : BoardEditorStyle.LINE);
    }

    public static int editorButton(DrawContext c, int x, int y, int w, int h, boolean active, boolean hot) {
        return BoardChrome.buttonFace(new BoardPlatform.Canvas(c), x, y, w, h, active, hot);
    }

    public static void editorFrame(DrawContext context, int width, int height, String section) {
        BoardChrome.header(new BoardPlatform.Canvas(context), width, height, section);
    }

    public static void drawSectionHeading(DrawContext context, TextRenderer renderer, String title, int x, int y, int maxWidth)
    {
        int textHeight = renderer.fontHeight;
        context.fill(x, y, x + 4, y + textHeight, accent());
        drawTextWithin(context, renderer, title, x + 12, y, maxWidth - 12, TEXT, false);
    }

    public static void card(DrawContext context, int x, int y, int width, int height, int fillColor, int borderColor)
    {
        context.fill(x, y, x + width, y + height, menuSurface(fillColor));
        context.drawBorder(x, y, width, height, borderColor);
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

    static final Map<ToolDirectory, Function<Screen, Screen>> TOOL_SCREENS = Map.of(
            ToolDirectory.HOTKEYS, GuiConfigs::new,
            ToolDirectory.PROJECTS, ProjectManagerScreen::new,
            ToolDirectory.PROFILE, PlayerProfileScreen::new,
            ToolDirectory.HISTORY, SessionHistoryScreen::new,
            ToolDirectory.SUMMARY, parent -> new SummaryScreen(MiningStats.getCurrentSession(), parent),
            ToolDirectory.SCOREBOARD, ScoreboardScreen::new);

    public static void positionWidget(ClickableWidget widget, int width, int x, int y)
    {
        widget.setWidth(width);
        widget.setX(x);
        widget.setY(y);
    }

}
