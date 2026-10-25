package com.mmm.legacy.settings;

import com.mmm.legacy.LegacyBlockItems;
import com.mmm.legacy.LegacyDrawContext;
import com.mmm.legacy.LegacyScreen;
import com.mmm.timer.MmmTimerState;
import com.mmm.util.UiFormat;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;

public final class TimerResultsScreen extends LegacyScreen {
    private final List<MmmTimerState.BlockCount> blocks = List.copyOf(MmmTimerState.getTopBlocks());
    private final long total = MmmTimerState.getBlocksBroken();
    private final long bestHour = MmmTimerState.getBestHourBlocks();
    private final long elapsed = Math.max(0, MmmTimerState.getDurationMs() - MmmTimerState.getRemainingMs());
    private int offset;
    public TimerResultsScreen(Screen parent) { super(parent, "Timer Results"); }
    @Override public void render(int x, int y, float delta) {
        shell();
        fit(UiFormat.formatBlocks(total) + " / " + MmmTimerState.formatTime(elapsed), left, 48, panelWidth, 0xFFFFFFFF);
        fit("Best Hour: " + UiFormat.formatCompact(bestHour) + " / Average: " + UiFormat.formatBlocksPerHour(elapsed <= 0 ? 0 : Math.round(total * 3600000.0 / elapsed)), left, 64, panelWidth, 0xFFBBBBBB);
        int capacity = Math.max(1, (height - 102) / 22);
        offset = SettingsUi.clampScroll(offset, blocks.size(), capacity);
        LegacyDrawContext context = new LegacyDrawContext();
        for (int i = offset; i < Math.min(blocks.size(), offset + capacity); i++) {
            var item = blocks.get(i); int py = 87 + (i - offset) * 22;
            context.drawItem(LegacyBlockItems.icon(item.id()), left, py - 4);
            String count = UiFormat.formatBlocks(item.count());
            fit(LegacyBlockItems.name(item.id()), left + 24, py, panelWidth - textRenderer.getWidth(count) - 30, 0xFFDDDDDD);
            text(count, left + panelWidth - textRenderer.getWidth(count), py, 0xFFFFFFFF);
        }
        if (blocks.isEmpty()) fit("No blocks recorded", left, 88, panelWidth, 0xFF949494);
        super.render(x, y, delta);
    }
    @Override public void handleMouse() { super.handleMouse(); int wheel = Mouse.getEventDWheel(); if (wheel != 0) offset += wheel > 0 ? -3 : 3; }
}
