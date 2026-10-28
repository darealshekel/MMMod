package com.mmm.legacy.collection;

import com.mmm.leaderboard.LeaderboardData;
import com.mmm.tracker.CollectionWebsiteClient;
import java.util.*;
import net.minecraft.client.gui.screen.Screen;

/** Public profile breakdowns only; no remote data is ever written to local mining counters. */
public final class LeaderboardProfileScreen extends CollectionScreen {
    private final LeaderboardData.Row player;
    private final String ranking;
    private CollectionWebsiteClient.Snapshot profile;
    private List<Map.Entry<String, Long>> blocks = List.of();
    private String error;
    private int x, contentWidth, offset, generation;
    private boolean loading;
    public LeaderboardProfileScreen(Screen parent, LeaderboardData.Row player, String ranking) {
        super(parent); this.player = player; this.ranking = ranking;
    }
    @Override public void init() {
        super.init(); contentWidth = Math.min(520, width - 24); x = (width - contentWidth) / 2;
        button(x + contentWidth - 60, height - 28, 60, "Done", this::close);
        if (profile == null && !loading) {
            loading = true; int request = ++generation;
            CollectionWebsiteClient.load("profile:" + player.name(), player.name()).whenComplete((result, failure) -> minecraft.executeTask(() -> {
                if (request != generation) return;
                loading = false;
                if (failure != null) error = "Website profile unavailable";
                else {
                    profile = result;
                    blocks = result.counts().entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).toList();
                }
            }));
        }
    }
    @Override public void render(int mx, int my, float delta) {
        draw.fill(0, 0, width, height, 0xED111111);
        fit(player.name(), x, 15, contentWidth, 0xFFF5F5F5);
        fit(ranking + " rank #" + player.rank() + " | " + LeaderboardScreen.format(player.blocks()) + " blocks", x, 34, contentWidth, 0xFFFFCC66);
        fit(player.detail(), x, 49, contentWidth, 0xFFBBBBBB);
        fit(loading ? "Loading profile..." : error != null ? error : profile == null ? "" : profile.worlds() + " website worlds", x, 67, contentWidth, 0xFFBBBBBB);
        int visible = Math.max(0, (height - 124) / 24);
        offset = Math.clamp(offset, 0, Math.max(0, blocks.size() - visible));
        for (int i = offset; i < Math.min(blocks.size(), offset + visible); i++) {
            var block = blocks.get(i); int y = 86 + (i - offset) * 24;
            draw.drawItem(CollectionIcons.block(block.getKey()), x + 2, y);
            fit(CollectionIcons.name(block.getKey()), x + 24, y + 4, contentWidth - 140, 0xFFF5F5F5);
            right(LeaderboardScreen.format(block.getValue()), x + contentWidth - 4, y + 4, 110, 0xFFF5F5F5);
        }
        super.render(mx, my, delta);
    }
    @Override protected void scroll(int mx, int my, int direction) { offset = Math.max(0, offset - direction * 3); }
    @Override public void removed() { super.removed(); generation++; loading = false; }
}
