package com.mmm.legacy.collection;

import com.mmm.ui.CollectionBoardState;
import com.mmm.legacy.LegacyDrawContext;

import com.mmm.config.Configs;
import com.mmm.leaderboard.BlockRankingCategory;
import com.mmm.storage.WorldIdentity;
import com.mmm.storage.WorldSessionContext;
import com.mmm.tracker.CollectionProgress;
import com.mmm.tracker.CollectionRankClient;
import com.mmm.tracker.PinnedCollection;
import com.mmm.tracker.CollectionWebsiteClient;
import com.mmm.sync.WebsiteLinkManager;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Supplier;
import static com.mmm.legacy.BoardArt.*;
import static com.mmm.legacy.collection.CollectionSlotButton.Kind.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

public final class CollectionBookScreen extends CollectionScreen
{
    private record BlockEntry(String id, String name, ItemStack icon) {}
    private final List<BlockEntry> catalog = new ArrayList<>();
    private final Map<BlockRankingCategory, List<String>> categoryHints = new EnumMap<>(BlockRankingCategory.class);
    private List<BlockEntry> rows = List.of();
    private Map<String, Long> counts = Map.of();
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);
    private BlockRankingCategory category;
    private BlockEntry selected;
    private boolean closed, allWorlds;
    private CollectionWebsiteClient.Snapshot website;
    private String websitePlayer = "";
    private boolean websiteLoading, websiteFailed;
    private long nextWebsiteRefresh;
    private int sort, filter, page, ticks, x, y, panelWidth, panelHeight, gridY, gridX, bodyWidth, footerY, startedCount, masteredCount;
    private final long[] tierCompletions = new long[CollectionProgress.OVERVIEW_MILESTONES.size()];
    private final long[] tierTotals = new long[CollectionProgress.OVERVIEW_MILESTONES.size()];
    private int overviewScroll;
    private String rankHoverId = "";
    private long rankHoverStarted;
    private static final int OVERVIEW_ROW_HEIGHT = 29;
    private String search = "";
    private static final String[] SORTS = { "Nearest milestone", "Most mined", "Block name" };
    private static final String[] FILTERS = { "All collections", "Started collections", "Completed collections" };
    private int detailPage;
    private final CollectionBoardState board = new CollectionBoardState();
    private CollectionSlotButton focusedSlot;
    private boolean keyboardNavigation;
    private int lastMouseX = Integer.MIN_VALUE, lastMouseY = Integer.MIN_VALUE;
    private long inspectedAt;
    private long viewOpened;
    private long earnedAt, rollAt, rollFrom, rollTo;
    private long displayedCount(long now) {
        float t = CollectionBoardState.reveal(now - rollAt, 0);
        return t >= 1 ? rollTo : rollFrom + Math.round((rollTo - rollFrom) * (double)t);
    }
    private int firstEarned, lastEarned;

    public CollectionBookScreen(Screen parent) {
        super(parent);
        filter = Configs.Generic.COLLECTION_FILTER.getIntegerValue();
        sort = Configs.Generic.COLLECTION_SORT.getIntegerValue();
        allWorlds = Configs.Generic.COLLECTION_GLOBAL.getBooleanValue();
        int tab = Configs.Generic.COLLECTION_CATEGORY.getIntegerValue();
        category = tab == 0 ? null : BlockRankingCategory.values()[tab - 1];
    }
    private void rememberCategory(BlockRankingCategory group) {
        category = group;
        Configs.Generic.COLLECTION_CATEGORY.setIntegerValue(group == null ? 0 : group.ordinal() + 1);
        Configs.requestSave();
    }

    @Override public void init() {
        super.init(); closed = false;
        viewOpened = now();
        firstEarned = lastEarned = 0;
        panelWidth = Math.min(586, width - 12);
        panelHeight = Math.min(345, height - 12);
        x = (width - panelWidth) / 2; y = (height - panelHeight) / 2;
        gridX = x + (sideRail() ? 107 : 9);
        gridY = y + (sideRail() ? 42 : 65);
        bodyWidth = x + panelWidth - 9 - gridX;
        footerY = y + panelHeight - 24;
        if (selected != null) detailPage = Math.min(detailPage, detailPages() - 1);
        overviewScroll = Math.clamp(overviewScroll, 0, maxOverviewScroll());
        if (catalog.isEmpty()) {
            var added = new HashSet<String>();
            for (String id : CollectionCatalog.blockIds()) {
                String group = CollectionProgress.collectionId(id);
                if (added.add(group)) catalog.add(new BlockEntry(group, CollectionIcons.name(group), CollectionIcons.block(group)));
            }
        }
        boolean wasEditing = searchField != null && searchField.isVisible() && searchField.isFocused();
        searchField = search(gridX, gridY, bodyWidth, search);
        searchField.setVisible(selected == null);
        searchField.setFocused(selected == null && wasEditing);
        refresh(); rebuildSlots();
        if (selected != null) rollFrom = rollTo = count(selected);
    }

    @Override protected void searchChanged(String value) {
        search = value; page = 0;
        if (!value.isBlank() && category == null) category = BlockRankingCategory.ALL;
        filterRows(); rebuildSlots();
    }
    private static String tierLabel(int tier) {
        return switch(tier) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> Integer.toString(tier); };
    }
    private void refresh() {
        refreshWebsite();
        var sources = new ArrayList<CollectionProgress.Source>();
        for (var world : Configs.WORLD_STATS) {
            String id = WorldIdentity.canonicalWorldId(world.worldId, world.kind, world.host);
            if (allWorlds || id.equals(WorldSessionContext.getCurrentWorldId()))
                sources.add(new CollectionProgress.Source(id, world.blockBreakdown));
        }
        counts = allWorlds && website != null ? com.mmm.tracker.LiveCollections.counts(website)
                : com.mmm.tracker.LiveCollections.localCounts(CollectionProgress.aggregate(sources), allWorlds ? null : WorldSessionContext.getCurrentWorldId());
        categoryHints.clear();
        Arrays.fill(tierCompletions, 0);
        Arrays.fill(tierTotals, 0);
        startedCount = masteredCount = 0;
        for (var entry : catalog) {
            if (count(entry) > 0) startedCount++;
            if (CollectionProgress.next(entry.id, count(entry)) == 0) masteredCount++;
            var goals = CollectionProgress.milestones(entry.id);
            for (long goal : goals) {
                int tier = CollectionProgress.OVERVIEW_MILESTONES.indexOf(goal);
                tierTotals[tier]++;
                if (count(entry) >= goal) tierCompletions[tier]++;
            }
        }
        List<BlockEntry> before = rows;
        filterRows();
        if (!before.equals(rows) && selected == null) rebuildSlots();
    }
    private void refreshWebsite() {
        String player = WebsiteLinkManager.isCurrentPlayerLinked() ? Configs.websiteLinkedMinecraftUuid : "";
        if (!player.equals(websitePlayer)) {
            websitePlayer = player; website = null; websiteLoading = false; websiteFailed = false; nextWebsiteRefresh = 0;
        }
        if (!allWorlds || player.isBlank() || websiteLoading || System.currentTimeMillis() < nextWebsiteRefresh) return;
        String username = WebsiteLinkManager.getPersistedUsername();
        if (username.isBlank()) username = minecraft.player.getGameProfile().getName();
        websiteLoading = true;
        CollectionWebsiteClient.load(player, username).whenComplete((result, failure) -> minecraft.executeTask(() -> {
            if (closed || !player.equals(websitePlayer) || !player.equals(Configs.websiteLinkedMinecraftUuid)) return;
            websiteLoading = false; websiteFailed = failure != null;
            nextWebsiteRefresh = System.currentTimeMillis() + (failure == null ? 60_000 : 30_000);
            if (failure == null) website = result;
            refresh(); rebuildSlots();
            if (selected != null) rollFrom = rollTo = count(selected);
        }));
    }
    private String scopeLabel() {
        if (!allWorlds) return "This World / " + WorldSessionContext.getCurrentWorldName();
        if (websiteLoading) return website == null ? "Global / loading website..." : "Global / refreshing website...";
        if (website != null) return "Global / website + unsynced mining" + (websiteFailed ? " (cached)" : "");
        return websitePlayer.isBlank() ? "Global / local only (not linked)" : "Global / local only (unavailable)";
    }
    private void filterRows() {
        String query = search.strip().toLowerCase(Locale.ROOT);
        Comparator<BlockEntry> order = sort == 0 ? Comparator.comparingLong(entry -> CollectionProgress.remaining(entry.id, count(entry)))
                : sort == 1 ? Comparator.<BlockEntry>comparingLong(this::count).reversed()
                : Comparator.comparing(BlockEntry::name, String.CASE_INSENSITIVE_ORDER);
        rows = catalog.stream().filter(entry -> category == null || category.includes(entry.id))
                .filter(entry -> entry.name.toLowerCase(Locale.ROOT).contains(query) || entry.id.contains(query))
                .filter(entry -> filter == 0 || filter == 1 && count(entry) > 0 || filter == 2 && CollectionProgress.next(entry.id, count(entry)) == 0)
                .sorted(order.thenComparing(BlockEntry::name)).toList();
        page = Math.min(page, pages() - 1);
    }
    private boolean sideRail() { return panelWidth >= 460 && panelHeight >= 279; }
    private int entryColumns() { return Math.max(1, Math.min(3, bodyWidth / 137)); }
    private int entryTop() { return gridY + 39; }
    private int pageSize() { return entryColumns() * Math.max(1, (footerY - 5 - entryTop()) / 32); }
    private int pages() { return Math.max(1, (rows.size() + pageSize() - 1) / pageSize()); }
    private long count(BlockEntry block) { return counts.getOrDefault(block.id, 0L); }
    private void rebuildSlots() {
        buttons.clear();
        slots.clear();
        categorySlots();
        if (selected != null) detailSlots();
        else if (category != null) blockSlots();

        action(x + panelWidth - 100, y + 7, 69, "World scope", allWorlds ? "Global" : "This world", () -> {
            allWorlds = !allWorlds; board.reset();
            Configs.Generic.COLLECTION_GLOBAL.setBooleanValue(allWorlds); Configs.requestSave();
            page = 0; firstEarned = lastEarned = 0; refresh(); rebuildSlots();
            if (selected != null) rollFrom = rollTo = count(selected);
        }, () -> List.of((scopeLabel()), (allWorlds ? "Switch to this world" : "Switch to global totals")));
        action(x + panelWidth - 26, y + 7, 18, "Close", "X", this::close, () -> List.of(("Close collections")));
        int left = x + 8;
        action(left, footerY, 32, "Back", "Back", this::back, () -> List.of(("Go back")));
        left += 36;
        if (selected != null) {
            boolean pinned = PinnedCollection.matches(selected.id, allWorlds);
            var pinButton = action(left, footerY, 44, pinned ? "Unpin collection" : "Pin collection", pinned ? "Unpin" : "Pin", () -> {
                if (PinnedCollection.matches(selected.id, allWorlds)) PinnedCollection.unpin();
                else PinnedCollection.pin(selected.id, allWorlds, website);
                rebuildSlots();
            }, () -> List.of(("Track this collection on the HUD")));
            pinButton.select(pinned);
            pinButton.focusKey = "Pin collection";
            action(left + 48, footerY, 42, "Share collection", "Share",
                    () -> minecraft.openScreen(new CollectionShareScreen(this, selected.name, selected.id, count(selected),
                            allWorlds ? "Global Total" : WorldSessionContext.getCurrentWorldName(), selected.icon)),
                    () -> List.of(("Save image or copy progress")));
        } else {
            if (category != null) {
                action(left, footerY, 64, "Progress filter", new String[]{"All states", "Started", "Completed"}[filter], () -> {
                    filter = (filter + 1) % FILTERS.length;
                    Configs.Generic.COLLECTION_FILTER.setIntegerValue(filter); rememberCategory(category);
                    page = 0; filterRows(); rebuildSlots();
                }, () -> List.of(("Filter: " + FILTERS[filter])));
                left += 67;
                action(left, footerY, 64, "Sort collections", new String[]{"Nearest", "Most mined", "Name"}[sort], () -> {
                    sort = (sort + 1) % SORTS.length;
                    Configs.Generic.COLLECTION_SORT.setIntegerValue(sort); rememberCategory(category);
                    page = 0; filterRows(); rebuildSlots();
                }, () -> List.of(("Sort: " + SORTS[sort])));
                left += 67;
            }
            if (PinnedCollection.hasPin()) action(left, footerY, 34, "Unpin collection", "Unpin",
                    () -> { PinnedCollection.unpin(); rebuildSlots(); }, () -> List.of(("Remove HUD pin")));
        }
        boolean overview = selected == null && category == null;
        int current = selected != null ? detailPage : overview ? overviewScroll : page;
        int last = selected != null ? detailPages() - 1 : overview ? maxOverviewScroll() : pages() - 1;
        if (last > 0) {
            for (int d : new int[]{-1, 1}) {
                String name = selected != null ? (d < 0 ? "Previous milestones" : "Next milestones")
                        : overview ? (d < 0 ? "Scroll tiers up" : "Scroll tiers down") : (d < 0 ? "Previous page" : "Next page");
                action(x + panelWidth - (d < 0 ? 49 : 27), footerY, 19, name, d < 0 ? "<" : ">", () -> {
                    if (selected != null) changeDetailPage(d);
                    else if (overview) scrollOverview(d * OVERVIEW_ROW_HEIGHT);
                    else changePage(d);
                }, () -> List.of((name))).active = d < 0 ? current > 0 : current < last;
            }
        }
        restoreSlotFocus();
    }
    private CollectionSlotButton action(int sx, int sy, int w, String name, String caption, Runnable action, Supplier<List<String>> hint) {
        return add(new CollectionSlotButton(sx, sy, w, 18, ACTION, name, caption, ItemStack.EMPTY, () -> "", action, hint, 0, 0));
    }
    private CollectionSlotButton add(CollectionSlotButton slot) { slots.add(slot); return addButton(slot); }
    private void categorySlots() {
        var groups = BlockRankingCategory.values();
        for (int i = 0; i <= groups.length; i++) {
            var group = i == 0 ? null : groups[i - 1];
            String caption = group == null ? "Overview" : group.label;
            String name = group == null ? "Collection progress" : group.label + " collections";
            ItemStack icon = group == null ? ItemStack.EMPTY : CollectionIcons.item(group.icon);
            int step = (panelWidth - 16) / (groups.length + 1);
            int sx = sideRail() ? x + 5 : x + 8 + i * step;
            int sy = sideRail() ? y + 51 + i * 19 : y + 34;
            var tab = add(new CollectionSlotButton(sx, sy, sideRail() ? 93 : step - 2, sideRail() ? 18 : 22,
                    TAB, name, sideRail() ? caption : "", icon, () -> "", () -> {
                        rememberCategory(group); selected = null; page = detailPage = 0; search = ""; rebuild();
                    }, () -> group == null ? List.of(("Collection overview"), (startedCount + " discovered / " + masteredCount + " mastered")) : categoryTooltip(group), viewOpened, 0));
            tab.select(group == category);
        }
    }
    private List<String> categoryTooltip(BlockRankingCategory group) {
        return categoryHints.computeIfAbsent(group, key -> {
            int total = 0, started = 0, mastered = 0;
            for (var entry : catalog) if (key.includes(entry.id)) {
                total++;
                long value = count(entry);
                if (value > 0) started++;
                if (CollectionProgress.next(entry.id, value) == 0) mastered++;
            }
            return List.of((key.label + " collections"),
                    (started + " / " + total + " discovered"),
                    (mastered + " mastered"));
        });
    }
    private void blockSlots() {
        int limit = pageSize(), cols = entryColumns(), span = bodyWidth / cols;
        for (int i = page * limit; i < Math.min(rows.size(), (page + 1) * limit); i++) {
            int pos = i % limit;
            var entry = rows.get(i);
            add(new CollectionSlotButton(gridX + pos % cols * span, entryTop() + pos / cols * 32, span - 2, 30,
                    ENTRY, entry.name, entry.name, entry.icon, () -> numbers.format(count(entry)) + " mined", () -> {
                        selected = entry; board.reset(); inspectedAt = now();
                        int completed = CollectionProgress.completed(entry.id, count(entry));
                        detailPage = Math.min(detailPages() - 1, completed / detailPageSize());
                        rebuild();
                    }, () -> blockTooltip(entry), viewOpened, pos / cols)).focusKey = entry.id;
        }
    }
    private List<String> blockTooltip(BlockEntry block) {
        long value = count(block), next = CollectionProgress.next(block.id, value);
        int tier = CollectionProgress.completed(block.id, value);
        var lines = new ArrayList<>(List.of((block.name), (numbers.format(value) + " mined"),
                (next == 0 ? "All milestones unlocked" : numbers.format(next - value) +
                        " to Tier " + tierLabel(tier + 1)), rankText(block)));
        if (CollectionIcons.unavailable(block.id)) lines.add("Not available as a block in Minecraft 1.12.2");
        return lines;
    }
    private boolean wideInspector() { return sideRail() && bodyWidth >= 378; }
    private int tierBoardWidth() { return bodyWidth - (wideInspector() ? 154 : 0); }
    private int detailColumns() { return Math.max(2, Math.min(5, tierBoardWidth() / 77)); }
    private int detailTop() { return gridY + 76; }
    private int detailRowHeight() { return sideRail() ? 39 : 27; }
    private int detailRows() { return Math.max(1, (footerY - 5 - detailTop()) / detailRowHeight()); }
    private int detailPageSize() { return detailColumns() * detailRows(); }
    private int detailPages() {
        return Math.max(1, (CollectionProgress.milestones(selected.id).size() + detailPageSize() - 1) / detailPageSize());
    }
    private void changeDetailPage(int amount) {
        int next = Math.clamp(detailPage + amount, 0, detailPages() - 1);
        if (next == detailPage) return;
        detailPage = next;
        viewOpened = now();
        rebuildSlots();
    }
    private int detailPaneX(int index) {
        return gridX + (index % detailColumns()) * (tierBoardWidth() / detailColumns());
    }
    private int detailPaneY(int index) { return detailTop() + (index % detailPageSize() / detailColumns()) * detailRowHeight(); }
    private void detailSlots() {
        var goals = CollectionProgress.milestones(selected.id);
        int completed = CollectionProgress.completed(selected.id, count(selected));
        for (int i = detailPage * detailPageSize(); i < Math.min(goals.size(), (detailPage + 1) * detailPageSize()); i++) {
            int tier = i + 1; long goal = goals.get(i);
            String label = "Tier " + tierLabel(tier);
            var slot = add(new CollectionSlotButton(detailPaneX(i), detailPaneY(i), tierBoardWidth() / detailColumns() - 3, detailRowHeight() - 3,
                    MILESTONE, label, label, ItemStack.EMPTY, () -> CollectionProgress.targetLabel(goal), () -> {
                        board.toggleTier(tier);
                        inspectedAt = now();
                        markInspection(completed, goals.size());
                    }, () -> {
                        long value = count(selected);
                        return List.of((selected.name + " / " + label),
                                ("Milestone: " + numbers.format(goal) + " blocks"),
                                (value >= goal ? "Unlocked" : (tier == completed + 1 ? "Next: " : "Locked: ") + numbers.format(goal - value) + " blocks to go"));
                    }, viewOpened, i % detailPageSize()));
            slot.earned = completed;
            slot.earnedAt = tier >= firstEarned && tier <= lastEarned ? earnedAt : -1;
            slot.tier = tier;
        }
        markInspection(completed, goals.size());
    }
    private void markInspection(int completed, int total) {
        int shown = board.displayedTier(completed,total);
        for (var slot : slots) if (slot.tier > 0) slot.select(slot.tier == shown);
    }
    private String rankText(BlockEntry block) {
        if (minecraft == null || minecraft.player == null) return ("Website rank unavailable");
        if (selected == null) {
            long now = System.nanoTime();
            if (!rankHoverId.equals(block.id)) { rankHoverId = block.id; rankHoverStarted = now; }
            if (now - rankHoverStarted < 250_000_000L) return ("Loading website rank...");
        }
        String username = WebsiteLinkManager.isCurrentPlayerLinked() ? WebsiteLinkManager.getPersistedUsername() : "";
        if (username.isBlank()) username = minecraft.player.getGameProfile().getName();
        var rank = CollectionRankClient.get(block.id, username);
        return CollectionRankText.format(rank);
    }
    private void back() {
        if (selected != null) { selected = null; rebuild(); }
        else if (category != null) { rememberCategory(null); search = ""; page = 0; rebuild(); }
        else close();
    }
    private void changePage(int amount) {
        int next = Math.clamp(page + amount, 0, pages() - 1);
        if (page == next) return;
        page = next; viewOpened = now(); rebuildSlots();
    }
    @Override public void tick() {
        super.tick();
        if (++ticks % 20 == 0) {
            long oldCount = selected == null ? -1 : count(selected);
            refresh();
            if (selected != null && oldCount != count(selected)) {
                rollFrom = oldCount; rollTo = count(selected); rollAt = now();
                int before = CollectionProgress.completed(selected.id, oldCount);
                int after = CollectionProgress.completed(selected.id, count(selected));
                if (after > before) {
                    firstEarned = before + 1; lastEarned = after; earnedAt = now();
                }
                rebuildSlots();
            }
        }
    }
    @Override public void render(int mouseX, int mouseY, float delta) {
        if (lastMouseX != Integer.MIN_VALUE && (mouseX != lastMouseX || mouseY != lastMouseY)) keyboardNavigation = false;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        LegacyDrawContext context = draw;
        context.fill(0, 0, width, height, 0x97000000);
        context.fill(x, y, x + panelWidth, y + panelHeight, BASE);
        context.drawBorder(x,y,panelWidth,panelHeight,LINE);
        context.fill(x + 1,y + 1,x + panelWidth - 1,y + 31,BLACK);
        RedBookIcon.draw(context,x + 9,y + 9);
        context.drawText(textRenderer,"MMM",x + 30,y + 12,ROSE,true);
        context.drawText(textRenderer,"/ Collections",x + 54,y + 12,TEXT,false);
        if (sideRail()) {
            context.fill(x + 1, y + 32, gridX - 6, footerY - 5, RAIL);
            context.drawText(textRenderer, "CATALOG", x + 10, y + 38, MUTED, false);
        }
        context.fill(x + 1, footerY - 4, x + panelWidth - 1, footerY - 3, LINE);
        if (selected != null) drawCollectionDetail(context);
        else if (category == null) drawTierOverview(context);
        else {
            context.drawText(textRenderer, category.label + " / " + rows.size() + " blocks", gridX, gridY + 24, TEXT, false);
            right(context, (page + 1) + " / " + pages(), gridX + bodyWidth, gridY + 24, MUTED);
            if (rows.isEmpty()) context.drawText(textRenderer, "No matching blocks", gridX, entryTop() + 10, MUTED, false);
        }
        super.render(mouseX, mouseY, delta);
        if (searchField.isVisible() && !searchField.isFocused() && search.isEmpty()) fit("Search blocks...",gridX+5,gridY+5,bodyWidth-10,MUTED);
    }
    private void right(LegacyDrawContext c, String text, int edge, int yy, int color) {
        c.drawText(textRenderer, text, edge - textRenderer.getWidth(text), yy, color, false);
    }
    private int overviewColumns() { return Math.max(1, Math.min(3, bodyWidth / 140)); }
    private int overviewTop() { return gridY + 65; }
    private int overviewBottom() { return footerY - 7; }
    private int maxOverviewScroll() {
        int rows = (tierTotals.length + overviewColumns() - 1) / overviewColumns();
        return Math.max(0, rows * OVERVIEW_ROW_HEIGHT - (overviewBottom() - overviewTop()));
    }
    private void scrollOverview(int amount) {
        overviewScroll = Math.clamp(overviewScroll + amount, 0, maxOverviewScroll());
        rebuildSlots();
    }
    private void drawTierOverview(LegacyDrawContext context) {
        int columns = overviewColumns(), span = (bodyWidth - 6) / columns;
        int row = gridY + 25;
        context.drawText(textRenderer, "Discovered", gridX, row, MUTED, false);
        context.drawText(textRenderer, startedCount + " / " + catalog.size(), gridX, row + 12, TEXT, true);
        context.drawText(textRenderer, "Mastered", gridX + bodyWidth / 2, row, MUTED, false);
        context.drawText(textRenderer, Integer.toString(masteredCount), gridX + bodyWidth / 2, row + 12, TEXT, true);
        context.drawText(textRenderer, "Milestone ledger", gridX, overviewTop()-12, TEXT, false);
        long elapsed = now() - viewOpened;
        scissor(gridX, overviewTop(), gridX + bodyWidth, overviewBottom());
        try { for (int i = 0; i < tierTotals.length; i++) {
            int xx = gridX + i % columns * span, yy = overviewTop() + i / columns * OVERVIEW_ROW_HEIGHT - overviewScroll;
            if (yy + OVERVIEW_ROW_HEIGHT <= overviewTop() || yy >= overviewBottom()) continue;
            int w = span - 3;
            com.mmm.ui.BoardEditorStyle.plate(context::fill, xx, yy, w, 26, TILE, LINE);
            String label = CollectionProgress.targetLabel(CollectionProgress.OVERVIEW_MILESTONES.get(i));
            context.drawText(textRenderer, label, xx + 5, yy + 5, TEXT, false);
            right(context, tierCompletions[i] + "/" + tierTotals[i], xx + w - 5, yy + 5, MUTED);
            float ratio = tierTotals[i] == 0 ? 0 : (float)tierCompletions[i]/tierTotals[i];
            meter(context,xx+5,yy+18,w-10,ratio * CollectionBoardState.reveal(elapsed,i % columns));
        }
        } finally { endScissor(); }
        if (maxOverviewScroll() > 0) {
            int track = overviewBottom() - overviewTop(), thumb = Math.max(8, track * track / (track + maxOverviewScroll()));
            int yy = overviewTop() + (track - thumb) * overviewScroll / maxOverviewScroll();
            context.fill(gridX + bodyWidth - 2,overviewTop(),gridX + bodyWidth,overviewBottom(),LINE);
            context.fill(gridX + bodyWidth - 2,yy,gridX + bodyWidth,yy + thumb,MUTED);
        }
    }
    private void drawCollectionDetail(LegacyDrawContext c) {
        long mined = count(selected), now = now(), target = CollectionProgress.next(selected.id,mined);
        int tier = CollectionProgress.completed(selected.id,mined);
        var goals = CollectionProgress.milestones(selected.id);
        float enter = CollectionBoardState.reveal(now - viewOpened,0);
        c.fill(gridX,gridY,gridX + 34,gridY + 34,TILE);
        var mat = c.getMatrices(); mat.push();
        try {
            mat.translate(gridX + 17,gridY + 17,0);
            float scale = 1.23f + .27f * enter;
            mat.scale(scale,scale,1); c.drawItem(selected.icon,-8,-8);
        } finally { mat.pop(); }
        fit(selected.name,gridX + 42,gridY + 1,bodyWidth - 45,TEXT);
        c.drawText(textRenderer,numbers.format(displayedCount(now)) + " blocks mined",gridX + 42,gridY + 13,TEXT,false);
        fit(rankText(selected),gridX+42,gridY+25,bodyWidth-45,MUTED);
        int shown = board.displayedTier(tier,goals.size());
        long goal = goals.get(shown - 1);
        String status = target == 0 ? "All milestones unlocked" : "Next: " + numbers.format(target - mined) + " more for " + CollectionProgress.targetLabel(target);
        boolean compactInspection = !wideInspector() && board.inspectedTier() != 0;
        if (compactInspection) status = "Tier " + tierLabel(shown) + ": " +
                (mined >= goal ? "unlocked" : numbers.format(goal - mined) + " to go");
        fit(status,gridX,gridY+41,bodyWidth-78,ROSE);
        right(c,tier + "/" + goals.size() + " tiers",gridX+bodyWidth,gridY+41,TEXT);
        long meterGoal = compactInspection ? goal : target;
        meter(c,gridX,gridY+53,bodyWidth,(meterGoal == 0 ? 1f : (float)displayedCount(now)/meterGoal) * enter);
        c.drawText(textRenderer,"Milestones",gridX,gridY+63,TEXT,false);
        if (detailPages() > 1) right(c,(detailPage+1) + " / " + detailPages(),gridX+tierBoardWidth(),gridY+63,MUTED);
        if (wideInspector()) {
            int left = gridX + tierBoardWidth() + 4, top = detailTop(), w = bodyWidth - tierBoardWidth() - 4;
            dossier(c,left,top,w,Math.min(124,footerY-top-5));
            float opening = CollectionBoardState.reveal(now - inspectedAt,0);
            int tx = left + 9 + Math.round(5 * (1 - opening));
            c.drawText(textRenderer,"MILESTONE RECORD",tx,top+11,MUTED,false);
            c.drawText(textRenderer,"Tier " + tierLabel(shown),tx,top+27,TEXT,true);
            right(c,shown <= tier ? "Earned" : shown == tier + 1 ? "Next" : "Locked",left+w-9,top+27,ROSE);
            c.fill(left+9,top+42,left+w-9,top+43,LINE);
            c.drawText(textRenderer,numbers.format(goal) + " blocks",tx,top+50,TEXT,false);
            meter(c,left+9,top+65,w-18,Math.min(1f,(float)displayedCount(now)/goal)*opening);
            c.drawText(textRenderer,mined >= goal ? "Milestone reached" : numbers.format(goal-mined) + " to go",tx,top+75,MUTED,false);
            int n = goals.size(), inner = w-18;
            for (int i = 0; i < n; i++) {
                int sx = left+9+i*inner/n, ex = left+9+(i+1)*inner/n-1;
                c.fill(sx,top+98,ex,top+102,i < tier ? RED : LINE);
                if (i+1 == shown) c.fill(sx,top+104,ex,top+106,ROSE);
            }
            c.drawText(textRenderer,"I",left+9,top+110,MUTED,false);
            right(c,tierLabel(n),left+w-9,top+110,MUTED);
        }
    }
    @Override protected void renderSlotTooltip(int mx, int my) {
        if (!keyboardNavigation) { super.renderSlotTooltip(mx, my); return; }
        if (searchField.isVisible() && searchField.isFocused()) {
            draw.drawBorder(gridX - 2, gridY - 2, bodyWidth + 4, 22, TEXT);
        } else if (focusedSlot != null) {
            focusedSlot.drawFocus(draw);
            if (!(focusedSlot.tier > 0 && focusedSlot.chosen))
                renderTooltip(focusedSlot.details.get(), focusedSlot.x + focusedSlot.getWidth() / 2, focusedSlot.y);
        }
    }
    private void restoreSlotFocus() {
        if (focusedSlot == null) return;
        for (var slot : slots) if (slot.active && slot.visible && slot.focusKey.equals(focusedSlot.focusKey)) {
            focusedSlot = slot;
            return;
        }
        focusedSlot = null;
        if (keyboardNavigation) {
            for (var slot : slots) if (slot.active && slot.visible && slot.chosen) {
                focusedSlot = slot;
                if (slot.tier > 0) break;
            }
        }
    }
    private void traverseFocus(boolean backwards) {
        // Search is stop zero; subsequent stops follow the board's widget order.
        int size = slots.size() + 1;
        int index = searchField.isVisible() && searchField.isFocused() ? 0
                : focusedSlot == null ? (backwards ? 0 : -1) : slots.indexOf(focusedSlot) + 1;
        for (int i = 0; i < size; i++) {
            index = Math.floorMod(index + (backwards ? -1 : 1), size);
            var slot = index == 0 ? null : slots.get(index - 1);
            if (slot == null ? !searchField.isVisible() : !slot.active || !slot.visible) continue;
            searchField.setFocused(slot == null);
            focusedSlot = slot;
            keyboardNavigation = true;
            return;
        }
    }
    @Override protected void keyPressed(char c, int key) {
        boolean editing = searchField.isVisible() && searchField.isFocused();
        if (key == Keyboard.KEY_ESCAPE || (!editing && key == minecraft.options.inventoryKey.getKeyCode())) {
            super.keyPressed(c, key);
        } else if (key == Keyboard.KEY_TAB) {
            traverseFocus(isShiftDown());
        } else if (!editing && focusedSlot != null && (key == Keyboard.KEY_RETURN
                || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_SPACE)) {
            keyboardNavigation = true;
            if (!Keyboard.isRepeatEvent() && focusedSlot.visible && focusedSlot.active) activate(focusedSlot);
        } else super.keyPressed(c, key);
    }
    private void activate(CollectionSlotButton slot) {
        slot.playClickSound(minecraft.getSoundManager());
        slot.press();
    }
    @Override protected void mouseClicked(int mx, int my, int button) {
        keyboardNavigation = false;
        focusedSlot = null;
        if (searchField.isVisible()) searchField.mouseClicked(mx, my, button);
        if (button != 0) return;
        // Vanilla walks the mutable button list after activation. A filter change may
        // insert new entries before the footer, dispatching this same click twice.
        for (CollectionSlotButton slot : slots) if (slot.mouseClicked(minecraft, mx, my)) {
            lastClickedButton = slot;
            focusedSlot = slot;
            activate(slot);
            return;
        }
    }
    @Override protected void scroll(int mx,int my,int direction) {
        if (mx < x || mx >= x + panelWidth || my < gridY || my >= footerY) return;
        if (selected != null) changeDetailPage(-direction);
        else if (category == null) scrollOverview(-direction * OVERVIEW_ROW_HEIGHT);
        else changePage(-direction);
    }
    @Override public void removed() { super.removed(); closed = true; websiteLoading = false; }
}
