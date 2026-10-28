package com.mmm.legacy.collection;

import com.mmm.leaderboard.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

public final class LeaderboardScreen extends CollectionScreen {
    private static final int ROW_HEIGHT = 26;
    private final boolean chooseSidebar;
    private LeaderboardData.Kind kind = LeaderboardData.Kind.PLAYERS;
    private BlockRankingCategory category = BlockRankingCategory.ALL;
    private String selectedBlock, search = "", error;
    private LeaderboardData.Page data;
    private CompletableFuture<LeaderboardData.Page> pending;
    private ButtonWidget previous, next, refresh;
    private List<String> visibleBlocks = List.of();
    private int page = 1, offset, generation, x, contentWidth, listTop, listBottom, blockChoiceY;
    private long searchAt, refreshAt;
    private boolean loading, removed;
    public LeaderboardScreen(Screen parent) { this(parent, false); }
    public LeaderboardScreen(Screen parent, boolean chooseSidebar) {
        super(parent); this.chooseSidebar = chooseSidebar;
        if (chooseSidebar) {
            var selection = LeaderboardSidebar.selection(); kind = selection.kind();
            selectedBlock = kind == LeaderboardData.Kind.BLOCKS ? selection.block() : null;
        }
    }
    private boolean picking() { return kind == LeaderboardData.Kind.BLOCKS && selectedBlock == null; }
    @Override public void init() {
        super.init(); removed = false;
        contentWidth = Math.min(640, width - 24); x = (width - contentWidth) / 2;
        int y = 36, columns = contentWidth >= 400 ? 8 : 4, cell = contentWidth / columns, i = 0;
        String[] compact = {"All", "Srv", "Wld", "TMC", "SSP", "HSP", "Geo", "Blk"};
        for (var entry : LeaderboardData.Kind.values()) {
            String label = textRenderer.getWidth(entry.label) <= cell - 7 ? entry.label : compact[i];
            button(x + i % columns * cell, y + i / columns * 22, cell - 3, label, () -> {
                kind = entry; selectedBlock = null; search = ""; page = 1; offset = 0; data = null; error = null;
                searchAt = 0; cancel(); rebuild();
            }).active = entry != kind;
            i++;
        }
        y += ((i + columns - 1) / columns) * 22 + 4; blockChoiceY = y;
        if (picking()) {
            button(x, y, contentWidth, category.label + " category", () -> {
                category = BlockRankingCategory.values()[(category.ordinal() + 1) % BlockRankingCategory.values().length];
                offset = 0; rebuild();
            }); y += 24;
        } else if (kind == LeaderboardData.Kind.BLOCKS) {
            button(x, y, contentWidth, CollectionIcons.name(selectedBlock) + " - Choose block", () -> {
                selectedBlock = null; search = ""; data = null; offset = 0; page = 1; error = null; cancel(); rebuild();
            }); y += 24;
        }
        searchField = search(x, y, contentWidth - 68, search);
        refresh = button(x + contentWidth - 62, y - 1, 62, chooseSidebar ? "Use" : picking() ? "Clear" : "Refresh", () -> {
            if (chooseSidebar) { LeaderboardSidebar.select(kind, selectedBlock); close(); return; }
            if (picking()) { searchField.setText(""); searchChanged(""); }
            else { searchAt = 0; refreshAt = System.currentTimeMillis() + 2000; load(true); }
        });
        listTop = y + 34; listBottom = height - 36;
        int nav = Math.min(72, contentWidth / 5);
        previous = button(x, height - 28, nav, "< Prev", () -> { page--; offset = 0; load(false); });
        next = button(x + nav + 4, height - 28, nav, "Next >", () -> { page++; offset = 0; load(false); });
        button(x + contentWidth - nav, height - 28, nav, "Done", this::close);
        if (!chooseSidebar) button(x + contentWidth - nav * 2 - 4, height - 28, nav, "Sidebar", () -> minecraft.openScreen(new LeaderboardScreen(this, true)));
        filterBlocks();
        if (data == null && !loading && !picking() && error == null && searchAt == 0) load(false);
        updateButtons();
    }
    @Override protected void searchChanged(String value) {
        search = value; offset = 0; page = 1; cancel(); data = null; error = null;
        if (picking()) filterBlocks(); else searchAt = System.currentTimeMillis() + 350;
    }
    private void filterBlocks() {
        String query = search.toLowerCase(Locale.ROOT);
        visibleBlocks = CollectionCatalog.blockIds().stream().filter(category::includes)
                .filter(id -> id.contains(query) || CollectionIcons.name(id).toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private void cancel() {
        generation++; loading = false;
        if (pending != null) pending.cancel(true);
        pending = null;
    }
    private void load(boolean force) {
        if (picking() || removed) return;
        cancel(); loading = true; error = null; data = null;
        int requestGeneration = generation;
        pending = LeaderboardClient.load(new LeaderboardData.Query(kind, selectedBlock, search, page), force);
        pending.whenComplete((result, failure) -> minecraft.executeTask(() -> {
            if (removed || requestGeneration != generation) return;
            loading = false; pending = null;
            if (failure == null) { data = result; page = result.page(); }
            else {
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                error = cause instanceof IllegalStateException ? cause.getMessage() : "Could not load rankings. Check your connection and refresh.";
            }
            updateButtons();
        }));
        updateButtons();
    }
    private void updateButtons() {
        if (previous == null) return;
        previous.active = !picking() && !loading && data != null && page > 1;
        next.active = !picking() && !loading && data != null && page < data.pages();
        refresh.active = chooseSidebar ? !picking() : !loading && System.currentTimeMillis() >= refreshAt;
    }
    @Override public void tick() {
        super.tick();
        if (searchAt > 0 && System.currentTimeMillis() >= searchAt) { searchAt = 0; load(false); }
        updateButtons();
    }
    @Override public void render(int mx, int my, float delta) {
        draw.fill(0, 0, width, height, 0xED111111);
        fit(chooseSidebar ? "MMM Sidebar Leaderboard" : "MMM Leaderboards", x, 14, contentWidth, 0xFFF5F5F5);
        String status = picking() ? visibleBlocks.size() + " blocks" : loading || searchAt > 0 ? "Loading rankings..."
                : error != null ? error : data == null ? "" : kind.label + " | " + data.page() + "/" + data.pages() + " | " + format(data.total()) + " entries";
        fit(status, x, listTop - 11, contentWidth, error != null ? 0xFFFF8888 : 0xFFBBBBBB);
        int count = picking() ? visibleBlocks.size() : data == null ? 0 : data.rows().size();
        int visible = Math.max(0, (listBottom - listTop) / ROW_HEIGHT);
        offset = Math.clamp(offset, 0, Math.max(0, count - visible));
        for (int i = offset; i < Math.min(count, offset + visible); i++) {
            int ry = listTop + (i - offset) * ROW_HEIGHT;
            boolean hover = mx >= x && mx < x + contentWidth && my >= ry && my < ry + ROW_HEIGHT;
            draw.fill(x, ry, x + contentWidth, ry + ROW_HEIGHT - 1, hover ? 0xFF432527 : 0xFF202020);
            if (picking()) {
                String id = visibleBlocks.get(i);
                draw.drawItem(CollectionIcons.block(id), x + 4, ry + 4);
                fit(CollectionIcons.name(id), x + 26, ry + 9, contentWidth - 36, 0xFFF5F5F5);
            } else {
                var row = data.rows().get(i); int amountWidth = Math.min(118, contentWidth / 2);
                draw.drawItem(CollectionIcons.item(kindIcon(kind)), x + 3, ry + 4);
                fit("#" + row.rank() + " " + row.name(), x + 23, ry + 3, contentWidth - amountWidth - 34, 0xFFF5F5F5);
                fit(row.detail(), x + 23, ry + 15, contentWidth - amountWidth - 34, 0xFFAAAAAA);
                right(format(row.blocks()), x + contentWidth - 6, ry + 3, amountWidth, 0xFFFF6955);
                right("blocks mined", x + contentWidth - 6, ry + 15, amountWidth, 0xFFAAAAAA);
            }
        }
        if (count == 0 && !loading && error == null && searchAt == 0)
            fit("No matching records.", x + 6, listTop + 12, contentWidth - 12, 0xFFBBBBBB);
        if (count > visible && visible > 0) {
            int track = visible * ROW_HEIGHT, thumb = Math.max(8, track * visible / count);
            int ry = listTop + (track - thumb) * offset / Math.max(1, count - visible);
            draw.fill(x + contentWidth - 2, ry, x + contentWidth, ry + thumb, 0xFFCC3344);
        }
        super.render(mx, my, delta);
        if (kind == LeaderboardData.Kind.BLOCKS)
            draw.drawItem(picking() ? CollectionIcons.item(category.icon) : CollectionIcons.block(selectedBlock), x + 4, blockChoiceY + 2);
        if (error != null && mx >= x && mx < x + contentWidth && my >= listTop - 12 && my < listTop)
            renderTooltip(textRenderer.split(error, Math.min(300, width - 30)), mx, my);
    }
    static String kindIcon(LeaderboardData.Kind kind) {
        return switch (kind) {
            case PLAYERS -> "diamond_pickaxe"; case SERVERS -> "beacon"; case WORLD, SSP -> "grass_block";
            case TMC -> "redstone"; case HSP -> "golden_apple"; case COUNTRIES -> "compass"; case BLOCKS -> "stone";
        };
    }
    static String format(long value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }
    @Override protected void mouseClicked(int mx, int my, int button) {
        if (button == 0 && mx >= x && mx < x + contentWidth && my >= listTop && my < listTop + Math.max(0, (listBottom - listTop) / ROW_HEIGHT) * ROW_HEIGHT) {
            int index = offset + (my - listTop) / ROW_HEIGHT;
            if (picking() && index < visibleBlocks.size()) {
                selectedBlock = visibleBlocks.get(index); search = ""; page = 1; offset = 0; searchAt = 0; rebuild(); return;
            }
            if (!picking() && data != null && index < data.rows().size() && kind != LeaderboardData.Kind.SERVERS && kind != LeaderboardData.Kind.COUNTRIES) {
                var row = data.rows().get(index);
                if (row.name().matches("[A-Za-z0-9_]{1,16}")) minecraft.openScreen(new LeaderboardProfileScreen(this, row, kind.label));
                return;
            }
        }
        super.mouseClicked(mx, my, button);
    }
    @Override protected void scroll(int mx, int my, int direction) {
        if (mx >= x && mx < x + contentWidth && my >= listTop && my < listBottom) offset = Math.max(0, offset - direction * 3);
    }
    @Override public void removed() { super.removed(); removed = true; cancel(); }
}
