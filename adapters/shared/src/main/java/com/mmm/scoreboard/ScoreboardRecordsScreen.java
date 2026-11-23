package com.mmm.scoreboard;

import com.mmm.ui.BoardEditorStyle;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.mmm.ui.MmmUi;

import com.mmm.util.MmmMessages;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ScoreboardRecordsScreen extends Screen
{
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final int ROW_HEIGHT = 42;

    private final Screen parent;
    private final List<RecordButtons> rowButtons = new ArrayList<>();
    private ButtonWidget recordButton;
    private ButtonWidget exportButton;
    private ButtonWidget closeButton;
    private double scrollY;
    private int contentHeight;

    public ScoreboardRecordsScreen(Screen parent)
    {
        super(Text.literal("Recorded Scoreboards"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.clearChildren();
        this.closeButton = this.addSelectableChild(MmmUi.backButton(this, MmmUi.contentLeft(this.width)));
        this.rowButtons.clear();
        this.recordButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Record Current"), ignored -> this.recordCurrent())
                .dimensions(0, 0, 120, 18).build());
        this.exportButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Export All"), ignored -> this.exportAll())
                .dimensions(0, 0, 100, 18).build());

        List<ScoreboardState.Snapshot> snapshots = ScoreboardState.getSnapshots();
        for (int index = 0; index < snapshots.size(); index++)
        {
            int capturedIndex = index;
            ButtonWidget up = this.addDrawableChild(ButtonWidget.builder(Text.literal("Up"), ignored -> this.move(capturedIndex, -1))
                    .dimensions(0, 0, 42, 18).build());
            ButtonWidget down = this.addDrawableChild(ButtonWidget.builder(Text.literal("Down"), ignored -> this.move(capturedIndex, 1))
                    .dimensions(0, 0, 48, 18).build());
            ButtonWidget delete = this.addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), ignored -> this.remove(capturedIndex))
                    .dimensions(0, 0, 58, 18).build());
            up.active = index > 0;
            down.active = index + 1 < snapshots.size();
            this.rowButtons.add(new RecordButtons(up, down, delete));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.toolBoard(context, this.textRenderer, this.width, this.height, MmmUi.contentLeft(this.width), "Scoreboard / Records");

        int x = MmmUi.contentLeft(this.width);
        int y = MmmUi.TOP_BAR_HEIGHT;
        int width = MmmUi.contentWidth(this.width);
        int height = Math.max(1, this.height - y - MmmUi.pagePad(this.width));
        context.enableScissor(x, y, x + width, y + height);
        this.drawContent(context, x, y, width, height);
        super.render(context, mouseX, mouseY, delta);
        context.disableScissor();
        this.closeButton.render(context, mouseX, mouseY, delta);
    }

    private void drawContent(DrawContext context, int x, int viewportY, int width, int viewportHeight)
    {
        List<ScoreboardState.Snapshot> snapshots = ScoreboardState.getSnapshots();
        boolean stacked = width < 320;
        int rowHeight = stacked ? ROW_HEIGHT + 20 : ROW_HEIGHT;
        this.contentHeight = 16 + 20 + 30 + (snapshots.isEmpty() ? 64 : snapshots.size() * rowHeight + 12);
        this.scrollY = Math.clamp(this.scrollY, 0.0D, Math.max(0, this.contentHeight - viewportHeight));
        int y = viewportY + 16 - (int) Math.round(this.scrollY);
        MmmUi.drawTextWithin(context, this.textRenderer, "Saved snapshots: " + snapshots.size(), x, y, width, BoardEditorStyle.TEXT, false);
        y += 20;

        this.recordButton.setDimensionsAndPosition(Math.min(120, width / 3), 18, x, y);
        this.exportButton.setDimensionsAndPosition(Math.min(100, width / 3), 18, x + Math.min(120, width / 3) + 6, y);
        boolean toolbarVisible = y + 18 >= viewportY && y <= viewportY + viewportHeight;
        this.recordButton.visible = toolbarVisible;
        this.exportButton.visible = toolbarVisible;
        y += 30;

        context.fill(x, y - 5, x + width, y - 4, BoardEditorStyle.LINE);
        if (snapshots.isEmpty())
        {
            MmmUi.boardRow(context, x, y, width, 52, false, false);
            MmmUi.drawTextWithin(context, this.textRenderer, "NO RECORDS", x + 12, y + 12, width - 24, MmmUi.TEXT, false);
            MmmUi.drawTextWithin(context, this.textRenderer, "Record the current sidebar to add it here.", x + 12, y + 29, width - 24, BoardEditorStyle.MUTED, false);
            return;
        }

        for (int index = 0; index < snapshots.size(); index++)
        {
            ScoreboardState.Snapshot snapshot = snapshots.get(index);
            MmmUi.boardRow(context, x, y, width, rowHeight, false, false);
            int buttonsWidth = 154;
            int textWidth = stacked ? width - 20 : width - buttonsWidth - 24;
            MmmUi.drawTextWithin(context, this.textRenderer, snapshot.displayName(), x + 10, y + 8,
                    textWidth, MmmUi.TEXT, false);
            String detail = snapshot.rows().size() + " rows  |  " + TIME_FORMAT.format(Instant.ofEpochMilli(snapshot.capturedAtMs()));
            MmmUi.drawTextWithin(context, this.textRenderer, detail, x + 10, y + 22,
                    textWidth, BoardEditorStyle.MUTED, false);

            RecordButtons controls = this.rowButtons.get(index);
            int buttonX = x + width - buttonsWidth - 6;
            int buttonY = y + (stacked ? 38 : 10);
            controls.up().setDimensionsAndPosition(42, 18, buttonX, buttonY);
            controls.down().setDimensionsAndPosition(48, 18, buttonX + 46, buttonY);
            controls.delete().setDimensionsAndPosition(58, 18, buttonX + 98, buttonY);
            boolean visible = y + rowHeight >= viewportY && y <= viewportY + viewportHeight;
            controls.setVisible(visible);
            y += rowHeight;
        }
    }

    private void recordCurrent()
    {
        try
        {
            ScoreboardService.recordCurrent();
            this.clearAndInit();
        }
        catch (IOException exception)
        {
            MmmMessages.actionbar("No scoreboard is available to record");
        }
    }

    private void exportAll()
    {
        try
        {
            MmmMessages.actionbar("Exported records to %s", ScoreboardService.exportRecorded().getFileName().toString());
        }
        catch (IOException exception)
        {
            MmmMessages.actionbar("Record at least one scoreboard before exporting");
        }
    }

    private void move(int index, int direction)
    {
        if (ScoreboardState.moveSnapshot(index, direction))
        {
            this.clearAndInit();
        }
    }

    private void remove(int index)
    {
        if (ScoreboardState.removeSnapshot(index))
        {
            this.clearAndInit();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (mouseY < MmmUi.TOP_BAR_HEIGHT && !this.closeButton.isMouseOver(mouseX, mouseY)) return false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        int viewportHeight = Math.max(1, this.height - MmmUi.TOP_BAR_HEIGHT - MmmUi.pagePad(this.width));
        int maxScroll = Math.max(0, this.contentHeight - viewportHeight);
        this.scrollY = Math.max(0.0D, Math.min(maxScroll, this.scrollY - verticalAmount * 28.0D));
        return true;
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

    private record RecordButtons(ButtonWidget up, ButtonWidget down, ButtonWidget delete)
    {
        private void setVisible(boolean visible)
        {
            this.up.visible = visible;
            this.down.visible = visible;
            this.delete.visible = visible;
        }
    }
}
