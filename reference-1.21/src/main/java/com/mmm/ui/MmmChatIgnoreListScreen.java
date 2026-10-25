package com.mmm.ui;

import com.mmm.social.MmmChatIgnoreList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class MmmChatIgnoreListScreen extends Screen
{
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_GAP = 4;
    private static final int FIELD_HEIGHT = 20;
    private final Screen parent;
    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private TextFieldWidget usernameField;
    private int scrollOffset;
    private String status = "Messages and milestones from ignored players stay hidden.";
    private boolean statusError;

    public MmmChatIgnoreListScreen(Screen parent)
    {
        super(Text.literal("MMM Chat Ignore List"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.clearChildren();
        this.usernameField = new TextFieldWidget(this.textRenderer, 0, 0, 180, FIELD_HEIGHT, Text.literal("Player"));
        this.usernameField.setDrawsBackground(false);
        this.usernameField.setEditableColor(MmmUi.TEXT);
        this.usernameField.setMaxLength(16);
        this.usernameField.setPlaceholder(Text.literal("Minecraft username"));
        this.addDrawableChild(this.usernameField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        this.clickTargets.clear();
        Layout layout = layout();
        List<String> ignored = MmmChatIgnoreList.entries();
        MmmUi.backdrop(context, this.width, this.height);
        MmmUi.drawMmmScreensSidebar(context, this.textRenderer, this.width, this.height, mouseX, mouseY, "SETTINGS");
        MmmUi.card(context, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), MmmUi.PANEL, MmmUi.BORDER);
        MmmUi.drawSectionHeading(context, this.textRenderer, "IGNORED PLAYERS", layout.contentX(), layout.contentY(), layout.contentWidth());
        MmmUi.drawTextWithin(context, this.textRenderer, "Hide MMM chat and milestone messages from selected Minecraft players.",
                layout.contentX(), layout.contentY() + 18, layout.contentWidth(), MmmUi.MUTED, false);

        int addWidth = Math.min(70, Math.max(52, layout.contentWidth() / 5));
        int fieldWidth = Math.max(50, layout.contentWidth() - addWidth - 6);
        MmmUi.card(context, layout.contentX(), layout.inputY(), fieldWidth, FIELD_HEIGHT, MmmUi.INSET,
                this.usernameField.isFocused() ? MmmUi.accent() : MmmUi.BORDER_SOFT);
        this.usernameField.setX(layout.contentX() + 6);
        this.usernameField.setY(layout.inputY() + 6);
        this.usernameField.setWidth(Math.max(24, fieldWidth - 12));
        drawButton(context, layout.contentX() + fieldWidth + 6, layout.inputY(), addWidth, FIELD_HEIGHT,
                "IGNORE", mouseX, mouseY, this::addPlayer);

        int visibleRows = visibleRows(layout);
        int maxScroll = Math.max(0, ignored.size() - visibleRows);
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));
        if (ignored.isEmpty())
        {
            MmmUi.card(context, layout.contentX(), layout.listTop(), layout.contentWidth(), 38, MmmUi.CARD, MmmUi.BORDER_SOFT);
            MmmUi.drawTextWithin(context, this.textRenderer, "No players are ignored.", layout.contentX() + 10,
                    layout.listTop() + 12, layout.contentWidth() - 20, MmmUi.MUTED, false);
        }
        else
        {
            int end = Math.min(ignored.size(), this.scrollOffset + visibleRows);
            int rowY = layout.listTop();
            for (int index = this.scrollOffset; index < end; index++)
            {
                drawPlayerRow(context, ignored.get(index), layout.contentX(), rowY, layout.contentWidth(), mouseX, mouseY);
                rowY += ROW_HEIGHT + ROW_GAP;
            }
        }

        MmmUi.drawTextWithin(context, this.textRenderer, this.status, layout.contentX(), layout.statusY(),
                Math.max(1, layout.contentWidth() - 72), this.statusError ? MmmUi.ERROR : MmmUi.MUTED, false);
        drawButton(context, layout.panelX() + layout.panelWidth() - 70, layout.statusY() - 6,
                58, FIELD_HEIGHT, "DONE", mouseX, mouseY, this::close);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawPlayerRow(DrawContext context, String username, int x, int y, int width, int mouseX, int mouseY)
    {
        MmmUi.card(context, x, y, width, ROW_HEIGHT, MmmUi.CARD, MmmUi.BORDER_SOFT);
        MmmUi.drawTextWithin(context, this.textRenderer, username, x + 9, y + 10, width - 52, MmmUi.TEXT, false);
        drawButton(context, x + width - 31, y + 4, 22, 20, "X", mouseX, mouseY, () -> removePlayer(username));
    }

    private void addPlayer()
    {
        String username = this.usernameField == null ? "" : this.usernameField.getText().trim();
        if (!MmmChatIgnoreList.isValidUsername(username))
        {
            setStatus("Enter a valid Minecraft username.", true);
            return;
        }
        if (!MmmChatIgnoreList.add(username))
        {
            setStatus(username + " is already ignored.", true);
            return;
        }
        this.usernameField.setText("");
        this.usernameField.setFocused(true);
        this.scrollOffset = Math.max(0, MmmChatIgnoreList.entries().size() - visibleRows(layout()));
        setStatus("Ignoring messages from " + username + ".", false);
    }

    private void removePlayer(String username)
    {
        if (MmmChatIgnoreList.remove(username))
        {
            setStatus("Showing messages from " + username + " again.", false);
        }
    }

    private void setStatus(String value, boolean error)
    {
        this.status = value;
        this.statusError = error;
    }

    private void drawButton(DrawContext context, int x, int y, int width, int height, String label,
                            int mouseX, int mouseY, Runnable action)
    {
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        context.fill(x, y, x + width, y + height, hovered ? MmmUi.accentHover() : MmmUi.INSET);
        context.drawBorder(x, y, width, height, hovered ? MmmUi.accent() : MmmUi.BORDER_SOFT);
        int textX = x + Math.max(3, (width - this.textRenderer.getWidth(label)) / 2);
        context.drawText(this.textRenderer, Text.literal(label), textX, y + 6, hovered ? MmmUi.TEXT : MmmUi.MUTED, false);
        this.clickTargets.add(new ClickTarget(x, y, width, height, action));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && MmmUi.handleMmmScreensSidebarClick(this, this.parent, mouseX, mouseY, "SETTINGS"))
        {
            return true;
        }
        if (button == 0)
        {
            for (ClickTarget target : List.copyOf(this.clickTargets))
            {
                if (target.contains(mouseX, mouseY))
                {
                    target.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        Layout layout = layout();
        if (mouseY >= layout.listTop() && mouseY < layout.statusY())
        {
            int maxScroll = Math.max(0, MmmChatIgnoreList.entries().size() - visibleRows(layout));
            this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
        {
            addPlayer();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE)
        {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    private int visibleRows(Layout layout)
    {
        return Math.max(1, (layout.statusY() - layout.listTop() - 6) / (ROW_HEIGHT + ROW_GAP));
    }

    private Layout layout()
    {
        int availableWidth = Math.max(1, MmmUi.contentWidth(this.width) - 12);
        int panelWidth = Math.min(620, availableWidth);
        int panelX = MmmUi.centerContentX(this.width, panelWidth);
        int panelY = MmmUi.TOP_BAR_HEIGHT + 8;
        int panelHeight = Math.max(1, this.height - panelY - 8);
        int contentX = panelX + 12;
        int contentY = panelY + 12;
        int contentWidth = Math.max(1, panelWidth - 24);
        int inputY = contentY + 42;
        int listTop = inputY + FIELD_HEIGHT + 10;
        int statusY = panelY + panelHeight - 22;
        return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, contentY, contentWidth, inputY, listTop, statusY);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight, int contentX, int contentY,
                          int contentWidth, int inputY, int listTop, int statusY)
    {
    }

    private record ClickTarget(int x, int y, int width, int height, Runnable action)
    {
        private boolean contains(double mouseX, double mouseY)
        {
            return MmmChatIgnoreListScreen.contains(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }
}
