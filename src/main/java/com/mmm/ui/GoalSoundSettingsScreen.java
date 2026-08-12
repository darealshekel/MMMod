package com.mmm.ui;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mmm.sound.GoalSoundLibrary;

public class GoalSoundSettingsScreen extends CompatScreen
{
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 8;

    private final Screen parent;
    private final List<Button> thresholdButtons = new ArrayList<>();
    private int selectedThreshold = 25;
    private Button chooseButton;
    private Button previewButton;
    private Button resetButton;
    private Button doneButton;
    private String statusMessage = "Choose a milestone, then select an OGG sound.";
    private boolean statusError;

    public GoalSoundSettingsScreen(Screen parent)
    {
        super(Component.literal("Milestone Sounds"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.clearWidgets();
        this.thresholdButtons.clear();
        Layout layout = this.layout();

        for (int threshold : GoalSoundLibrary.MILESTONES)
        {
            Button button = Button.builder(Component.empty(), ignored -> this.selectThreshold(threshold))
                    .bounds(0, 0, 1, BUTTON_HEIGHT)
                    .build();
            this.thresholdButtons.add(this.addRenderableWidget(button));
        }
        this.chooseButton = this.addRenderableWidget(Button.builder(Component.literal("Choose OGG"), ignored -> this.chooseSound())
                .bounds(0, 0, 1, BUTTON_HEIGHT)
                .build());
        this.previewButton = this.addRenderableWidget(Button.builder(Component.literal("Preview"), ignored -> GoalSoundLibrary.play(this.selectedThreshold))
                .bounds(0, 0, 1, BUTTON_HEIGHT)
                .build());
        this.resetButton = this.addRenderableWidget(Button.builder(Component.literal("Use Default"), ignored -> this.resetSound())
                .bounds(0, 0, 1, BUTTON_HEIGHT)
                .build());
        this.doneButton = this.addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> this.onClose())
                .bounds(0, 0, 1, BUTTON_HEIGHT)
                .build());
        this.updateBounds(layout);
        this.refreshButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        Layout layout = this.layout();
        this.updateBounds(layout);
        MmmUi.backdrop(context, this.width, this.height);
        MmmUi.drawMmmScreensSidebar(context, this.font, this.width, this.height, mouseX, mouseY, "SETTINGS");

        MmmUi.card(context, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), MmmUi.PANEL, MmmUi.BORDER);
        MmmUi.drawSectionHeading(context, this.font, "MILESTONE SOUNDS", layout.contentX(), layout.contentY(), layout.contentWidth());
        MmmUi.drawTextWithin(context, this.font, "Set one custom OGG for each fixed daily-goal milestone.", layout.contentX(), layout.contentY() + 18, layout.contentWidth(), MmmUi.MUTED, false);

        MmmUi.card(context, layout.contentX(), layout.infoY(), layout.contentWidth(), 58, MmmUi.CARD, MmmUi.BORDER_SOFT);
        MmmUi.drawTextWithin(context, this.font, this.selectedThreshold + "% MILESTONE", layout.contentX() + 10, layout.infoY() + 10, layout.contentWidth() - 20, MmmUi.accent(), false);
        MmmUi.drawTextWithin(context, this.font, GoalSoundLibrary.getDisplayName(this.selectedThreshold), layout.contentX() + 10, layout.infoY() + 28, layout.contentWidth() - 20, MmmUi.TEXT, false);
        MmmUi.drawTextWithin(context, this.font, GoalSoundLibrary.hasCustomSound(this.selectedThreshold) ? "Custom sound active" : "Built-in MMM sound active", layout.contentX() + 10, layout.infoY() + 42, layout.contentWidth() - 20, MmmUi.MUTED, false);

        MmmUi.drawTextWithin(context, this.font, this.statusMessage, layout.contentX(), layout.statusY(), layout.contentWidth(), this.statusError ? MmmUi.ERROR : MmmUi.MUTED, false);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && MmmUi.handleMmmScreensSidebarClick(this, this.parent, mouseX, mouseY, "SETTINGS"))
        {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose()
    {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen()
    {
        return MmmUi.shouldPauseGame();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
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
        Minecraft client = Minecraft.getInstance();
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
            this.thresholdButtons.get(index).setMessage(Component.literal(label));
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
            Button button = this.thresholdButtons.get(index);
            button.setX(layout.contentX() + index * (thresholdWidth + GAP));
            button.setY(layout.thresholdY());
            button.setWidth(thresholdWidth);
        }

        int actionWidth = (layout.contentWidth() - GAP * 2) / 3;
        this.chooseButton.setRectangle(actionWidth, BUTTON_HEIGHT, layout.contentX(), layout.actionY());
        this.previewButton.setRectangle(actionWidth, BUTTON_HEIGHT, layout.contentX() + actionWidth + GAP, layout.actionY());
        this.resetButton.setRectangle(actionWidth, BUTTON_HEIGHT, layout.contentX() + (actionWidth + GAP) * 2, layout.actionY());
        this.doneButton.setRectangle(64, BUTTON_HEIGHT, layout.panelX() + layout.panelWidth() - 76, layout.panelY() + 10);
    }

    private Layout layout()
    {
        int availableWidth = Math.max(1, MmmUi.contentWidth(this.width) - 16);
        int panelWidth = Math.min(540, availableWidth);
        int availableHeight = Math.max(1, this.height - MmmUi.TOP_BAR_HEIGHT - 12);
        int panelHeight = Math.min(250, availableHeight);
        int panelX = MmmUi.centerContentX(this.width, panelWidth);
        int panelY = MmmUi.TOP_BAR_HEIGHT + Math.max(6, (availableHeight - panelHeight) / 2);
        int contentX = panelX + 14;
        int contentY = panelY + 14;
        int contentWidth = Math.max(1, panelWidth - 28);
        int thresholdY = contentY + 48;
        int infoY = thresholdY + BUTTON_HEIGHT + 12;
        int actionY = infoY + 70;
        int statusY = actionY + BUTTON_HEIGHT + 12;
        return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, contentY, contentWidth, thresholdY, infoY, actionY, statusY);
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight, int contentX, int contentY,
                          int contentWidth, int thresholdY, int infoY, int actionY, int statusY)
    {
    }
}
