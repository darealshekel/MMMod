package com.mmm.leaderboard;

import com.mmm.config.Configs;
import com.mmm.scoreboard.ScoreboardHudRenderer;
import com.mmm.scoreboard.ScoreboardMoveScreen;
import com.mmm.scoreboard.ScoreboardService;
import com.mmm.scoreboard.ScoreboardState;
import com.mmm.util.BlockBreakdownCatalog;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Website-only display data. Never installs an objective in the world's scoreboard. */
public final class LeaderboardSidebar {
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.US);
    private static LeaderboardData.Query current;
    private static LeaderboardData.Page data;
    private static CompletableFuture<LeaderboardData.Page> pending;
    private static long refreshAt;
    private static int generation, page = 1;
    private static String error;
    private static boolean lastChunk;
    private LeaderboardSidebar() {}

    public static boolean enabled() { return Configs.Generic.MMM_LEADERBOARD_SIDEBAR.getBooleanValue(); }
    public static LeaderboardData.Query selection() {
        LeaderboardData.Kind kind;
        try { kind = LeaderboardData.Kind.valueOf(Configs.sidebarLeaderboardKind); }
        catch (RuntimeException ignored) { kind = LeaderboardData.Kind.PLAYERS; }
        String block = BlockBreakdownCatalog.isValid(Configs.sidebarLeaderboardBlock)
                ? Configs.sidebarLeaderboardBlock : "minecraft:stone";
        return new LeaderboardData.Query(kind, block, "", 1);
    }
    public static void select(LeaderboardData.Kind kind, String block) {
        Configs.sidebarLeaderboardKind = kind.name();
        Configs.sidebarLeaderboardBlock = block == null ? "minecraft:stone" : block;
        Configs.Generic.MMM_LEADERBOARD_SIDEBAR.setBooleanValue(true);
        reset();
        Configs.requestSave();
    }
    private static void reset() {
        generation++;
        if (pending != null) pending.cancel(true);
        pending = null; current = null; data = null; error = null; refreshAt = 0; page = 1;
        lastChunk = false;
        ScoreboardState.resetPage();
        ScoreboardHudRenderer.invalidateLayout();
    }
    public static void tick(Minecraft client) {
        if (!enabled() || client.level == null || client.player == null) {
            if (current != null || pending != null) reset();
            return;
        }
        var selected = selection();
        if (current != null && (current.kind() != selected.kind() || !current.block().equals(selected.block()))) reset();
        if (pending != null || System.currentTimeMillis() < refreshAt) return;
        var query = new LeaderboardData.Query(selected.kind(), selected.block(), "", page);
        current = query;
        int requestGeneration = ++generation;
        pending = LeaderboardClient.load(query, false);
        pending.whenComplete((result, failure) -> client.execute(() -> {
            if (generation != requestGeneration) return;
            pending = null;
            if (failure == null) {
                data = result; page = result.page(); error = null;
                if (lastChunk) {
                    int size = Math.max(1, Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue());
                    while (ScoreboardState.pageDown(data.rows().size(), size)) { }
                    lastChunk = false;
                }
            }
            else error = data == null ? "Unavailable - retrying" : "Cached - retrying";
            refreshAt = System.currentTimeMillis() + (failure == null ? 60_000 : 30_000);
            ScoreboardHudRenderer.invalidateLayout();
        }));
    }
    public static Component title() {
        var query = selection();
        String name = query.kind().label;
        if (query.kind() == LeaderboardData.Kind.BLOCKS) {
            var id = Identifier.tryParse(query.block());
            name = id != null && BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id).getName().getString()
                    : query.block().replace("minecraft:", "").replace('_', ' ');
        }
        return Component.literal("MMM " + name + (data != null && data.pages() > 1 ? " | " + page + "/" + data.pages() : "")
                + (data != null && error != null ? " | Cached" : ""));
    }
    public static List<ScoreboardService.RenderEntry> entries() {
        if (data == null || data.rows().isEmpty()) return List.of(new ScoreboardService.RenderEntry("",
                Component.literal(error != null ? error : data == null ? "Loading rankings..." : "No records"), Component.empty(), 0));
        int size = Math.max(1, Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue());
        ScoreboardState.clampPage(data.rows().size(), size);
        int from = Math.min(data.rows().size(), ScoreboardState.getPageOffset());
        int to = Math.min(data.rows().size(), from + size);
        if (Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue() == 0) return List.of();
        return data.rows().subList(from, to).stream().map(row -> new ScoreboardService.RenderEntry(row.name(),
                Component.literal("#" + row.rank() + " " + row.name()),
                Configs.Generic.SCOREBOARD_SCORES_VISIBLE.getBooleanValue() ? Component.literal(format(row.blocks())) : Component.empty(), 0)).toList();
    }
    private static String format(long blocks) {
        if (Configs.Generic.SCOREBOARD_SCORE_ABBREVIATED.getBooleanValue() && blocks >= 1000) {
            double divisor = blocks >= 1_000_000_000 ? 1_000_000_000D : blocks >= 1_000_000 ? 1_000_000D : 1000D;
            return String.format(Locale.US, "%.2f%s", blocks / divisor, divisor == 1_000_000_000D ? "B" : divisor == 1_000_000D ? "M" : "k");
        }
        return Configs.Generic.SCOREBOARD_SCORE_COMMAS.getBooleanValue() ? NUMBERS.format(blocks) : Long.toString(blocks);
    }
    public static boolean changePage(boolean forward) {
        if (pending != null || data == null || Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue() <= 0) return false;
        int size = Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue();
        boolean changed = forward ? ScoreboardState.pageDown(data.rows().size(), size) : ScoreboardState.pageUp(size);
        if (!changed && (forward ? page < data.pages() : page > 1)) {
            page += forward ? 1 : -1;
            lastChunk = !forward;
            data = null; error = null; refreshAt = 0; ScoreboardState.resetPage(); changed = true;
        }
        if (changed) ScoreboardHudRenderer.invalidateLayout();
        return changed;
    }
    public static void render(GuiGraphicsExtractor context, Minecraft client) {
        if (enabled() && client.level != null && client.player != null && !client.gui.hud.isHidden()
                && Configs.Generic.SCOREBOARD_VISIBLE.getBooleanValue() && !(client.gui.screen() instanceof ScoreboardMoveScreen))
            ScoreboardHudRenderer.render(context, client, null);
    }
}
