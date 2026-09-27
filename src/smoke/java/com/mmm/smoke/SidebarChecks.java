package com.mmm.smoke;

import com.mmm.config.Configs;
import com.mmm.leaderboard.*;
import com.mmm.scoreboard.*;
import com.mmm.ui.LeaderboardScreen;
import java.util.ArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class SidebarChecks {
    static void run(MinecraftClient client) throws Exception {
        var scoreboard = client.world.getScoreboard();
        int originalObjectives = scoreboard.getObjectives().size();
        LeaderboardSidebar.select(LeaderboardData.Kind.BLOCKS, "minecraft:gilded_blackstone");
        require(com.mmm.storage.AsyncPersistence.flush(java.time.Duration.ofSeconds(5)), "Sidebar save timed out");
        Configs.saveToFile();
        Configs.Generic.MMM_LEADERBOARD_SIDEBAR.setBooleanValue(false);
        Configs.sidebarLeaderboardKind = "PLAYERS";
        Configs.loadFromFile();
        require(LeaderboardSidebar.enabled() && LeaderboardSidebar.selection().kind() == LeaderboardData.Kind.BLOCKS
                && LeaderboardSidebar.selection().block().equals("minecraft:gilded_blackstone"), "Sidebar config persistence");

        var screen = new LeaderboardScreen(null, true);
        client.setScreen(screen);
        var use = screen.children().stream().filter(c -> c instanceof ButtonWidget button
                && button.getMessage().equals(Text.literal("Use"))).map(c -> (ButtonWidget)c).findFirst().orElseThrow();
        require(use.active, "Saved block cannot be selected");
        use.onPress(new net.minecraft.client.input.KeyInput(32, 0, 0));
        require(client.currentScreen == null, "Sidebar chooser did not return");

        var rows = new ArrayList<LeaderboardData.Row>();
        for (int i = 0; i < 25; i++) rows.add(new LeaderboardData.Row(i + 1, "Miner" + (i + 1), 4_500_000_000L - i, ""));
        set("current", LeaderboardSidebar.selection());
        set("data", new LeaderboardData.Page(rows, 1, 2, 50, 9_000_000_000L));
        set("refreshAt", Long.MAX_VALUE);
        require(LeaderboardSidebar.entries().getFirst().score().getString().equals("4,500,000,000"), "Long totals truncated");
        require(ScoreboardService.pageDown() && LeaderboardSidebar.entries().getFirst().name().getString().startsWith("#16"), "Sidebar paging");
        require(ScoreboardService.pageUp() && LeaderboardSidebar.entries().getFirst().name().getString().startsWith("#1 "), "Sidebar previous page");
        var bounds = ScoreboardHudRenderer.getBounds(client, null);
        require(bounds.left() >= 0 && bounds.right() <= client.getWindow().getScaledWidth(), "Sidebar width overflow");
        client.setScreen(new ScoreboardMoveScreen(null));
        client.setScreen(null);
        require(scoreboard.getObjectives().size() == originalObjectives, "Sidebar changed world scoreboard");
        set("error", "Cached - retrying");
        require(LeaderboardSidebar.title().getString().contains("Cached") && LeaderboardSidebar.entries().size() == 15,
                "Failed refresh discarded cached rankings");
        set("error", null);
        ScoreboardHudRenderer.invalidateLayout();
    }
    private static void set(String name, Object value) throws Exception {
        var field = LeaderboardSidebar.class.getDeclaredField(name);
        field.setAccessible(true); field.set(null, value);
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
