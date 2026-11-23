package com.mmm.legacy;

import com.mmm.sync.SyncScoreboardSelector;
import java.util.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Mouse;

final class LegacyScoreboardScreen extends LegacyScreen {
    private List<SyncScoreboardSelector.ObjectiveChoice> choices = List.of();
    private int offset;
    LegacyScoreboardScreen(Screen parent) { super(parent, "Sync scoreboard"); }
    @Override protected int maxContentWidth() { return 520; }
    private int capacity() { return Math.max(1, (height - 128) / 36); }
    @Override public void init() {
        super.init();
        choices = SyncScoreboardSelector.availableObjectives(minecraft);
        offset = Math.min(offset, Math.max(0, choices.size() - capacity()));
        recordButton(1, 55, "Automatic", !SyncScoreboardSelector.hasManualSelection());
        for (int i = 0; i < capacity() && offset + i < choices.size(); i++) {
            var choice = choices.get(offset + i);
            recordButton(100 + i, 84 + i * 36,
                    choice.displayName() + " [" + choice.objectiveName() + "]", choice.objectiveName().equals(SyncScoreboardSelector.selectedObjectiveName()));
        }
        button(2, left + panelWidth - 50, height - 28, 22, "<").active = offset > 0;
        button(3, left + panelWidth - 24, height - 28, 22, ">").active = offset + capacity() < choices.size();
    }
    @Override protected void buttonClicked(ButtonWidget button) {
        if (button.id == 1) { SyncScoreboardSelector.selectObjective(""); minecraft.openScreen(parent); }
        else if (button.id >= 100 && button.id < 100 + capacity()) {
            SyncScoreboardSelector.selectObjective(choices.get(offset + button.id - 100).objectiveName()); minecraft.openScreen(parent);
        } else if (button.id == 2 || button.id == 3) { offset += button.id == 2 ? -capacity() : capacity(); offset = Math.max(0, offset); init(); }
        else super.buttonClicked(button);
    }
    @Override public void render(int x, int y, float delta) {
        shell(); section("Mining objective", 39);
        if (choices.isEmpty()) { recordRow(84, 24, false); text("No mining scoreboards available", left + 7, 91, 0xFFBBBBBB); }
        for (int i = 0; i < capacity() && offset + i < choices.size(); i++) {
            var choice = choices.get(offset + i);
            fit("You: " + String.format(Locale.ROOT, "%,d", choice.playerTotal()) + " | " + choice.playerCount() + " players",
                    left + 4, 105 + i * 36, panelWidth - 8, 0xFFBBBBBB);
        }
        super.render(x, y, delta);
    }
    @Override public void handleMouse() {
        super.handleMouse(); int wheel = Mouse.getEventDWheel();
        if (wheel != 0) { offset = Math.max(0, Math.min(Math.max(0, choices.size() - capacity()), offset + (wheel < 0 ? 1 : -1))); init(); }
    }
}
