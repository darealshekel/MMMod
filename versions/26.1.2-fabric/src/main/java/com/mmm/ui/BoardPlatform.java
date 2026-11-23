package com.mmm.ui;

import com.mmm.config.Configs;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import static com.mmm.ui.MmmSettingsScreen.*;

final class BoardPlatform {
    static final boolean SPEED_GRAPH = false;
    private BoardPlatform() {}

    static com.mojang.blaze3d.pipeline.RenderTarget renderTarget() {
        return Minecraft.getInstance().getMainRenderTarget();
    }
    static void open(Screen screen) { Minecraft.getInstance().setScreen(screen); }
    static Screen ignoreList(Screen parent) { throw new UnsupportedOperationException("No ignore-list setting on 26.1.2"); }

    static void addSocial(List<SettingsSection> sections) {
        sections.add(SettingsSection.social(
                new SettingRow("Share Milestones", "Share progress with MMM players here.", Configs.Generic.SHARE_GOAL_MILESTONES, ControlKind.BOOLEAN),
                new SettingRow("Receive Milestones", "Show progress from MMM players here.", Configs.Generic.RECEIVE_GOAL_MILESTONES, ControlKind.BOOLEAN),
                new SettingRow("MMM Chat Messages", "Show global MMM messages in Minecraft chat.", Configs.Generic.SHOW_MMM_CHAT_MESSAGES, ControlKind.BOOLEAN)
        ));
    }

    static void addRankEstimate(List<SettingsSection> sections) { /* Not available on 26.1.2. */ }
}
