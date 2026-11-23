package com.mmm.ui;

import com.mmm.sync.SyncScoreboardSelector;
import com.mmm.util.UiFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class SyncScoreboardScreen extends CompatScreen
{
    private static final int ROW_HEIGHT = 42;
    private static final int ROW_GAP = 5;
    private final Screen parent;
    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private List<SyncScoreboardSelector.ObjectiveChoice> choices = List.of();
    private int scrollOffset;

    public SyncScoreboardScreen(Screen parent)
    {
        super(Text.literal("Sync Scoreboard"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.choices = SyncScoreboardSelector.availableObjectives(MinecraftClient.getInstance());
        this.addDrawableChild(MmmUi.backButton(this, layout().contentX() + layout().contentWidth()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        this.clickTargets.clear();
        Layout layout = layout();
        MmmUi.backdrop(context, this.width, this.height);
        MmmUi.boardHeader(context, textRenderer, width, layout.contentX(), getTitle().getString());
        MmmUi.drawTextWithin(context, this.textRenderer,
                "Choose the mining-total objective for this source. Project and unrelated objectives are hidden.",
                layout.contentX(), layout.contentY(), layout.contentWidth(), BoardEditorStyle.MUTED, false);

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
                layout.contentWidth(), selectedAvailable ? BoardEditorStyle.MUTED : MmmUi.WARNING, false);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawChoice(DrawContext context, String title, String subtitle, String objectiveName,
                            int playerCount, long sourceTotal, boolean selected,
                            int x, int y, int width, int mouseX, int mouseY)
    {
        boolean hovered = contains(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        int fill = selected ? MmmUi.accentSoft() : hovered ? MmmUi.accentHover() : MmmUi.CARD;
        int border = selected || hovered ? MmmUi.accent() : MmmUi.BORDER_SOFT;
        MmmUi.boardRow(context, x, y, width, ROW_HEIGHT, fill, border);
        int infoWidth = playerCount > 0 || sourceTotal > 0L ? Math.min(width / 3, 152) : 0;
        if (selected) context.fill(x, y, x + 2, y + ROW_HEIGHT, BoardEditorStyle.ROSE);
        MmmUi.drawTextWithin(context, this.textRenderer, title, x + 10, y + 7, Math.max(1, width - infoWidth - 20), BoardEditorStyle.TEXT, false);
        MmmUi.drawTextWithin(context, this.textRenderer, subtitle, x + 10, y + 23, Math.max(1, width - infoWidth - 20), BoardEditorStyle.MUTED, false);
        if (playerCount > 0 || sourceTotal > 0L)
        {
            String summary = playerCount + " players  /  " + UiFormat.formatCompact(sourceTotal);
            MmmUi.drawTextRightWithin(context, this.textRenderer, summary, x + width - 10, y + 15, infoWidth, BoardEditorStyle.MUTED, false);
        }
        this.clickTargets.add(new ClickTarget(x, y, width, ROW_HEIGHT, () -> {
            SyncScoreboardSelector.selectObjective(objectiveName);
            this.choices = SyncScoreboardSelector.availableObjectives(MinecraftClient.getInstance());
        }));
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        Layout layout = layout();
        if (mouseY >= layout.listTop() && mouseY < layout.statusY())
        {
            this.scrollOffset = Math.max(0, this.scrollOffset - (int) Math.signum(verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta)
    {
    }

    private Layout layout()
    {
        int contentWidth = BoardEditorStyle.contentWidth(width, 640);
        int contentX = BoardEditorStyle.contentX(width, 640);
        int contentY = BoardEditorStyle.HEADER_HEIGHT + 10;
        return new Layout(contentX, contentY, contentWidth, contentY + 20, height - 19);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Layout(int contentX, int contentY, int contentWidth, int listTop, int statusY)
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
