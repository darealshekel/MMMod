package com.mmm.legacy.achievement;

import com.mmm.legacy.LegacyDrawContext;
import com.mmm.sound.GoalSoundLibrary;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

public final class GoalSoundSettingsScreen extends Screen {
    private final Screen parent;
    private int selected = 25;
    private boolean choosing;
    private boolean failed;
    private String status = "";
    private int left;
    private int contentWidth;
    private String displayName = "";

    public GoalSoundSettingsScreen(Screen parent) { this.parent = parent; }

    @Override public void init() {
        buttons.clear();
        displayName = GoalSoundLibrary.getDisplayName(selected);
        contentWidth = Math.min(500, width - 24);
        left = (width - contentWidth) / 2;
        int tabWidth = (contentWidth - 18) / 4;
        for (int i = 0; i < 4; i++) {
            int threshold = GoalSoundLibrary.MILESTONES.get(i);
            ButtonWidget button = addButton(new ButtonWidget(threshold, left + i * (tabWidth + 6), 48, tabWidth, 20,
                    threshold + "%"));
            button.active = threshold != selected && !choosing;
        }
        int actionWidth = (contentWidth - 12) / 3;
        addButton(new ButtonWidget(1, left, 114, actionWidth, 20, "Choose OGG")).active = !choosing;
        addButton(new ButtonWidget(2, left + actionWidth + 6, 114, actionWidth, 20, "Preview")).active = !choosing;
        addButton(new ButtonWidget(3, left + (actionWidth + 6) * 2, 114, actionWidth, 20, "Use Default")).active =
                !choosing && GoalSoundLibrary.hasCustomSound(selected);
        addButton(new ButtonWidget(4, left + contentWidth - 60, 12, 60, 20, "Done"));
    }

    @Override protected void buttonClicked(ButtonWidget button) {
        if (GoalSoundLibrary.isMilestone(button.id)) { selected = button.id; status = ""; failed = false; init(); }
        else if (button.id == 1) choose();
        else if (button.id == 2) GoalSoundLibrary.play(selected);
        else if (button.id == 3) {
            try { GoalSoundLibrary.reset(selected); status = "MMM default restored."; failed = false; }
            catch (Exception error) { status = message(error); failed = true; }
            init();
        } else if (button.id == 4) minecraft.openScreen(parent);
    }

    private void choose() {
        if (choosing) return;
        choosing = true;
        int threshold = selected;
        status = "Waiting for a sound file...";
        failed = false;
        init();
        EventQueue.invokeLater(() -> {
            FileDialog dialog = null;
            Path path = null;
            String error = null;
            try {
                dialog = new FileDialog((Frame) null, "Choose an OGG sound for " + threshold + "%", FileDialog.LOAD);
                dialog.setFile("*.ogg");
                dialog.setFilenameFilter((directory, name) -> name != null && name.toLowerCase(Locale.ROOT).endsWith(".ogg"));
                dialog.setVisible(true);
                if (dialog.getDirectory() != null && dialog.getFile() != null)
                    path = new File(dialog.getDirectory(), dialog.getFile()).toPath();
                if (path != null) GoalSoundLibrary.install(threshold, path);
            } catch (Exception failure) { error = message(failure); }
            finally { if (dialog != null) dialog.dispose(); }
            final boolean installed = path != null && error == null;
            final String problem = error;
            Minecraft.getInstance().executeTask(() -> {
                choosing = false;
                selected = threshold;
                failed = problem != null;
                status = problem != null ? problem : installed ? "Custom sound saved for " + threshold + "%." : "No sound selected.";
                if (Minecraft.getInstance().screen == this) {
                    init();
                    if (installed) GoalSoundLibrary.play(threshold);
                }
            });
        });
    }

    private static String message(Exception failure) {
        return failure.getMessage() == null ? "Could not update the sound." : failure.getMessage();
    }

    @Override public void render(int mouseX, int mouseY, float delta) {
        LegacyDrawContext context = new LegacyDrawContext();
        context.fill(0, 0, width, height, 0xEF080808);
        context.drawText(textRenderer, "Milestone Sounds", left, 18, 0xFFFFFF, false);
        context.fill(left, 37, left + contentWidth, 38, 0xFFE00000);
        context.drawText(textRenderer, selected + "% - " + textRenderer.trim(displayName,
                contentWidth - 44), left, 83, 0xFFFFFF, false);
        int y = 148;
        for (String line : textRenderer.split(status, contentWidth)) {
            if (y + textRenderer.fontHeight >= height) break;
            context.drawText(textRenderer, line, left, y, failed ? 0xFF5555 : 0xBBBBBB, false);
            y += textRenderer.fontHeight + 2;
        }
        super.render(mouseX, mouseY, delta);
    }

    @Override protected void keyPressed(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) minecraft.openScreen(parent);
    }

    @Override public boolean shouldPauseGame() { return true; }
}
