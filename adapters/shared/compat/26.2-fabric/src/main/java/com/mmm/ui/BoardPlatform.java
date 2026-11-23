package com.mmm.ui;

import com.mmm.config.Configs;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import static com.mmm.ui.MmmSettingsScreen.*;

final class BoardPlatform {
    static final boolean SPEED_GRAPH = true;
    private BoardPlatform() {}

    static com.mojang.blaze3d.pipeline.RenderTarget renderTarget() {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }
    static void open(Screen screen) { Minecraft.getInstance().gui.setScreen(screen); }
    static Screen ignoreList(Screen parent) { return new MmmChatIgnoreListScreen(parent); }

    static void addSocial(List<SettingsSection> sections) {
        sections.add(SettingsSection.social(
                new SettingRow("Active Digger Icons", "Mark active MMM miners in the player list.", Configs.Generic.SHOW_ACTIVE_DIGGERS, ControlKind.BOOLEAN),
                new SettingRow("Friends Only", "Only mark active miners from your MMM friends list.", Configs.Generic.ACTIVE_DIGGERS_FRIENDS_ONLY, ControlKind.BOOLEAN),
                new SettingRow("Share Milestones", "Share your goal milestones with linked MMM players.", Configs.Generic.SHARE_GOAL_MILESTONES, ControlKind.BOOLEAN),
                new SettingRow("Collection Milestones", "Share new collection tiers in MMM chat, before sync.", Configs.Generic.SHARE_COLLECTION_MILESTONES, ControlKind.BOOLEAN),
                new SettingRow("Receive Milestones", "Show milestones from linked MMM players.", Configs.Generic.RECEIVE_GOAL_MILESTONES, ControlKind.BOOLEAN),
                new SettingRow("MMM Chat Messages", "Show MMM chat in Minecraft chat.", Configs.Generic.SHOW_MMM_CHAT_MESSAGES, ControlKind.BOOLEAN),
                new SettingRow("Censor MMM Chat", "Hide slurs in MMM chat.", Configs.Generic.CENSOR_MMM_CHAT, ControlKind.BOOLEAN),
                new SettingRow("Ignored Players", "Hide selected players' chat and milestones.", null, ControlKind.ACTION)
        ));
    }

    static void addRankEstimate(List<SettingsSection> sections) {
        SettingsSection hud = sections.removeLast();
        var rows = new java.util.ArrayList<>(hud.rows());
        int worldTotal = 0;
        while (worldTotal < rows.size() && !rows.get(worldTotal).label().equals("World Total")) worldTotal++;
        rows.add(worldTotal, new SettingRow("Rank Overtake Estimate", "Projects manual rank using website totals plus new local mining. ETA uses the HUD Blocks/hr pace and shows active mining time. Reconciles after sync; assumes the target stops mining.", Configs.Generic.HUD_RANK_OVERTAKE_VISIBLE, ControlKind.BOOLEAN));
        sections.add(new SettingsSection(SidebarItem.HUD, hud.title(), hud.description(), List.copyOf(rows)));
    }
}
