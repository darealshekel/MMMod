package com.mmm.ui;

import java.util.List;

import com.mmm.Reference;
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
    public static final int TOP_BAR_HEIGHT = 42;
    public static final int SIDEBAR_WIDTH = 150;
    public static final int PAGE_PAD = 14;

    private static final int SIDEBAR_ROW_HEIGHT = 24;

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

    public static int contentLeft()
    {
        return SIDEBAR_WIDTH + PAGE_PAD;
    }

    public static int sidebarWidth(int screenWidth)
    {
        if (screenWidth < 520)
        {
            return 84;
        }
        if (screenWidth < 720)
        {
            return 104;
        }
        if (screenWidth < 900)
        {
            return 128;
        }
        return SIDEBAR_WIDTH;
    }

    public static int pagePad(int screenWidth)
    {
        return screenWidth < 720 ? 6 : screenWidth < 900 ? 10 : PAGE_PAD;
    }

    public static int contentLeft(int screenWidth)
    {
        return sidebarWidth(screenWidth) + pagePad(screenWidth);
    }

    public static int contentWidth(int screenWidth)
    {
        return Math.max(1, screenWidth - contentLeft(screenWidth) - pagePad(screenWidth));
    }

    public static int centerContentX(int screenWidth, int contentWidth)
    {
        return contentLeft(screenWidth) + Math.max(0, (contentWidth(screenWidth) - contentWidth) / 2);
    }

    public static int sidebarStartY(int screenHeight)
    {
        return sidebarMetrics(screenHeight).startY();
    }

    public static int sidebarRowHeight(int screenHeight)
    {
        return sidebarMetrics(screenHeight).rowHeight();
    }

    public static int sidebarRowGap(int screenHeight)
    {
        return sidebarMetrics(screenHeight).rowGap();
    }

    public static boolean sidebarFooterVisible(int screenHeight)
    {
        return sidebarMetrics(screenHeight).showFooter();
    }

    public static void drawMmmScreensSidebar(DrawContext context, TextRenderer renderer, int width, int height, int mouseX, int mouseY, String activeId)
    {
        drawMmmTopBar(context, renderer, width);
        int sidebarWidth = sidebarWidth(width);
        int sidePad = sidebarWidth < 120 ? 8 : 12;
        SidebarMetrics metrics = sidebarMetrics(height);
        context.fill(0, TOP_BAR_HEIGHT, sidebarWidth, height, menuSurface(0xE9080808));
        context.drawBorder(0, TOP_BAR_HEIGHT, sidebarWidth, Math.max(1, height - TOP_BAR_HEIGHT), BORDER);

        int titleY = metrics.titleY();
        drawSectionHeading(context, renderer, "MMM SCREENS", sidePad, titleY, sidebarWidth - sidePad * 2);

        int y = metrics.startY();
        for (SidebarRoute route : SidebarRoute.values())
        {
            boolean active = route.id.equals(activeId);
            boolean hovered = mouseX >= sidePad && mouseX < sidebarWidth - sidePad && mouseY >= y && mouseY < y + metrics.rowHeight();
            int fill = active ? accentSoft() : hovered ? accentHover() : INSET;
            int border = active || hovered ? accent() : BORDER_SOFT;
            context.fill(sidePad, y, sidebarWidth - sidePad, y + metrics.rowHeight(), menuSurface(fill));
            context.drawBorder(sidePad, y, sidebarWidth - sidePad * 2, metrics.rowHeight(), border);
            int textY = y + Math.max(2, (metrics.rowHeight() - 8) / 2);
            String label = sidebarWidth < 120 ? route.compactLabel : route.label;
            drawTextWithin(context, renderer, label, sidePad + 8, textY, sidebarWidth - sidePad * 2 - 16, active ? TEXT : MUTED, false);
            y += metrics.rowHeight() + metrics.rowGap();
        }

        if (metrics.showFooter())
        {
            int bottomY = height - 42;
            context.drawBorder(sidePad, bottomY, sidebarWidth - sidePad * 2, 28, BORDER_SOFT);
            drawTextWithin(context, renderer, "MMM MOD", sidePad + 8, bottomY + 7, sidebarWidth - sidePad * 2 - 16, TEXT, false);
            drawTextWithin(context, renderer, Reference.MOD_VERSION, sidePad + 8, bottomY + 18, sidebarWidth - sidePad * 2 - 16, MUTED, false);
        }
    }

    public static void drawMmmTopBar(DrawContext context, TextRenderer renderer, int width)
    {
        context.fill(0, 0, width, TOP_BAR_HEIGHT, menuSurface(0xF0060606));
        context.drawBorder(0, 0, width, TOP_BAR_HEIGHT, BORDER);
        context.fill(14, 12, 18, 30, accent());
        drawTextWithin(context, renderer, "MMM", 26, 10, 40, accent(), false);
        int versionWidth = renderer.getWidth(Reference.MOD_VERSION);
        int versionSpace = width >= 300 ? versionWidth + 24 : 0;
        int brandWidth = Math.max(0, width - 68 - versionSpace - 12);
        if (brandWidth >= 72)
        {
            drawTextWithin(context, renderer, "Manual Mining Maniacs", 68, 10, brandWidth, TEXT, false);
        }
        if (versionSpace > 0)
        {
            drawTextRightWithin(context, renderer, Reference.MOD_VERSION, width - 16, 10, versionWidth, MUTED, false);
        }
    }

    public static boolean handleMmmScreensSidebarClick(Screen current, Screen parent, double mouseX, double mouseY, String activeId)
    {
        int sidebarWidth = sidebarWidth(current.width);
        int sidePad = sidebarWidth < 120 ? 8 : 12;
        SidebarMetrics metrics = sidebarMetrics(current.height);
        if (mouseX < sidePad || mouseX >= sidebarWidth - sidePad)
        {
            return false;
        }

        int y = metrics.startY();
        for (SidebarRoute route : SidebarRoute.values())
        {
            if (mouseY >= y && mouseY < y + metrics.rowHeight())
            {
                if (route.id.equals(activeId))
                {
                    return true;
                }
                openSidebarRoute(current, parent, route);
                return true;
            }
            y += metrics.rowHeight() + metrics.rowGap();
        }

        return false;
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

    private static void openSidebarRoute(Screen current, Screen parent, SidebarRoute route)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null)
        {
            return;
        }

        Screen routeParent = parent != null ? parent : current;
        switch (route)
        {
            case SETTINGS -> client.setScreen(new MmmSettingsScreen(routeParent));
            case HOTKEYS -> client.setScreen(new GuiConfigs(routeParent));
            case PROJECTS -> client.setScreen(new ProjectManagerScreen(routeParent));
            case PROFILE -> client.setScreen(new PlayerProfileScreen(routeParent));
            case WEBSITE_LINK -> client.setScreen(new WebsiteLinkScreen(routeParent));
            case HISTORY -> client.setScreen(new SessionHistoryScreen(routeParent));
            case SUMMARY -> client.setScreen(new SummaryScreen(MiningStats.getCurrentSession(), routeParent));
            case SCOREBOARD -> client.setScreen(new ScoreboardScreen(routeParent));
        }
    }

    private static SidebarMetrics sidebarMetrics(int screenHeight)
    {
        int routeCount = SidebarRoute.values().length;
        boolean compact = screenHeight < 360;
        int titleY = TOP_BAR_HEIGHT + (compact ? 8 : 16);
        int startY = titleY + (compact ? 18 : 28);
        boolean showFooter = !compact && screenHeight - startY >= routeCount * 24 + 50;
        int available = Math.max(routeCount * 10, screenHeight - startY - (showFooter ? 50 : 8));
        int rowGap = available >= routeCount * 24 + (routeCount - 1) * 7 ? 7
                : available >= routeCount * 18 + (routeCount - 1) * 3 ? 3 : 1;
        int rowHeight = Math.max(10, Math.min(SIDEBAR_ROW_HEIGHT, (available - rowGap * (routeCount - 1)) / routeCount));
        return new SidebarMetrics(titleY, startY, rowHeight, rowGap, showFooter);
    }

    private record SidebarMetrics(int titleY, int startY, int rowHeight, int rowGap, boolean showFooter)
    {
    }

    private enum SidebarRoute
    {
        SETTINGS("SETTINGS", "Settings", "Settings"),
        HOTKEYS("HOTKEYS", "Hotkeys", "Hotkeys"),
        PROJECTS("PROJECTS", "Projects", "Projects"),
        PROFILE("PROFILE", "Profile", "Profile"),
        WEBSITE_LINK("WEBSITE_LINK", "Website Link", "Link"),
        HISTORY("HISTORY", "History", "History"),
        SUMMARY("SUMMARY", "Summary", "Summary"),
        SCOREBOARD("SCOREBOARD", "Scoreboard", "Scores");

        private final String id;
        private final String label;
        private final String compactLabel;

        SidebarRoute(String id, String label, String compactLabel)
        {
            this.id = id;
            this.label = label;
            this.compactLabel = compactLabel;
        }
    }
}
