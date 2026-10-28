package com.mmm.legacy.collection;

import com.mmm.config.Configs;
import com.mmm.leaderboard.BlockRankingCategory;
import com.mmm.storage.WorldIdentity;
import com.mmm.storage.WorldSessionContext;
import com.mmm.sync.WebsiteLinkManager;
import com.mmm.tracker.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;

/** The modern collection book, rendered through Feather's inventory GUI pipeline. */
public final class CollectionBookScreen extends CollectionScreen {
    private record Entry(String id, String name) {}
    private static final int PAGE_SIZE = 28, OVERVIEW_ROW_HEIGHT = 42;
    private static final String[] TIERS = {"I", "II", "III", "IV", "V"};
    private static final String[] SORTS = {"Nearest milestone", "Most mined", "Block name"};
    private static final String[] FILTERS = {"All collections", "Started collections", "Completed collections"};
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);
    private final List<Entry> catalog = new ArrayList<>();
    private List<Entry> rows = List.of();
    private Map<String, Long> counts = Map.of();
    private BlockRankingCategory category;
    private Entry selected;
    private boolean allWorlds = true, loading, failed, closed;
    private CollectionWebsiteClient.Snapshot website;
    private String websitePlayer = "", search = "", rankHoverId = "";
    private long refreshAt, rankHoverStarted;
    private int sort, filter, page, ticks, x, y, cell, panelWidth, panelHeight, gridX, gridY, footerY, overviewScroll;
    private final long[] tierCompletions = new long[CollectionProgress.OVERVIEW_MILESTONES.size()];
    private final long[] tierTotals = new long[CollectionProgress.OVERVIEW_MILESTONES.size()];

    public CollectionBookScreen(Screen parent) { super(parent); }
    @Override public void init() {
        super.init(); closed = false;
        panelWidth = Math.min(420, width - 24);
        cell = Math.max(20, Math.min(40, Math.min((panelWidth - 24) / 7, (height - 126) / 4)));
        panelHeight = cell * 4 + 110;
        if (category == null && selected == null)
            panelHeight = Math.min(height - 16, 168 + ((tierTotals.length + columns() - 1) / columns()) * OVERVIEW_ROW_HEIGHT);
        x = (width - panelWidth) / 2; y = (height - panelHeight) / 2;
        gridX = x + (panelWidth - cell * 7) / 2; gridY = y + 72; footerY = y + panelHeight - 30;
        overviewScroll = Math.clamp(overviewScroll, 0, maxScroll());
        if (catalog.isEmpty()) {
            var added = new HashSet<String>();
            for (String block : CollectionCatalog.blockIds()) {
                String id = CollectionProgress.collectionId(block);
                if (added.add(id)) catalog.add(new Entry(id, CollectionIcons.name(id)));
            }
        }
        searchField = search(x + 12, y + 48, panelWidth - 24, search);
        searchField.setVisible(selected == null);
        refresh(); rebuildSlots();
    }
    @Override protected void searchChanged(String value) {
        search = value; page = 0;
        if (!value.isBlank() && category == null) {
            category = BlockRankingCategory.ALL; rebuild(); searchField.setFocused(true);
        } else { filterRows(); rebuildSlots(); }
    }
    private long count(Entry entry) { return counts.getOrDefault(entry.id, 0L); }
    private void refresh() {
        refreshWebsite();
        var sources = new ArrayList<CollectionProgress.Source>();
        for (var world : Configs.WORLD_STATS) {
            String id = WorldIdentity.canonicalWorldId(world.worldId, world.kind, world.host);
            if (allWorlds || id.equals(WorldSessionContext.getCurrentWorldId()))
                sources.add(new CollectionProgress.Source(id, world.blockBreakdown));
        }
        counts = allWorlds && website != null ? LiveCollections.counts(website)
                : LiveCollections.localCounts(CollectionProgress.aggregate(sources), allWorlds ? null : WorldSessionContext.getCurrentWorldId());
        Arrays.fill(tierCompletions, 0); Arrays.fill(tierTotals, 0);
        for (var entry : catalog) for (long goal : CollectionProgress.milestones(entry.id)) {
            int tier = CollectionProgress.OVERVIEW_MILESTONES.indexOf(goal);
            tierTotals[tier]++;
            if (count(entry) >= goal) tierCompletions[tier]++;
        }
        filterRows();
    }
    private void refreshWebsite() {
        String player = WebsiteLinkManager.isCurrentPlayerLinked() ? Configs.websiteLinkedMinecraftUuid : "";
        if (!player.equals(websitePlayer)) {
            websitePlayer = player; website = null; loading = false; failed = false; refreshAt = 0;
        }
        if (!allWorlds || player.isBlank() || loading || System.currentTimeMillis() < refreshAt) return;
        String name = WebsiteLinkManager.getPersistedUsername();
        if (name.isBlank()) name = minecraft.player.getGameProfile().getName();
        loading = true;
        CollectionWebsiteClient.load(player, name).whenComplete((result, failure) -> minecraft.executeTask(() -> {
            if (closed || !player.equals(websitePlayer) || !player.equals(Configs.websiteLinkedMinecraftUuid)) return;
            loading = false; failed = failure != null;
            refreshAt = System.currentTimeMillis() + (failed ? 30_000 : 60_000);
            if (!failed) website = result;
            refresh(); rebuildSlots();
        }));
    }
    private String scope() {
        if (!allWorlds) return "This World / " + WorldSessionContext.getCurrentWorldName();
        if (loading) return website == null ? "Global / loading website..." : "Global / refreshing website...";
        if (website != null) return "Global / website + unsynced mining" + (failed ? " (cached)" : "");
        return websitePlayer.isBlank() ? "Global / local only (not linked)" : "Global / local only (unavailable)";
    }
    private void filterRows() {
        String query = search.strip().toLowerCase(Locale.ROOT);
        Comparator<Entry> order = sort == 0 ? Comparator.comparingLong(entry -> CollectionProgress.remaining(entry.id, count(entry)))
                : sort == 1 ? Comparator.<Entry>comparingLong(this::count).reversed()
                : Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
        rows = catalog.stream().filter(entry -> category == null || category.includes(entry.id))
                .filter(entry -> entry.name.toLowerCase(Locale.ROOT).contains(query) || entry.id.contains(query))
                .filter(entry -> filter == 0 || filter == 1 && count(entry) > 0 || filter == 2 && CollectionProgress.next(entry.id, count(entry)) == 0)
                .sorted(order.thenComparing(Entry::name)).toList();
        page = Math.min(page, pages() - 1);
    }
    private int pages() { return Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE); }
    private void rebuildSlots() {
        buttons.clear(); slots.clear();
        slot(x + panelWidth - 34, y + 8, 22, "Collection progress", ItemStack.EMPTY, true,
                () -> { selected = null; category = null; search = ""; page = 0; rebuild(); }, () -> "", () -> {
                    var lines = new ArrayList<String>(); lines.add("\u00a7cMMM Collections");
                    for (int i = 0; i < tierTotals.length; i++) lines.add(CollectionProgress.targetLabel(CollectionProgress.OVERVIEW_MILESTONES.get(i))
                            + ": " + tierCompletions[i] + " / " + tierTotals[i] + " completed");
                    return lines;
                });
        footer(0, "Back", "arrow", this::back, () -> List.of(selected != null ? "Back to blocks" : category != null ? "Back to categories" : "Back"));
        footer(1, "World scope", allWorlds ? "compass" : "grass_block", () -> { allWorlds = !allWorlds; page = 0; refresh(); rebuildSlots(); },
                () -> List.of("\u00a7e" + scope(), allWorlds ? "Includes mining since the last sync" : "Live local block breakdown",
                        allWorlds ? "Switch to this world" : "Switch to Global Total"));
        footer(8, "Close", "barrier", this::close, () -> List.of("\u00a7cClose collections"));
        if (selected != null) {
            boolean pinned = PinnedCollection.matches(selected.id, allWorlds);
            footer(4, "Pin collection", pinned ? "lime_dye" : "tripwire_hook", () -> {
                if (PinnedCollection.matches(selected.id, allWorlds)) PinnedCollection.unpin();
                else PinnedCollection.pin(selected.id, allWorlds, website);
                rebuildSlots();
            }, () -> List.of(pinned ? "Unpin collection" : "Pin collection to HUD", allWorlds ? "Global Total / live progress" : WorldSessionContext.getCurrentWorldName()));
            detailSlots();
        } else {
            if (PinnedCollection.hasPin()) footer(4, "Unpin collection", "lime_dye", () -> { PinnedCollection.unpin(); rebuildSlots(); }, () -> List.of("Unpin collection from HUD"));
            if (category == null) {
                int step = Math.min(cell, (panelWidth - 24) / BlockRankingCategory.values().length);
                int size = Math.min(24, step - 2);
                for (var group : BlockRankingCategory.values()) {
                    int bx = x + (panelWidth - step * BlockRankingCategory.values().length) / 2 + group.ordinal() * step + (step - size) / 2;
                    slot(bx, footerY - 30, size, group.label, CollectionIcons.item(group.icon), false,
                            () -> { category = group; page = 0; rebuild(); }, () -> "", () -> List.of("\u00a7e" + group.label + " Collections",
                                    catalog.stream().filter(e -> group.includes(e.id) && CollectionProgress.next(e.id, count(e)) == 0).count()
                                            + " / " + catalog.stream().filter(e -> group.includes(e.id)).count() + " completed"));
                }
                if (maxScroll() > 0) {
                    footer(5, "Scroll tiers up", "arrow", () -> scrollOverview(-42), () -> List.of("Scroll tiers up")).active = overviewScroll > 0;
                    footer(6, "Scroll tiers down", "arrow", () -> scrollOverview(42), () -> List.of("Scroll tiers down")).active = overviewScroll < maxScroll();
                }
            } else {
                footer(2, "Progress filter", "hopper", () -> { filter = (filter + 1) % FILTERS.length; page = 0; filterRows(); rebuildSlots(); }, () -> List.of("\u00a7e" + FILTERS[filter], "Change progress filter"));
                footer(3, "Sort collections", "comparator", () -> { sort = (sort + 1) % SORTS.length; page = 0; filterRows(); rebuildSlots(); }, () -> List.of("\u00a7e" + SORTS[sort], "Change sorting"));
                footer(5, "Previous page", "arrow", () -> changePage(-1), () -> List.of("Previous page")).active = page > 0;
                footer(6, "Next page", "arrow", () -> changePage(1), () -> List.of("Next page")).active = page + 1 < pages();
                for (int i = page * PAGE_SIZE; i < Math.min(rows.size(), (page + 1) * PAGE_SIZE); i++) {
                    var entry = rows.get(i); int index = i % PAGE_SIZE;
                    slot(gridX + index % 7 * cell, gridY + index / 7 * cell, cell - 2, entry.name, CollectionIcons.block(entry.id), false,
                            () -> { selected = entry; rebuild(); }, () -> {
                                int completed = CollectionProgress.completed(entry.id, count(entry));
                                return completed == 0 ? "" : TIERS[completed - 1];
                            }, () -> tooltip(entry));
                }
            }
        }
    }
    private CollectionSlotButton footer(int col, String name, String icon, Runnable action, Supplier<List<String>> details) {
        return slot(x + 12 + col * ((panelWidth - 46) / 8), footerY, 22, name, CollectionIcons.item(icon), false, action, () -> "", details);
    }
    private List<String> tooltip(Entry entry) {
        var lines = new ArrayList<String>(); int tier = CollectionProgress.completed(entry.id, count(entry));
        lines.add("\u00a7e" + entry.name + (tier == 0 ? "" : " " + TIERS[tier - 1]));
        lines.add(numbers.format(count(entry)) + " blocks mined"); lines.add(rank(entry));
        lines.add("\u00a77Global ranking among synced MMM players");
        if (CollectionIcons.unavailable(entry.id)) lines.add("\u00a77Not available as a block in Minecraft 1.12.2");
        var goals = CollectionProgress.milestones(entry.id);
        for (int i = 0; i < goals.size(); i++) lines.add((count(entry) >= goals.get(i) ? "\u00a7a" : "\u00a77") + TIERS[i] + "  " + numbers.format(goals.get(i)));
        return lines;
    }
    private String rank(Entry entry) {
        if (minecraft.player == null) return "Website rank unavailable";
        if (selected == null) {
            if (!rankHoverId.equals(entry.id)) { rankHoverId = entry.id; rankHoverStarted = System.nanoTime(); }
            if (System.nanoTime() - rankHoverStarted < 250_000_000L) return "\u00a77Loading website rank...";
        }
        String name = WebsiteLinkManager.isCurrentPlayerLinked() ? WebsiteLinkManager.getPersistedUsername() : "";
        if (name.isBlank()) name = minecraft.player.getGameProfile().getName();
        return CollectionRankText.format(CollectionRankClient.get(entry.id, name));
    }
    private int detailY() { return gridY + Math.max(16, (footerY - gridY - 52) / 2); }
    private void detailSlots() {
        var goals = CollectionProgress.milestones(selected.id); int span = (panelWidth - 24) / goals.size();
        int size = Math.min(36, span - 8);
        for (int i = 0; i < goals.size(); i++) {
            long goal = goals.get(i); int tier = i;
            slot(x + 12 + i * span + (span - size) / 2, detailY(), size, "Tier " + TIERS[i], CollectionIcons.pane(count(selected) >= goal), false,
                    () -> {}, () -> "", () -> List.of("\u00a7e" + selected.name + " " + TIERS[tier], numbers.format(count(selected)) + " / " + numbers.format(goal) + " blocks",
                            count(selected) >= goal ? "\u00a7aCompleted" : "\u00a7cIn progress"));
        }
    }
    private void back() {
        if (selected != null) { selected = null; rebuild(); }
        else if (category != null) { category = null; search = ""; page = 0; rebuild(); }
        else close();
    }
    private void changePage(int amount) { page = Math.clamp(page + amount, 0, pages() - 1); rebuildSlots(); }
    @Override public void tick() {
        super.tick();
        if (++ticks % 20 == 0) { refresh(); rebuildSlots(); }
    }
    @Override public void removed() { super.removed(); closed = true; loading = false; }
    @Override public void render(int mx, int my, float delta) {
        draw.fill(0, 0, width, height, 0xA0000000);
        draw.fill(x, y, x + panelWidth, y + panelHeight, 0xFF373737);
        draw.drawBorder(x, y, panelWidth, panelHeight, 0xFF111111);
        draw.fill(x + 2, y + 2, x + panelWidth - 2, y + 4, 0xFFBA303A);
        String title = selected != null ? selected.name + " Collection" : category == null ? "MMM Collections" : category.label + " Collections " + (page + 1) + "/" + pages();
        fit(title, x + 12, y + 13, panelWidth - 58, 0xFFEAEAEA);
        fit(scope(), x + 12, y + 32, panelWidth - 24, 0xFFBBBBBB);
        draw.fill(x + 12, footerY - 6, x + panelWidth - 12, footerY - 5, 0xFF515155);
        if (selected != null) renderDetail();
        else if (category == null) renderOverview();
        else if (rows.isEmpty()) center("No matching collections", x, gridY + 2 * cell, panelWidth, 0xFFBBBBBB);
        super.render(mx, my, delta);
        if (category == null && selected == null && mx >= x + 12 && mx < x + panelWidth - 12 && my >= overviewTop() && my < overviewBottom()) {
            int span = (panelWidth - 30) / columns(), col = (mx - x - 12) / span;
            int index = ((my - overviewTop() + overviewScroll) / OVERVIEW_ROW_HEIGHT) * columns() + col;
            if (col < columns() && index < tierTotals.length) renderTooltip(List.of(
                    "\u00a7e" + CollectionProgress.targetLabel(CollectionProgress.OVERVIEW_MILESTONES.get(index)) + " blocks",
                    tierCompletions[index] + " / " + tierTotals[index] + " collections completed",
                    tierTotals[index] > 0 && tierCompletions[index] == tierTotals[index] ? "\u00a7aCompleted" : "\u00a7cNot completed"), mx, my);
        }
    }
    private void renderDetail() {
        draw.drawItem(CollectionIcons.block(selected.id), x + 12, y + 49);
        fit(numbers.format(count(selected)) + " blocks mined", x + 38, y + 52, panelWidth - 50, 0xFFEAEAEA);
        fit(rank(selected), x + 12, y + 65, panelWidth - 24, 0xFFEAEAEA);
        var goals = CollectionProgress.milestones(selected.id); int span = (panelWidth - 24) / goals.size();
        int size = Math.min(36, span - 8);
        for (int i = 0; i < goals.size(); i++) {
            int bx = x + 12 + i * span; long goal = goals.get(i);
            center(TIERS[i], bx, detailY() - 13, span, 0xFFEAEAEA);
            center(CollectionProgress.targetLabel(goal), bx, detailY() + size + 5, span, 0xFFEAEAEA);
            center(count(selected) >= goal ? "Done" : String.format(Locale.ROOT, "%.1f%%", count(selected) * 100.0 / goal),
                    bx, detailY() + size + 18, span, count(selected) >= goal ? 0xFF77DD88 : 0xFFBBBBBB);
        }
    }
    private int columns() { return Math.max(3, (panelWidth - 30) / 52); }
    private int overviewTop() { return gridY + 16; }
    private int overviewBottom() { return footerY - 47; }
    private int maxScroll() { return Math.max(0, ((tierTotals.length + columns() - 1) / columns()) * OVERVIEW_ROW_HEIGHT - (overviewBottom() - overviewTop())); }
    private void scrollOverview(int amount) { overviewScroll = Math.clamp(overviewScroll + amount, 0, maxScroll()); rebuildSlots(); }
    private void renderOverview() {
        fit("Completed collections", x + 12, gridY + 2, panelWidth - 24, 0xFFEAEAEA);
        int span = (panelWidth - 30) / columns();
        scissor(x + 12, overviewTop(), x + panelWidth - 12, overviewBottom());
        try {
            for (int i = 0; i < tierTotals.length; i++) {
                int bx = x + 12 + i % columns() * span, top = overviewTop() + i / columns() * OVERVIEW_ROW_HEIGHT - overviewScroll;
                if (top + OVERVIEW_ROW_HEIGHT <= overviewTop() || top >= overviewBottom()) continue;
                boolean done = tierTotals[i] > 0 && tierTotals[i] == tierCompletions[i];
                draw.drawItem(CollectionIcons.pane(done), bx + span / 2 - 8, top);
                center(CollectionProgress.targetLabel(CollectionProgress.OVERVIEW_MILESTONES.get(i)), bx, top + 18, span, 0xFFBBBBBB);
                center(tierCompletions[i] + "/" + tierTotals[i], bx, top + 29, span, done ? 0xFF77DD88 : 0xFFEAEAEA);
            }
        } finally { endScissor(); }
        if (maxScroll() > 0) {
            int track = overviewBottom() - overviewTop(), thumb = Math.max(10, track * track / (track + maxScroll()));
            int top = overviewTop() + (track - thumb) * overviewScroll / maxScroll();
            draw.fill(x + panelWidth - 11, overviewTop(), x + panelWidth - 9, overviewBottom(), 0xFF222225);
            draw.fill(x + panelWidth - 11, top, x + panelWidth - 9, top + thumb, 0xFFBBBBBB);
        }
        fit("Categories", x + 12, footerY - 43, panelWidth - 24, 0xFFBBBBBB);
    }
    @Override protected void scroll(int mx, int my, int direction) {
        if (selected != null || mx < x || mx >= x + panelWidth || my < gridY || my >= footerY) return;
        if (category == null) scrollOverview(-direction * OVERVIEW_ROW_HEIGHT); else changePage(-direction);
    }
}
