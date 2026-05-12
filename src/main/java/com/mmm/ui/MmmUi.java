package com.mmm.ui;

import java.util.List;

import com.mmm.Reference;
import com.mmm.gui.GuiConfigs;
import com.mmm.hud.SessionHistoryScreen;
import com.mmm.hud.SummaryScreen;
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
    private static final int SIDEBAR_ROW_GAP = 7;

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

    public static void backdrop(DrawContext context, int width, int height)
    {
        context.fill(0, 0, width, height, OVERLAY);
    }

    public static int contentLeft()
    {
        return SIDEBAR_WIDTH + PAGE_PAD;
    }

    public static int contentWidth(int screenWidth)
    {
        return Math.max(1, screenWidth - contentLeft() - PAGE_PAD);
    }

    public static int centerContentX(int screenWidth, int contentWidth)
    {
        return contentLeft() + Math.max(0, (contentWidth(screenWidth) - contentWidth) / 2);
    }

    public static void drawMmmScreensSidebar(DrawContext context, TextRenderer renderer, int width, int height, int mouseX, int mouseY, String activeId)
    {
        drawMmmTopBar(context, renderer, width);
        context.fill(0, TOP_BAR_HEIGHT, SIDEBAR_WIDTH, height, 0xE9080808);
        context.drawBorder(0, TOP_BAR_HEIGHT, SIDEBAR_WIDTH, height - TOP_BAR_HEIGHT, BORDER);

        int titleY = TOP_BAR_HEIGHT + 16;
        drawSectionHeading(context, renderer, "MMM SCREENS", 12, titleY, SIDEBAR_WIDTH - 24);

        int y = titleY + 28;
        for (SidebarRoute route : SidebarRoute.values())
        {
            boolean active = route.id.equals(activeId);
            boolean hovered = mouseX >= 12 && mouseX < SIDEBAR_WIDTH - 12 && mouseY >= y && mouseY < y + SIDEBAR_ROW_HEIGHT;
            int fill = active ? 0x33E00000 : hovered ? 0x22E00000 : INSET;
            int border = active || hovered ? ACCENT : BORDER_SOFT;
            context.fill(12, y, SIDEBAR_WIDTH - 12, y + SIDEBAR_ROW_HEIGHT, fill);
            context.drawBorder(12, y, SIDEBAR_WIDTH - 24, SIDEBAR_ROW_HEIGHT, border);
            drawTextWithin(context, renderer, route.label, 20, y + 8, SIDEBAR_WIDTH - 40, active ? TEXT : MUTED, false);
            y += SIDEBAR_ROW_HEIGHT + SIDEBAR_ROW_GAP;
        }

        int bottomY = height - 42;
        context.drawBorder(12, bottomY, SIDEBAR_WIDTH - 24, 28, BORDER_SOFT);
        drawTextWithin(context, renderer, "MMM MOD", 20, bottomY + 7, SIDEBAR_WIDTH - 40, TEXT, false);
        drawTextWithin(context, renderer, Reference.MOD_VERSION, 20, bottomY + 18, SIDEBAR_WIDTH - 40, MUTED, false);
    }

    public static void drawMmmTopBar(DrawContext context, TextRenderer renderer, int width)
    {
        context.fill(0, 0, width, TOP_BAR_HEIGHT, 0xF0060606);
        context.drawBorder(0, 0, width, TOP_BAR_HEIGHT, BORDER);
        context.fill(14, 12, 18, 30, ACCENT);
        drawTextWithin(context, renderer, "MMM", 26, 10, 40, ACCENT_BRIGHT, false);
        drawTextWithin(context, renderer, "Manual Mining Maniacs", 68, 10, Math.max(0, width / 2 - 80), TEXT, false);
        int versionWidth = renderer.getWidth(Reference.MOD_VERSION);
        drawTextRightWithin(context, renderer, Reference.MOD_VERSION, width - 16, 10, versionWidth, MUTED, false);
    }

    public static boolean handleMmmScreensSidebarClick(Screen current, Screen parent, double mouseX, double mouseY, String activeId)
    {
        if (mouseX < 12 || mouseX >= SIDEBAR_WIDTH - 12)
        {
            return false;
        }

        int y = TOP_BAR_HEIGHT + 44;
        for (SidebarRoute route : SidebarRoute.values())
        {
            if (mouseY >= y && mouseY < y + SIDEBAR_ROW_HEIGHT)
            {
                if (route.id.equals(activeId))
                {
                    return true;
                }
                openSidebarRoute(current, parent, route);
                return true;
            }
            y += SIDEBAR_ROW_HEIGHT + SIDEBAR_ROW_GAP;
        }

        return false;
    }

    public static void drawSectionHeading(DrawContext context, TextRenderer renderer, String title, int x, int y, int maxWidth)
    {
        int textHeight = renderer.fontHeight;
        context.fill(x, y, x + 4, y + textHeight, ACCENT);
        drawTextWithin(context, renderer, title, x + 12, y, maxWidth - 12, TEXT, false);
    }

    public static void card(DrawContext context, int x, int y, int width, int height, int fillColor, int borderColor)
    {
        context.fill(x, y, x + width, y + height, fillColor);
        context.drawBorder(x, y, width, height, borderColor);
    }

    public static void fieldShell(DrawContext context, int x, int y, int width, int height, boolean focused)
    {
        card(context, x, y, width, height, INSET, focused ? ACCENT : BORDER_SOFT);
    }

    public static void pill(DrawContext context, TextRenderer renderer, int x, int y, int width, int height, String text)
    {
        card(context, x, y, width, height, CARD, ACCENT);
        String clipped = truncate(renderer, text, width - 8);
        int textX = x + Math.max(4, (width - renderer.getWidth(clipped)) / 2);
        context.drawText(renderer, Text.literal(clipped), textX, y + 4, ACCENT_BRIGHT, false);
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
            case TOGGLES -> client.setScreen(GuiConfigs.createForTab("TWEAKS", routeParent));
            case HOTKEYS -> client.setScreen(GuiConfigs.createForTab("HOTKEYS", routeParent));
            case PROJECTS -> client.setScreen(new ProjectManagerScreen(routeParent));
            case PROFILE -> client.setScreen(new PlayerProfileScreen(routeParent));
            case WEBSITE_LINK -> client.setScreen(new WebsiteLinkScreen(routeParent));
            case HISTORY -> client.setScreen(new SessionHistoryScreen(routeParent));
            case SUMMARY -> client.setScreen(new SummaryScreen(MiningStats.getCurrentSession(), routeParent));
        }
    }

    private enum SidebarRoute
    {
        SETTINGS("SETTINGS", "Settings"),
        TOGGLES("TOGGLES", "Feature Toggles"),
        HOTKEYS("HOTKEYS", "Hotkeys"),
        PROJECTS("PROJECTS", "Projects"),
        PROFILE("PROFILE", "Profile"),
        WEBSITE_LINK("WEBSITE_LINK", "Website Link"),
        HISTORY("HISTORY", "History"),
        SUMMARY("SUMMARY", "Summary");

        private final String id;
        private final String label;

        SidebarRoute(String id, String label)
        {
            this.id = id;
            this.label = label;
        }
    }
}
