package com.mmm.ui;

import com.mmm.ui.BoardChrome;
import com.mmm.ui.BoardEditorStyle;

import com.mmm.sync.SyncScoreboardSelector;
import com.mmm.util.UiFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import com.mmm.compat.DrawContext;
import com.mmm.compat.MmmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class SyncScoreboardScreen extends MmmScreen implements BoardChrome.Page
{
    private static final int ROW_HEIGHT = 42;
    private static final int ROW_GAP = 5;
    private final Screen parent;
    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private List<SyncScoreboardSelector.ObjectiveChoice> choices = List.of();
    private int scrollOffset;

    public SyncScoreboardScreen(Screen parent)
    {
        super(new net.minecraft.text.LiteralText("Sync Scoreboard"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        this.addDrawableChild(BoardChrome.back(this.width, this::close));
        MmmUi.ensureCursorVisible();
        this.choices = SyncScoreboardSelector.availableObjectives(MinecraftClient.getInstance());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        this.clickTargets.clear();
        Layout layout = layout();
        MmmUi.editorFrame(context, width, height, "Sync scoreboard");
        MmmUi.editorHeading(context, this.textRenderer, "SYNC SCOREBOARD", layout.contentX(), layout.contentY(), layout.contentWidth());
        MmmUi.drawTextWithin(context, this.textRenderer,
                "Choose the mining-total objective for this source. Project and unrelated objectives are hidden.",
                layout.contentX(), layout.contentY() + 18, layout.contentWidth(), BoardEditorStyle.MUTED, false);

        String selected = SyncScoreboardSelector.selectedObjectiveName();
        int rowY = layout.listTop();
        drawChoice(context, "Auto Detect", "MMM chooses the strongest safe mining objective.", "", 0, 0L,
                selected.isBlank(), layout.contentX(), rowY, layout.contentWidth(), mouseX, mouseY);
        rowY += ROW_HEIGHT + ROW_GAP;

        int visibleRows = Math.max(1, (layout.statusY() - rowY) / (ROW_HEIGHT + ROW_GAP));
        int maxScroll = Math.max(0, this.choices.size() - visibleRows);
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));
        int end = Math.min(this.choices.size(), this.scrollOffset + visibleRows);
        for (int index = this.scrollOffset; index < end; index++)
        {
            SyncScoreboardSelector.ObjectiveChoice choice = this.choices.get(index);
            drawChoice(context,
                    choice.displayName().isBlank() ? choice.objectiveName() : choice.displayName(),
                    choice.objectiveName(),
                    choice.objectiveName(),
                    choice.playerCount(),
                    choice.sourceTotal(),
                    choice.objectiveName().equalsIgnoreCase(selected),
                    layout.contentX(), rowY, layout.contentWidth(), mouseX, mouseY);
            rowY += ROW_HEIGHT + ROW_GAP;
        }

        boolean selectedAvailable = selected.isBlank() || this.choices.stream()
                .anyMatch(choice -> choice.objectiveName().equalsIgnoreCase(selected));
        String status = this.choices.isEmpty()
                ? "No safe mining scoreboard is available in the current world."
                : selected.isBlank()
                        ? "Auto Detect is active for this source."
                        : selectedAvailable
                                ? "Selected objective: " + selected
                                : "Selected scoreboard unavailable. Source sync is paused.";
        MmmUi.drawTextWithin(context, this.textRenderer, status, layout.contentX(), layout.statusY(),
                layout.contentWidth() - 70, selectedAvailable ? BoardEditorStyle.MUTED : MmmUi.WARNING, false);
        drawButton(context, layout.panelX() + layout.panelWidth() - 70, layout.statusY() - 6,
                58, 20, "DONE", mouseX, mouseY, this::close);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawChoice(DrawContext context, String title, String subtitle, String objectiveName,
                            int playerCount, long sourceTotal, boolean selected,
                            int x, int y, int width, int mouseX, int mouseY)
    {
        boolean hovered = contains(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        int fill = selected ? MmmUi.accentSoft() : hovered ? BoardEditorStyle.TILE : BoardEditorStyle.TILE;
        int border = selected || hovered ? MmmUi.accent() : BoardEditorStyle.LINE;
        MmmUi.editorRow(context, x, y, width, ROW_HEIGHT, fill, border);
        MmmUi.drawTextWithin(context, this.textRenderer, title, x + 10, y + 7, Math.max(1, width - 190), BoardEditorStyle.TEXT, false);
        MmmUi.drawTextWithin(context, this.textRenderer, subtitle, x + 10, y + 23, Math.max(1, width - 190), BoardEditorStyle.MUTED, false);
        if (playerCount > 0 || sourceTotal > 0L)
        {
            String summary = playerCount + " players  /  " + UiFormat.formatCompact(sourceTotal);
            MmmUi.drawTextRightWithin(context, this.textRenderer, summary, x + width - 10, y + 15, 170, BoardEditorStyle.MUTED, false);
        }
        this.clickTargets.add(new ClickTarget(x, y, width, ROW_HEIGHT, () -> {
            SyncScoreboardSelector.selectObjective(objectiveName);
            this.choices = SyncScoreboardSelector.availableObjectives(MinecraftClient.getInstance());
        }));
    }

    private void drawButton(DrawContext context, int x, int y, int width, int height, String label,
                            int mouseX, int mouseY, Runnable action)
    {
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        MmmUi.editorRow(context, x, y, width, height, hovered ? BoardEditorStyle.TILE : BoardEditorStyle.TILE,
                hovered ? MmmUi.accent() : BoardEditorStyle.LINE);
        context.drawText(this.textRenderer, new net.minecraft.text.LiteralText(label),
                x + Math.max(3, (width - this.textRenderer.getWidth(label)) / 2), y + 6,
                hovered ? BoardEditorStyle.TEXT : BoardEditorStyle.MUTED, false);
        this.clickTargets.add(new ClickTarget(x, y, width, height, action));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
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
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount)
    {
        Layout layout = layout();
        if (mouseY >= layout.listTop() && mouseY < layout.statusY())
        {
            this.scrollOffset = Math.max(0, this.scrollOffset - (int) Math.signum(verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
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
    public void renderBackground(DrawContext context)
    {
    }

    private Layout layout()
    {
        int panelWidth = BoardEditorStyle.contentWidth(width, 680);
        int panelX = BoardEditorStyle.contentX(width, panelWidth);
        int panelY = MmmUi.TOP_BAR_HEIGHT + 8;
        int panelHeight = Math.max(1, this.height - panelY - 8);
        int contentX = panelX + 12;
        int contentY = panelY + 12;
        return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, contentY,
                Math.max(1, panelWidth - 24), contentY + 42, panelY + panelHeight - 22);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int contentX, int contentY, int contentWidth, int listTop, int statusY)
    {
    }

    private record ClickTarget(int x, int y, int width, int height, Runnable action)
    {
        private boolean contains(double mouseX, double mouseY)
        {
            return SyncScoreboardScreen.contains(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }
}
