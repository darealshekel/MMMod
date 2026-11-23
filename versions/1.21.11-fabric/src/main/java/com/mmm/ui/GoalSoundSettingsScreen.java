package com.mmm.ui;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mmm.sound.GoalSoundLibrary;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class GoalSoundSettingsScreen extends CompatScreen
{
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 8;

    private final Screen parent;
    private final List<ButtonWidget> thresholdButtons = new ArrayList<>();
    private int selectedThreshold = 25;
    private ButtonWidget chooseButton;
    private ButtonWidget previewButton;
    private ButtonWidget resetButton;
    private String statusMessage = "Choose a milestone, then select an OGG sound.";
    private boolean statusError;

    public GoalSoundSettingsScreen(Screen parent)
    {
        super(Text.literal("Milestone Sounds"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.clearChildren();
        this.thresholdButtons.clear();
        Layout layout = this.layout();

        for (int threshold : GoalSoundLibrary.MILESTONES)
        {
            ButtonWidget button = ButtonWidget.builder(Text.empty(), ignored -> this.selectThreshold(threshold))
                    .dimensions(0, 0, 1, BUTTON_HEIGHT)
                    .build();
            this.thresholdButtons.add(this.addDrawableChild(button));
        }
        this.chooseButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Choose OGG"), ignored -> this.chooseSound())
                .dimensions(0, 0, 1, BUTTON_HEIGHT)
                .build());
        this.previewButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Preview"), ignored -> GoalSoundLibrary.play(this.selectedThreshold))
                .dimensions(0, 0, 1, BUTTON_HEIGHT)
                .build());
        this.resetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Use Default"), ignored -> this.resetSound())
                .dimensions(0, 0, 1, BUTTON_HEIGHT)
                .build());
        this.addDrawableChild(MmmUi.backButton(this, layout.contentX() + layout.contentWidth()));
        this.updateBounds(layout);
        this.refreshButtons();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        Layout layout = this.layout();
        this.updateBounds(layout);
        MmmUi.backdrop(context, this.width, this.height);
        MmmUi.boardHeader(context, textRenderer, width, layout.contentX(), getTitle().getString());

        MmmUi.drawTextWithin(context, this.textRenderer, "Set one custom OGG for each fixed daily-goal milestone.", layout.contentX(), layout.contentY(), layout.contentWidth(), BoardEditorStyle.MUTED, false);

        MmmUi.boardRow(context, layout.contentX(), layout.infoY(), layout.contentWidth(), 45, MmmUi.CARD, MmmUi.BORDER_SOFT);
        MmmUi.drawTextWithin(context, this.textRenderer, this.selectedThreshold + "% MILESTONE", layout.contentX() + 10, layout.infoY() + 4, layout.contentWidth() - 20, BoardEditorStyle.ROSE, false);
        MmmUi.drawTextWithin(context, this.textRenderer, GoalSoundLibrary.getDisplayName(this.selectedThreshold), layout.contentX() + 10, layout.infoY() + 16, layout.contentWidth() - 20, MmmUi.TEXT, false);
        MmmUi.drawTextWithin(context, this.textRenderer, GoalSoundLibrary.hasCustomSound(this.selectedThreshold) ? "Custom sound active" : "Built-in MMM sound active", layout.contentX() + 10, layout.infoY() + 29, layout.contentWidth() - 20, BoardEditorStyle.MUTED, false);

        MmmUi.drawTextWithin(context, this.textRenderer, this.statusMessage, layout.contentX(), layout.statusY(), layout.contentWidth(), this.statusError ? MmmUi.ERROR : BoardEditorStyle.MUTED, false);
        super.render(context, mouseX, mouseY, delta);
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

    private void selectThreshold(int threshold)
    {
        this.selectedThreshold = threshold;
        this.statusMessage = "Choose a custom OGG or keep the MMM default.";
        this.statusError = false;
        this.refreshButtons();
    }

    private void chooseSound()
    {
        int threshold = this.selectedThreshold;
        this.chooseButton.active = false;
        this.statusMessage = "Waiting for a sound file...";
        this.statusError = false;

        EventQueue.invokeLater(() -> {
            Path selected = null;
            try
            {
                FileDialog dialog = new FileDialog((Frame) null, "Choose an OGG sound for " + threshold + "%", FileDialog.LOAD);
                dialog.setFile("*.ogg");
                dialog.setFilenameFilter((directory, name) -> name != null && name.toLowerCase().endsWith(".ogg"));
                dialog.setVisible(true);
                if (dialog.getDirectory() != null && dialog.getFile() != null)
                {
                    selected = new File(dialog.getDirectory(), dialog.getFile()).toPath();
                }
                dialog.dispose();
            }
            catch (RuntimeException exception)
            {
                this.finishSelection(threshold, null, "Could not open the file picker: " + exception.getMessage(), true);
                return;
            }

            if (selected == null)
            {
                this.finishSelection(threshold, null, "No sound was selected.", false);
                return;
            }
            this.finishSelection(threshold, selected, null, false);
        });
    }

    private void finishSelection(int threshold, Path selected, String immediateMessage, boolean immediateError)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null)
        {
            return;
        }
        client.execute(() -> {
            this.chooseButton.active = true;
            if (immediateMessage != null)
            {
                this.statusMessage = immediateMessage;
                this.statusError = immediateError;
                return;
            }
            try
            {
                GoalSoundLibrary.install(threshold, selected);
                this.selectedThreshold = threshold;
                this.statusMessage = "Custom sound saved for " + threshold + "% and shared across MMM versions.";
                this.statusError = false;
                this.refreshButtons();
                GoalSoundLibrary.play(threshold);
            }
            catch (IOException | RuntimeException exception)
            {
                this.statusMessage = exception.getMessage() == null ? "Could not save that sound." : exception.getMessage();
                this.statusError = true;
            }
        });
    }

    private void resetSound()
    {
        try
        {
            GoalSoundLibrary.reset(this.selectedThreshold);
            this.statusMessage = "The " + this.selectedThreshold + "% milestone now uses the MMM default.";
            this.statusError = false;
            this.refreshButtons();
        }
        catch (IOException exception)
        {
            this.statusMessage = "Could not remove the custom sound: " + exception.getMessage();
            this.statusError = true;
        }
    }

    private void refreshButtons()
    {
        for (int index = 0; index < this.thresholdButtons.size(); index++)
        {
            int threshold = GoalSoundLibrary.MILESTONES.get(index);
            String label = threshold == this.selectedThreshold ? "[ " + threshold + "% ]" : threshold + "%";
            this.thresholdButtons.get(index).setMessage(Text.literal(label));
        }
        if (this.resetButton != null)
        {
            this.resetButton.active = GoalSoundLibrary.hasCustomSound(this.selectedThreshold);
        }
    }

    private void updateBounds(Layout layout)
    {
        int thresholdWidth = (layout.contentWidth() - GAP * 3) / 4;
        for (int index = 0; index < this.thresholdButtons.size(); index++)
        {
            ButtonWidget button = this.thresholdButtons.get(index);
            button.setX(layout.contentX() + index * (thresholdWidth + GAP));
            button.setY(layout.thresholdY());
            button.setWidth(thresholdWidth);
        }

        int actionWidth = (layout.contentWidth() - GAP * 2) / 3;
        this.chooseButton.setDimensionsAndPosition(actionWidth, BUTTON_HEIGHT, layout.contentX(), layout.actionY());
        this.previewButton.setDimensionsAndPosition(actionWidth, BUTTON_HEIGHT, layout.contentX() + actionWidth + GAP, layout.actionY());
        this.resetButton.setDimensionsAndPosition(actionWidth, BUTTON_HEIGHT, layout.contentX() + (actionWidth + GAP) * 2, layout.actionY());
    }

    private Layout layout()
    {
        int contentX = BoardEditorStyle.contentX(width, 520);
        int contentWidth = BoardEditorStyle.contentWidth(width, 520);
        int contentY = BoardEditorStyle.HEADER_HEIGHT + 9;
        int thresholdY = contentY + 17;
        int infoY = thresholdY + BUTTON_HEIGHT + 5;
        int actionY = infoY + 49;
        return new Layout(contentX, contentY, contentWidth, thresholdY, infoY, actionY, actionY + 24);
    }

    private record Layout(int contentX, int contentY, int contentWidth, int thresholdY, int infoY, int actionY, int statusY)
    {
    }
}
