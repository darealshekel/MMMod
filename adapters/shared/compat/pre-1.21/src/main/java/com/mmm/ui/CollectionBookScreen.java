package com.mmm.ui;

import com.mmm.config.Configs;
import com.mmm.leaderboard.BlockRankingCategory;
import com.mmm.storage.WorldIdentity;
import com.mmm.storage.WorldSessionContext;
import com.mmm.tracker.CollectionProgress;
import com.mmm.tracker.CollectionRankClient;
import com.mmm.tracker.PinnedCollection;
import com.mmm.tracker.CollectionWebsiteClient;
import com.mmm.sync.WebsiteLinkManager;
import com.mmm.util.BlockBreakdownCatalog;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Supplier;
import static com.mmm.ui.CollectionBoardArt.*;
import static com.mmm.ui.CollectionSlotButton.Kind.*;
import com.mmm.ui.BoardPlatform.Canvas;
import net.minecraft.client.gui.screen.Screen;
import com.mmm.ui.BoardPlatform.BoardField;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public final class CollectionBookScreen extends BoardPlatform.BoardScreen
{
    private record BlockEntry(String id, String name, ItemStack icon) {}
    private final Screen parent;
    private final List<BlockEntry> catalog = new ArrayList<>();
    private final Map<BlockRankingCategory, List<Text>> categoryHints = new EnumMap<>(BlockRankingCategory.class);
    private final List<CollectionSlotButton> slots = new ArrayList<>();
    private List<BlockEntry> rows = List.of();
    private Map<String, Long> counts = Map.of();
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);
    private BlockRankingCategory category;
    private BlockEntry selected;
    private boolean allWorlds = true;
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
    private BoardField searchField;
    private static final String[] SORTS = { "Nearest milestone", "Most mined", "Block name" };
    private static final String[] FILTERS = { "All collections", "Started collections", "Completed collections" };
    private int detailPage;
    private final CollectionBoardState board = new CollectionBoardState();
    private long inspectedAt;
    private long viewOpened;
    private long earnedAt, rollAt, rollFrom, rollTo;
    private long displayedCount(long now) {
        float t = CollectionBoardArt.reveal(now - rollAt, 0);
        return t >= 1 ? rollTo : rollFrom + Math.round((rollTo - rollFrom) * (double)t);
    }
    private int firstEarned, lastEarned;

    public CollectionBookScreen(Screen parent) {
        super(BoardPlatform.literal("MMM Collections"));
        this.parent = parent;
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

    @Override protected void init() {
        MmmUi.ensureCursorVisible();
        viewOpened = CollectionBoardArt.now();
        firstEarned = lastEarned = 0;
        panelWidth = Math.min(586, width - 12);
        panelHeight = Math.min(345, height - 12);
        x = (width - panelWidth) / 2; y = (height - panelHeight) / 2;
        gridX = x + (sideRail() ? 107 : 9);
        gridY = y + (sideRail() ? 42 : 65);
        bodyWidth = x + panelWidth - 9 - gridX;
        footerY = y + panelHeight - 24;
        if (selected != null) detailPage = Math.min(detailPage, detailPages() - 1);
        overviewScroll = net.minecraft.util.math.MathHelper.clamp(overviewScroll, 0, maxOverviewScroll());
        if (catalog.isEmpty()) {
            var added = new HashSet<String>();
            for (String id : BlockBreakdownCatalog.blockIds()) {
                String collection = CollectionProgress.collectionId(id);
                if (!added.add(collection)) continue;
                Identifier key = Identifier.tryParse(CollectionProgress.iconId(collection));
                if (key != null && BoardPlatform.BLOCK.containsId(key)) {
                    var block = BoardPlatform.BLOCK.get(key);
                    String name = CollectionProgress.customName(collection);
                    catalog.add(new BlockEntry(collection, name == null ? block.getName().getString() : name, new ItemStack(block.asItem())));
                }
            }
            catalog.add(new BlockEntry("mmm:non_insta_deepslate", "Non-insta Deepslate", new ItemStack(Items.DEEPSLATE)));
        }
        searchField = new BoardField(textRenderer, gridX, gridY, bodyWidth, 17, BoardPlatform.literal("Search collections"));
        searchField.setMaxLength(64);
        searchField.setPlaceholder(BoardPlatform.literal("Search blocks..."));
        searchField.setText(search);
        searchField.visible = selected == null;
        searchField.setChangedListener(value -> {
            search = value; page = 0;
            if (!value.isBlank() && category == null) rememberCategory(BlockRankingCategory.ALL);
            filterRows(); rebuildSlots();
        });
        addDrawableChild(searchField);
        refresh(); rebuildSlots();
        if (selected != null) rollFrom = rollTo = count(selected);
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
        if (!before.equals(rows)) rebuildSlots();
    }
    private void refreshWebsite() {
        String player = WebsiteLinkManager.isCurrentPlayerLinked() ? Configs.websiteLinkedMinecraftUuid : "";
        if (!player.equals(websitePlayer)) {
            websitePlayer = player; website = null; websiteLoading = false; websiteFailed = false; nextWebsiteRefresh = 0;
        }
        if (!allWorlds || player.isBlank() || websiteLoading || System.currentTimeMillis() < nextWebsiteRefresh) return;
        String username = WebsiteLinkManager.getPersistedUsername();
        if (username.isBlank()) username = client.player.getGameProfile().getName();
        websiteLoading = true;
        CollectionWebsiteClient.load(player, username).whenComplete((result, failure) -> client.execute(() -> {
            if (!player.equals(websitePlayer) || !player.equals(Configs.websiteLinkedMinecraftUuid)) return;
            websiteLoading = false; websiteFailed = failure != null;
            nextWebsiteRefresh = System.currentTimeMillis() + (failure == null ? 60_000 : 30_000);
            if (failure == null) website = result;
            refresh(); rebuildSlots();
            if (selected != null) rollFrom = rollTo = count(selected);
        }));
    }
    private String scopeLabel() {
        if (!allWorlds) return "This World";
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
        for (var slot : slots) remove(slot);
        slots.clear();
        categorySlots();
        if (selected != null) detailSlots();
        else if (category != null) blockSlots();

        action(x + panelWidth - 100, y + 7, 69, "World scope", allWorlds ? "Global" : "This world", () -> {
            allWorlds = !allWorlds; board.reset();
            Configs.Generic.COLLECTION_GLOBAL.setBooleanValue(allWorlds); Configs.requestSave();
            page = 0; firstEarned = lastEarned = 0; refresh(); rebuildSlots();
            if (selected != null) rollFrom = rollTo = count(selected);
        }, () -> List.of(BoardPlatform.literal(scopeLabel()), BoardPlatform.literal(allWorlds ? "Switch to this world" : "Switch to global totals")));
        action(x + panelWidth - 26, y + 7, 18, "Close", "X", this::close, () -> List.of(BoardPlatform.literal("Close collections")));
        int left = x + 8;
        action(left, footerY, 32, "Back", "Back", this::back, () -> List.of(BoardPlatform.literal("Go back")));
        left += 36;
        if (selected != null) {
            boolean pinned = PinnedCollection.matches(selected.id, allWorlds);
            action(left, footerY, 44, pinned ? "Unpin collection" : "Pin collection", pinned ? "Unpin" : "Pin", () -> {
                if (PinnedCollection.matches(selected.id, allWorlds)) PinnedCollection.unpin();
                else PinnedCollection.pin(selected.id, allWorlds, website);
                rebuildSlots();
            }, () -> List.of(BoardPlatform.literal("Track this collection on the HUD"))).select(pinned);
            action(left + 48, footerY, 42, "Share collection", "Share",
                    () -> client.setScreen(ProgressShareScreen.collection(this, selected.name, selected.id, count(selected),
                            allWorlds ? "Global Total" : WorldSessionContext.getCurrentWorldName(), selected.icon)),
                    () -> List.of(BoardPlatform.literal("Save image or copy progress")));
        } else {
            if (category != null) {
                action(left, footerY, 64, "Progress filter", new String[]{"All states", "Started", "Completed"}[filter], () -> {
                    filter = (filter + 1) % FILTERS.length;
                    Configs.Generic.COLLECTION_FILTER.setIntegerValue(filter); rememberCategory(category);
                    page = 0; filterRows(); rebuildSlots();
                }, () -> List.of(BoardPlatform.literal("Filter: " + FILTERS[filter])));
                left += 67;
                action(left, footerY, 64, "Sort collections", new String[]{"Nearest", "Most mined", "Name"}[sort], () -> {
                    sort = (sort + 1) % SORTS.length;
                    Configs.Generic.COLLECTION_SORT.setIntegerValue(sort); rememberCategory(category);
                    page = 0; filterRows(); rebuildSlots();
                }, () -> List.of(BoardPlatform.literal("Sort: " + SORTS[sort])));
                left += 67;
            }
            if (PinnedCollection.hasPin()) action(left, footerY, 34, "Unpin collection", "Unpin",
                    () -> { PinnedCollection.unpin(); rebuildSlots(); }, () -> List.of(BoardPlatform.literal("Remove HUD pin")));
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
                }, () -> List.of(BoardPlatform.literal(name))).active = d < 0 ? current > 0 : current < last;
            }
        }
    }
    private CollectionSlotButton action(int sx, int sy, int w, String name, String caption, Runnable action, Supplier<List<Text>> hint) {
        return add(new CollectionSlotButton(sx, sy, w, 18, ACTION, name, caption, ItemStack.EMPTY, () -> "", action, hint, 0, 0));
    }
    private CollectionSlotButton add(CollectionSlotButton slot) { slots.add(slot); return addDrawableChild(slot); }
    private void categorySlots() {
        var groups = BlockRankingCategory.values();
        for (int i = 0; i <= groups.length; i++) {
            var group = i == 0 ? null : groups[i - 1];
            String caption = group == null ? "Overview" : group.label;
            String name = group == null ? "Collection progress" : group.label + " collections";
            ItemStack icon = group == null ? ItemStack.EMPTY : new ItemStack(BoardPlatform.ITEM.get(new Identifier("minecraft", group.icon)));
            int step = (panelWidth - 16) / (groups.length + 1);
            int sx = sideRail() ? x + 5 : x + 8 + i * step;
            int sy = sideRail() ? y + 51 + i * 19 : y + 34;
            var tab = add(new CollectionSlotButton(sx, sy, sideRail() ? 93 : step - 2, sideRail() ? 18 : 22,
                    TAB, name, sideRail() ? caption : "", icon, () -> "", () -> {
                        rememberCategory(group); selected = null; page = detailPage = 0; search = ""; clearAndInit();
                    }, () -> group == null ? List.of(BoardPlatform.literal("Collection overview"), BoardPlatform.literal(startedCount + " discovered / " + masteredCount + " mastered")) : categoryTooltip(group), viewOpened, 0));
            tab.select(group == category);
        }
    }
    private List<Text> categoryTooltip(BlockRankingCategory group) {
        return categoryHints.computeIfAbsent(group, key -> {
            int total = 0, started = 0, mastered = 0;
            for (var entry : catalog) if (key.includes(entry.id)) {
                total++;
                long value = count(entry);
                if (value > 0) started++;
                if (CollectionProgress.next(entry.id, value) == 0) mastered++;
            }
            return List.of(BoardPlatform.literal(key.label + " collections"),
                    BoardPlatform.literal(started + " / " + total + " discovered").formatted(Formatting.GRAY),
                    BoardPlatform.literal(mastered + " mastered").formatted(Formatting.GRAY));
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
                        clearAndInit();
                    }, () -> blockTooltip(entry), viewOpened, pos / cols));
        }
    }
    private List<Text> blockTooltip(BlockEntry block) {
        long value = count(block), next = CollectionProgress.next(block.id, value);
        int tier = CollectionProgress.completed(block.id, value);
        return List.of(BoardPlatform.literal(block.name), BoardPlatform.literal(numbers.format(value) + " mined").formatted(Formatting.GRAY),
                BoardPlatform.literal(next == 0 ? "All milestones unlocked" : numbers.format(next - value) +
                        " to Tier " + CollectionBoardArt.tierLabel(tier + 1)), rankText(block));
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
        int next = net.minecraft.util.math.MathHelper.clamp(detailPage + amount, 0, detailPages() - 1);
        if (next == detailPage) return;
        detailPage = next;
        viewOpened = CollectionBoardArt.now();
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
            String label = "Tier " + CollectionBoardArt.tierLabel(tier);
            var slot = add(new CollectionSlotButton(detailPaneX(i), detailPaneY(i), tierBoardWidth() / detailColumns() - 3, detailRowHeight() - 3,
                    MILESTONE, label, label, ItemStack.EMPTY, () -> CollectionProgress.targetLabel(goal), () -> {
                        board.toggleTier(tier);
                        inspectedAt = now();
                        markInspection(completed, goals.size());
                    }, () -> {
                        long value = count(selected);
                        return List.of(BoardPlatform.literal(selected.name + " / " + label),
                                BoardPlatform.literal("Milestone: " + numbers.format(goal) + " blocks"),
                                BoardPlatform.literal(value >= goal ? "Unlocked" : (tier == completed + 1 ? "Next: " : "Locked: ") + numbers.format(goal - value) + " blocks to go"));
                    }, viewOpened, i % detailPageSize()));
            slot.setTier(i < completed ? CollectionSlotButton.Tier.EARNED : i == completed ? CollectionSlotButton.Tier.NEXT : CollectionSlotButton.Tier.LOCKED,
                    tier >= firstEarned && tier <= lastEarned ? earnedAt : -1);
            slot.tierNumber = tier;
        }
        markInspection(completed, goals.size());
    }
    private void markInspection(int completed, int total) {
        int shown = board.displayedTier(completed,total);
        for (var slot : slots) if (slot.tierNumber > 0) slot.inspect(slot.tierNumber == shown, inspectedAt);
    }
    private Text rankText(BlockEntry block) {
        if (client == null || client.player == null) return BoardPlatform.literal("Website rank unavailable").formatted(Formatting.GRAY);
        if (selected == null) {
            long now = System.nanoTime();
            if (!rankHoverId.equals(block.id)) { rankHoverId = block.id; rankHoverStarted = now; }
            if (now - rankHoverStarted < 250_000_000L) return BoardPlatform.literal("Loading website rank...").formatted(Formatting.GRAY);
        }
        String username = WebsiteLinkManager.isCurrentPlayerLinked() ? WebsiteLinkManager.getPersistedUsername() : "";
        if (username.isBlank()) username = client.player.getGameProfile().getName();
        var rank = CollectionRankClient.get(block.id, username);
        return CollectionRankText.format(rank);
    }
    private void back() {
        if (selected != null) { selected = null; clearAndInit(); }
        else if (category != null) { rememberCategory(null); search = ""; page = 0; clearAndInit(); }
        else close();
    }
    private void changePage(int amount) {
        int next = net.minecraft.util.math.MathHelper.clamp(page + amount, 0, pages() - 1);
        if (page == next) return;
        page = next; viewOpened = CollectionBoardArt.now(); rebuildSlots();
    }
    @Override public void tick() {
        if (++ticks % 20 == 0) {
            long oldCount = selected == null ? -1 : count(selected);
            refresh();
            if (selected != null && oldCount != count(selected)) {
                rollFrom = oldCount; rollTo = count(selected); rollAt = now();
                int before = CollectionProgress.completed(selected.id, oldCount);
                int after = CollectionProgress.completed(selected.id, count(selected));
                if (after > before) {
                    firstEarned = before + 1; lastEarned = after; earnedAt = CollectionBoardArt.now();
                }
                rebuildSlots();
            }
        }
    }
    @Override public void render(Canvas context, int mouseX, int mouseY, float delta) {
        context.raw().fill(0, 0, width, height, 0x97000000);
        context.raw().fill(x, y, x + panelWidth, y + panelHeight, BASE);
        context.raw().drawBorder(x,y,panelWidth,panelHeight,LINE);
        context.raw().fill(x + 1,y + 1,x + panelWidth - 1,y + 31,BLACK);
        RedBookIcon.draw(context,x + 9,y + 9);
        context.raw().drawText(textRenderer,"MMM",x + 30,y + 12,ROSE,true);
        context.raw().drawText(textRenderer,"/ Collections",x + 54,y + 12,TEXT,false);
        if (sideRail()) {
            context.raw().fill(x + 1, y + 32, gridX - 6, footerY - 5, RAIL);
            context.raw().drawText(textRenderer, "CATALOG", x + 10, y + 38, MUTED, false);
        }
        context.raw().fill(x + 1, footerY - 4, x + panelWidth - 1, footerY - 3, LINE);
        if (selected != null) drawCollectionDetail(context);
        else if (category == null) drawTierOverview(context);
        else {
            context.raw().drawText(textRenderer, category.label + " / " + rows.size() + " blocks", gridX, gridY + 24, TEXT, false);
            right(context, (page + 1) + " / " + pages(), gridX + bodyWidth, gridY + 24, MUTED);
            if (rows.isEmpty()) context.raw().drawText(textRenderer, "No matching blocks", gridX, entryTop() + 10, MUTED, false);
        }
        super.render(context, mouseX, mouseY, delta);
        boolean keyboard = BoardPlatform.keyboard();
        for (var slot : slots) if ((slot.tierNumber == 0 || !slot.isInspected()) &&
                (keyboard ? slot.isFocused() : slot.isMouseOver(mouseX, mouseY))) {
            context.raw().drawTooltip(textRenderer, slot.details.get(), keyboard ? slot.getX() : mouseX, keyboard ? slot.getY() : mouseY); break;
        }
    }
    private void right(Canvas c, String text, int edge, int yy, int color) {
        c.raw().drawText(textRenderer, text, edge - textRenderer.getWidth(text), yy, color, false);
    }
    private int overviewColumns() { return Math.max(1, Math.min(3, bodyWidth / 140)); }
    private int overviewTop() { return gridY + 65; }
    private int overviewBottom() { return footerY - 7; }
    private int maxOverviewScroll() {
        int rows = (tierTotals.length + overviewColumns() - 1) / overviewColumns();
        return Math.max(0, rows * OVERVIEW_ROW_HEIGHT - (overviewBottom() - overviewTop()));
    }
    private void scrollOverview(int amount) {
        overviewScroll = net.minecraft.util.math.MathHelper.clamp(overviewScroll + amount, 0, maxOverviewScroll());
        rebuildSlots();
    }
    private void drawTierOverview(Canvas context) {
        int columns = overviewColumns(), span = (bodyWidth - 6) / columns;
        int row = gridY + 25;
        context.raw().drawText(textRenderer, "Discovered", gridX, row, MUTED, false);
        context.raw().drawText(textRenderer, startedCount + " / " + catalog.size(), gridX, row + 12, TEXT, true);
        context.raw().drawText(textRenderer, "Mastered", gridX + bodyWidth / 2, row, MUTED, false);
        context.raw().drawText(textRenderer, Integer.toString(masteredCount), gridX + bodyWidth / 2, row + 12, TEXT, true);
        context.raw().drawText(textRenderer, "Milestone ledger", gridX, overviewTop()-12, TEXT, false);
        long elapsed = now() - viewOpened;
        context.raw().enableScissor(gridX, overviewTop(), gridX + bodyWidth, overviewBottom());
        for (int i = 0; i < tierTotals.length; i++) {
            int xx = gridX + i % columns * span, yy = overviewTop() + i / columns * OVERVIEW_ROW_HEIGHT - overviewScroll;
            if (yy + OVERVIEW_ROW_HEIGHT <= overviewTop() || yy >= overviewBottom()) continue;
            int w = span - 3;
            context.raw().fill(xx, yy, xx + w, yy + 26, TILE);
            String label = CollectionProgress.targetLabel(CollectionProgress.OVERVIEW_MILESTONES.get(i));
            context.raw().drawText(textRenderer, label, xx + 5, yy + 5, TEXT, false);
            right(context, tierCompletions[i] + "/" + tierTotals[i], xx + w - 5, yy + 5, MUTED);
            float ratio = tierTotals[i] == 0 ? 0 : (float)tierCompletions[i]/tierTotals[i];
            meter(context,xx+5,yy+18,w-10,ratio * reveal(elapsed,i % columns),RED);
        }
        context.raw().disableScissor();
        if (maxOverviewScroll() > 0) {
            int track = overviewBottom() - overviewTop(), thumb = Math.max(8, track * track / (track + maxOverviewScroll()));
            int yy = overviewTop() + (track - thumb) * overviewScroll / maxOverviewScroll();
            context.raw().fill(gridX + bodyWidth - 2,overviewTop(),gridX + bodyWidth,overviewBottom(),LINE);
            context.raw().fill(gridX + bodyWidth - 2,yy,gridX + bodyWidth,yy + thumb,MUTED);
        }
    }
    private void drawCollectionDetail(Canvas c) {
        long mined = count(selected), now = now(), target = CollectionProgress.next(selected.id,mined);
        int tier = CollectionProgress.completed(selected.id,mined);
        var goals = CollectionProgress.milestones(selected.id);
        float enter = reveal(now - viewOpened,0);
        c.raw().fill(gridX,gridY,gridX + 34,gridY + 34,TILE);
        var mat = c.raw().getMatrices(); mat.push();
        try {
            mat.translate(gridX + 17,gridY + 17,0);
            float scale = 1.23f + .27f * enter;
            mat.scale(scale,scale,1); c.raw().drawItem(selected.icon,-8,-8);
        } finally { mat.pop(); }
        MmmUi.drawTextWithin(c.raw(),textRenderer,selected.name,gridX + 42,gridY + 1,bodyWidth - 45,TEXT,true);
        c.raw().drawText(textRenderer,numbers.format(displayedCount(now)) + " blocks mined",gridX + 42,gridY + 13,TEXT,false);
        c.raw().drawText(textRenderer,net.minecraft.util.Language.getInstance().reorder(textRenderer.trimToWidth(rankText(selected),bodyWidth-43)),
                gridX+42,gridY+25,MUTED,false);
        int shown = board.displayedTier(tier,goals.size());
        long goal = goals.get(shown - 1);
        String status = target == 0 ? "All milestones unlocked" : "Next: " + numbers.format(target - mined) + " more for " + CollectionProgress.targetLabel(target);
        boolean compactInspection = !wideInspector() && board.inspectedTier() != 0;
        if (compactInspection) status = "Tier " + CollectionBoardArt.tierLabel(shown) + ": " +
                (mined >= goal ? "unlocked" : numbers.format(goal - mined) + " to go");
        MmmUi.drawTextWithin(c.raw(),textRenderer,status,gridX,gridY+41,bodyWidth-78,ROSE,false);
        right(c,tier + "/" + goals.size() + " tiers",gridX+bodyWidth,gridY+41,TEXT);
        long meterGoal = compactInspection ? goal : target;
        meter(c,gridX,gridY+53,bodyWidth,(meterGoal == 0 ? 1f : (float)displayedCount(now)/meterGoal) * enter,RED);
        c.raw().drawText(textRenderer,"Milestones",gridX,gridY+63,TEXT,false);
        if (detailPages() > 1) right(c,(detailPage+1) + " / " + detailPages(),gridX+tierBoardWidth(),gridY+63,MUTED);
        if (wideInspector()) {
            int left = gridX + tierBoardWidth() + 4, top = detailTop(), w = bodyWidth - tierBoardWidth() - 4;
            dossier(c,left,top,w,Math.min(124,footerY-top-5));
            float opening = reveal(now - inspectedAt,0);
            int tx = left + 9 + Math.round(5 * (1 - opening));
            c.raw().drawText(textRenderer,"MILESTONE RECORD",tx,top+11,MUTED,false);
            c.raw().drawText(textRenderer,"Tier " + CollectionBoardArt.tierLabel(shown),tx,top+27,TEXT,true);
            right(c,shown <= tier ? "Earned" : shown == tier + 1 ? "Next" : "Locked",left+w-9,top+27,ROSE);
            c.raw().fill(left+9,top+42,left+w-9,top+43,LINE);
            c.raw().drawText(textRenderer,numbers.format(goal) + " blocks",tx,top+50,TEXT,false);
            meter(c,left+9,top+65,w-18,Math.min(1f,(float)displayedCount(now)/goal)*opening,RED);
            c.raw().drawText(textRenderer,mined >= goal ? "Milestone reached" : numbers.format(goal-mined) + " to go",tx,top+75,MUTED,false);
            int n = goals.size(), inner = w-18;
            for (int i = 0; i < n; i++) {
                int sx = left+9+i*inner/n, ex = left+9+(i+1)*inner/n-1;
                c.raw().fill(sx,top+98,ex,top+102,i < tier ? RED : LINE);
                if (i+1 == shown) c.raw().fill(sx,top+104,ex,top+106,ROSE);
            }
            c.raw().drawText(textRenderer,"I",left+9,top+110,MUTED,false);
            right(c,CollectionBoardArt.tierLabel(n),left+w-9,top+110,MUTED);
        }
    }
    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        if (selected != null && mx >= x && mx < x + panelWidth && my >= gridY && my < footerY && vertical != 0) {
            changeDetailPage(vertical < 0 ? 1 : -1); return true;
        }
        if (category == null && selected == null && mx >= x && mx < x + panelWidth && my >= gridY && my < footerY) {
            if (vertical != 0) scrollOverview(vertical < 0 ? OVERVIEW_ROW_HEIGHT : -OVERVIEW_ROW_HEIGHT);
            return true;
        }
        if (category != null && selected == null && mx >= x && mx < x + panelWidth && my >= gridY && my < y + panelHeight) {
            changePage(vertical < 0 ? 1 : -1); return true;
        }
        return super.mouseScrolled(mx, my, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!searchField.isFocused() && BoardPlatform.inventoryKey(key, scan)) { close(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return MmmUi.shouldPauseGame(); }
    @Override public void renderBackground(Canvas context) {}
}
