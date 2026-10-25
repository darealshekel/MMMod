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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public final class CollectionBookScreen extends Screen
{
    private record BlockEntry(String id, String name, ItemStack icon) {}
    private final Screen parent;
    private final List<BlockEntry> catalog = new ArrayList<>();
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
    private int sort, filter, page, ticks, x, y, cell, panelWidth, panelHeight, gridY, gridX, footerY;
    private final long[] tierCompletions = new long[CollectionProgress.OVERVIEW_MILESTONES.size()];
    private final long[] tierTotals = new long[CollectionProgress.OVERVIEW_MILESTONES.size()];
    private int overviewScroll;
    private String rankHoverId = "";
    private long rankHoverStarted;
    private static final int OVERVIEW_ROW_HEIGHT = 42;
    private String search = "";
    private TextFieldWidget searchField;
    private static final int PAGE_SIZE = 28;
    private static final String[] SORTS = { "Nearest milestone", "Most mined", "Block name" };
    private static final String[] FILTERS = { "All collections", "Started collections", "Completed collections" };
    private int detailPage;
    private static final int DETAIL_ROW_HEIGHT = 48;

    public CollectionBookScreen(Screen parent) { super(Text.literal("MMM Collections")); this.parent = parent; }

    @Override protected void init() {
        MmmUi.ensureCursorVisible();
        panelWidth = Math.min(420, width - 24);
        cell = Math.min(40, Math.min((panelWidth - 24) / 7, (height - 126) / 4));
        panelHeight = cell * 4 + 110;
        if (category == null && selected == null) {
            int rows = (tierTotals.length + overviewColumns() - 1) / overviewColumns();
            panelHeight = Math.min(height - 16, 168 + rows * OVERVIEW_ROW_HEIGHT);
        }
        if (selected != null) {
            int rows = (CollectionProgress.milestones(selected.id).size() + detailColumns() - 1) / detailColumns();
            panelHeight = Math.min(height - 16, 148 + rows * DETAIL_ROW_HEIGHT);
            detailPage = Math.min(detailPage, detailPages() - 1);
        }
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        gridY = y + 72;
        gridX = x + (panelWidth - cell * 7) / 2;
        footerY = y + panelHeight - 30;
        overviewScroll = Math.clamp(overviewScroll, 0, maxOverviewScroll());
        if (catalog.isEmpty()) {
            var added = new HashSet<String>();
            for (String id : BlockBreakdownCatalog.blockIds()) {
                String collection = CollectionProgress.collectionId(id);
                if (!added.add(collection)) continue;
                Identifier key = Identifier.tryParse(CollectionProgress.iconId(collection));
                if (key != null && Registries.BLOCK.containsId(key)) {
                    var block = Registries.BLOCK.get(key);
                    String name = CollectionProgress.customName(collection);
                    catalog.add(new BlockEntry(collection, name == null ? block.getName().getString() : name, new ItemStack(block.asItem())));
                }
            }
            catalog.add(new BlockEntry("mmm:non_insta_deepslate", "Non-insta Deepslate", new ItemStack(Items.DEEPSLATE)));
        }
        searchField = new TextFieldWidget(textRenderer, x + 12, y + 46, panelWidth - 24, 18, Text.literal("Search collections"));
        searchField.setMaxLength(64);
        searchField.setPlaceholder(Text.literal("Search blocks..."));
        searchField.setText(search);
        searchField.visible = selected == null;
        searchField.setChangedListener(value -> {
            search = value; page = 0;
            if (!value.isBlank() && category == null) category = BlockRankingCategory.ALL;
            filterRows(); rebuildSlots();
        });
        addDrawableChild(searchField);
        refresh(); rebuildSlots();
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
        Arrays.fill(tierCompletions, 0);
        Arrays.fill(tierTotals, 0);
        for (var entry : catalog) {
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
    private int pages() { return Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE); }
    private long count(BlockEntry block) { return counts.getOrDefault(block.id, 0L); }
    private void rebuildSlots() {
        for (var slot : slots) remove(slot);
        slots.clear();
        if (selected != null) detailSlots();
        else if (category == null) categorySlots();
        else blockSlots();
        slot(0, 5, "Back", Items.ARROW, this::back,
                () -> List.of(Text.literal(selected != null ? "Back to blocks" : category != null ? "Back to categories" : "Back")));
        slot(1, 5, "World scope", allWorlds ? Items.COMPASS : Items.GRASS_BLOCK, () -> { allWorlds = !allWorlds; page = 0; refresh(); rebuildSlots(); },
                () -> List.of(Text.literal(scopeLabel()).formatted(Formatting.YELLOW),
                        Text.literal(allWorlds ? "Website breakdowns across your worlds" : WorldSessionContext.getCurrentWorldName()),
                        Text.literal(allWorlds ? "Includes mining since the last sync" : "Live local block breakdown").formatted(Formatting.GRAY),
                        Text.literal(allWorlds ? "Switch to this world" : "Switch to Global Total").formatted(Formatting.GRAY)));
        slot(8, 5, "Close", Items.BARRIER, this::close, () -> List.of(Text.literal("Close collections").formatted(Formatting.RED)));
        if (selected != null) {
            slot(3, 5, "Share collection", Items.PAPER, () -> client.setScreen(ProgressShareScreen.collection(this,
                    selected.name, selected.id, count(selected), allWorlds ? "Global Total" : WorldSessionContext.getCurrentWorldName(), selected.icon)),
                    () -> List.of(Text.literal("Share collection").formatted(Formatting.YELLOW), Text.literal("Save image or copy progress").formatted(Formatting.GRAY)));
            boolean pinned = PinnedCollection.matches(selected.id, allWorlds);
            slot(4, 5, pinned ? "Unpin collection" : "Pin collection", pinned ? Items.LIME_DYE : Items.TRIPWIRE_HOOK, () -> {
                if (PinnedCollection.matches(selected.id, allWorlds)) PinnedCollection.unpin();
                else PinnedCollection.pin(selected.id, allWorlds, website);
                rebuildSlots();
            }, () -> List.of(Text.literal(pinned ? "Unpin collection" : "Pin collection to HUD").formatted(Formatting.YELLOW),
                    Text.literal(allWorlds ? "Global Total / live progress" : WorldSessionContext.getCurrentWorldName()),
                    Text.literal("Tracks the next collection tier").formatted(Formatting.GRAY)));
        } else if (PinnedCollection.hasPin()) {
            slot(4, 5, "Unpin collection", Items.LIME_DYE, () -> { PinnedCollection.unpin(); rebuildSlots(); },
                    () -> List.of(Text.literal("Unpin collection from HUD").formatted(Formatting.YELLOW)));
        }
        if (selected != null && detailPages() > 1) {
            var previous = slot(5, 5, "Previous milestones", Items.ARROW, () -> changeDetailPage(-1),
                    () -> List.of(Text.literal("Previous milestones")));
            var next = slot(6, 5, "Next milestones", Items.ARROW, () -> changeDetailPage(1),
                    () -> List.of(Text.literal("Next milestones")));
            previous.active = detailPage > 0;
            next.active = detailPage + 1 < detailPages();
        }
        if (category != null && selected == null) {
            slot(2, 5, "Progress filter", Items.HOPPER, () -> { filter = (filter + 1) % FILTERS.length; page = 0; filterRows(); rebuildSlots(); },
                    () -> List.of(Text.literal(FILTERS[filter]).formatted(Formatting.YELLOW), Text.literal("Change progress filter").formatted(Formatting.GRAY)));
            slot(3, 5, "Sort collections", Items.COMPARATOR, () -> { sort = (sort + 1) % SORTS.length; page = 0; filterRows(); rebuildSlots(); },
                    () -> List.of(Text.literal(SORTS[sort]).formatted(Formatting.YELLOW), Text.literal("Change sorting").formatted(Formatting.GRAY)));
            var prev = slot(5, 5, "Previous page", Items.ARROW, () -> changePage(-1), () -> List.of(Text.literal("Previous page")));
            var next = slot(6, 5, "Next page", Items.ARROW, () -> changePage(1), () -> List.of(Text.literal("Next page")));
            prev.active = page > 0; next.active = page + 1 < pages();
        }
        if (category == null && selected == null) {
            if (maxOverviewScroll() > 0) {
                var previous = slot(5, 5, "Scroll tiers up", Items.ARROW, () -> scrollOverview(-OVERVIEW_ROW_HEIGHT), () -> List.of(Text.literal("Scroll tiers up")));
                var next = slot(6, 5, "Scroll tiers down", Items.ARROW, () -> scrollOverview(OVERVIEW_ROW_HEIGHT), () -> List.of(Text.literal("Scroll tiers down")));
                previous.active = overviewScroll > 0;
                next.active = overviewScroll < maxOverviewScroll();
            }
        }
    }
    private void categorySlots() {
        bookSlot();
        int col = 1;
        for (var group : BlockRankingCategory.values()) {
            slot(col++, 2, group.label + " collections", Registries.ITEM.get(Identifier.of("minecraft", group.icon)), () -> {
                category = group; page = 0; clearAndInit();
            }, () -> categoryTooltip(group));
        }
    }
    private List<Text> categoryTooltip(BlockRankingCategory group) {
        var entries = catalog.stream().filter(entry -> group.includes(entry.id))
                .sorted(Comparator.<BlockEntry>comparingLong(this::count).reversed().thenComparing(BlockEntry::name)).toList();
        long done = entries.stream().filter(entry -> CollectionProgress.next(entry.id, count(entry)) == 0).count();
        var lines = new ArrayList<Text>();
        lines.add(Text.literal(group.label + " Collections").formatted(Formatting.GREEN));
        lines.add(Text.literal("View your mining collections!").formatted(Formatting.GRAY));
        lines.add(Text.empty());
        lines.add(Text.literal("Collections maxed out: ").formatted(Formatting.GRAY)
                .append(Text.literal(String.format(Locale.ROOT, "%.0f%%", entries.isEmpty() ? 0 : done * 100.0 / entries.size())).formatted(Formatting.GREEN)));
        lines.add(progressBar(done, entries.size()).append(Text.literal(" " + done + "/" + entries.size()).formatted(Formatting.YELLOW)));
        lines.add(Text.empty());
        int limit = Math.max(1, (height - 115) / 10);
        for (var entry : entries.stream().limit(limit).toList()) {
            lines.add(Text.literal(entry.name + ": ").formatted(Formatting.GRAY)
                    .append(Text.literal(numbers.format(count(entry))).formatted(Formatting.GREEN)));
        }
        if (entries.size() > limit) lines.add(Text.literal("+ " + (entries.size() - limit) + " more collections").formatted(Formatting.DARK_GRAY));
        lines.add(Text.empty());
        lines.add(Text.literal("Click to view!").formatted(Formatting.YELLOW));
        return lines;
    }
    private net.minecraft.text.MutableText progressBar(long value, long target) {
        int filled = target <= 0 ? 0 : (int)Math.clamp(value * 20.0 / target, 0, 20);
        return Text.literal("|".repeat(filled)).formatted(Formatting.GREEN)
                .append(Text.literal("|".repeat(20 - filled)).formatted(Formatting.DARK_GRAY));
    }
    private void bookSlot() {
        addSlot(4, 0, "Collection progress", ItemStack.EMPTY, true, () -> {
            category = null; selected = null; search = ""; page = 0; clearAndInit();
        }, () -> "", () -> categoryTooltip(BlockRankingCategory.ALL));
    }
    private void blockSlots() {
        bookSlot();
        for (int i = page * PAGE_SIZE; i < Math.min(rows.size(), (page + 1) * PAGE_SIZE); i++) {
            var entry = rows.get(i); int index = i % PAGE_SIZE;
            addSlot(index % 7 + 1, index / 7 + 1, entry.name, entry.icon, false, () -> {
                selected = entry; detailPage = 0; clearAndInit();
            }, () -> CollectionProgress.tierLabel(CollectionProgress.completed(entry.id, count(entry))), () -> blockTooltip(entry));
        }
    }
    private List<Text> blockTooltip(BlockEntry block) {
        long value = count(block), next = CollectionProgress.next(block.id, value);
        int tier = CollectionProgress.completed(block.id, value);
        var lines = new ArrayList<Text>();
        lines.add(Text.literal(block.name + (tier == 0 ? "" : " " + CollectionProgress.tierLabel(tier))).formatted(Formatting.GREEN));
        lines.add(Text.literal("View your " + block.name + " Collection").formatted(Formatting.GRAY));
        lines.add(Text.literal("progress and milestones!").formatted(Formatting.GRAY));
        lines.add(Text.empty());
        if (next == 0) lines.add(Text.literal("COLLECTION MAXED OUT!").formatted(Formatting.GREEN, Formatting.BOLD));
        else {
            lines.add(Text.literal("Next milestone: " + CollectionProgress.targetLabel(next)).formatted(Formatting.YELLOW));
            lines.add(progressBar(value, next).append(Text.literal(" " + String.format(Locale.ROOT, "%.1f%%", value * 100.0 / next)).formatted(Formatting.YELLOW)));
            lines.add(Text.literal(numbers.format(next - value) + " blocks to go").formatted(Formatting.GRAY));
        }
        lines.add(Text.literal("Total mined: ").formatted(Formatting.GRAY).append(Text.literal(numbers.format(value)).formatted(Formatting.YELLOW)));
        lines.add(rankText(block));
        lines.add(Text.empty());
        lines.add(Text.literal("Click to view!").formatted(Formatting.YELLOW));
        return lines;
    }
    private int detailColumns() { return Math.max(1, Math.min(8, (panelWidth - 24) / 46)); }
    private int detailRows() { return Math.max(1, (panelHeight - 148) / DETAIL_ROW_HEIGHT); }
    private int detailPageSize() { return detailColumns() * detailRows(); }
    private int detailPages() {
        return Math.max(1, (CollectionProgress.milestones(selected.id).size() + detailPageSize() - 1) / detailPageSize());
    }
    private void changeDetailPage(int amount) {
        detailPage = Math.clamp(detailPage + amount, 0, detailPages() - 1);
        rebuildSlots();
    }
    private int detailPaneX(int index) {
        int span = (panelWidth - 24) / detailColumns();
        return x + 12 + (index % detailPageSize() % detailColumns()) * span + (span - 32) / 2;
    }
    private int detailPaneY(int index) { return y + 98 + (index % detailPageSize() / detailColumns()) * DETAIL_ROW_HEIGHT; }
    private void detailSlots() {
        bookSlot();
        var goals = CollectionProgress.milestones(selected.id);
        for (int i = detailPage * detailPageSize(); i < Math.min(goals.size(), (detailPage + 1) * detailPageSize()); i++) {
            int tier = i + 1;
            long goal = goals.get(i);
            Item pane = count(selected) >= goal ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE;
            var slot = new CollectionSlotButton(detailPaneX(i), detailPaneY(i), 32,
                    "Tier " + CollectionProgress.tierLabel(tier), new ItemStack(pane), false, () -> {}, () -> Integer.toString(tier), () -> {
                        long value = count(selected);
                        var lines = new ArrayList<Text>();
                        lines.add(Text.literal(selected.name + " " + CollectionProgress.tierLabel(tier)).formatted(Formatting.GREEN));
                        lines.add(Text.literal("Milestone: " + numbers.format(goal) + " blocks").formatted(Formatting.GRAY));
                        lines.add(progressBar(value, goal).append(Text.literal(" " + numbers.format(Math.min(value, goal)) + "/" + numbers.format(goal)).formatted(Formatting.YELLOW)));
                        lines.add(Text.literal(value >= goal ? "UNLOCKED!" : numbers.format(goal - value) + " blocks to go")
                                .formatted(value >= goal ? Formatting.GREEN : Formatting.RED));
                        return lines;
                    });
            slots.add(slot); addDrawableChild(slot);
        }
    }
    private Text rankText(BlockEntry block) {
        if (client == null || client.player == null) return Text.literal("Website rank unavailable").formatted(Formatting.GRAY);
        if (selected == null) {
            long now = System.nanoTime();
            if (!rankHoverId.equals(block.id)) { rankHoverId = block.id; rankHoverStarted = now; }
            if (now - rankHoverStarted < 250_000_000L) return Text.literal("Loading website rank...").formatted(Formatting.GRAY);
        }
        String username = WebsiteLinkManager.isCurrentPlayerLinked() ? WebsiteLinkManager.getPersistedUsername() : "";
        if (username.isBlank()) username = client.player.getGameProfile().getName();
        var rank = CollectionRankClient.get(block.id, username);
        return CollectionRankText.format(rank);
    }
    private CollectionSlotButton slot(int col, int row, String label, Item icon, Runnable action, Supplier<List<Text>> tooltip) {
        return addSlot(col, row, label, new ItemStack(icon), false, action, () -> "", tooltip);
    }
    private CollectionSlotButton addSlot(int col, int row, String label, ItemStack icon, boolean book, Runnable action,
            Supplier<String> badge, Supplier<List<Text>> tooltip) {
        int size = row == 5 || row == 0 ? 22 : cell - 2;
        int sx = row == 5 ? x + 12 + col * ((panelWidth - 46) / 8)
                : row == 0 ? x + panelWidth - 34 : gridX + (col - 1) * cell;
        if (category == null && row != 0 && row != 5) {
            int count = BlockRankingCategory.values().length;
            int step = Math.min(cell, (panelWidth - 24) / count);
            size = Math.min(24, step - 2);
            sx = x + (panelWidth - count * step) / 2 + (col - 1) * step + (step - size) / 2;
        }
        int sy = row == 5 ? footerY : row == 0 ? y + 8
                : category == null ? footerY - 30 : gridY + (row - 1) * cell;
        var slot = new CollectionSlotButton(sx, sy, size, label, icon, book, action, badge, tooltip);
        slots.add(slot); addDrawableChild(slot); return slot;
    }
    private void back() {
        if (selected != null) { selected = null; clearAndInit(); }
        else if (category != null) { category = null; search = ""; page = 0; clearAndInit(); }
        else close();
    }
    private void changePage(int amount) { page = Math.max(0, Math.min(pages() - 1, page + amount)); rebuildSlots(); }
    @Override public void tick() {
        if (++ticks % 20 == 0) {
            long oldCount = selected == null ? -1 : count(selected);
            refresh();
            if (selected != null && oldCount != count(selected)) rebuildSlots();
        }
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0000000);
        context.fill(x, y, x + panelWidth, y + panelHeight, 0xFF373737);
        context.drawBorder(x, y, panelWidth, panelHeight, 0xFF111111);
        context.fill(x + 2, y + 2, x + panelWidth - 2, y + 4, 0xFFBA303A);
        String heading = selected != null ? selected.name + " Collection" : category == null ? "MMM Collections"
                : category.label + " Collections " + (page + 1) + "/" + pages();
        MmmUi.drawTextWithin(context, textRenderer, heading, x + 12, y + 13, panelWidth - 58, 0xFFEAEAEA, false);
        MmmUi.drawTextWithin(context, textRenderer, scopeLabel(), x + 12, y + 32, panelWidth - 24, MmmUi.LABEL, false);
        context.fill(x + 12, footerY - 6, x + panelWidth - 12, footerY - 5, 0xFF515155);
        if (selected != null) {
            drawCollectionDetail(context);
        } else if (category == null) {
            drawTierOverview(context);
        } else if (rows.isEmpty()) center(context, "No matching collections", gridY + 2 * cell, MmmUi.LABEL);
        super.render(context, mouseX, mouseY, delta);
        for (var slot : slots) if (slot.isHovered()) {
            context.drawTooltip(textRenderer, slot.details.get(), mouseX, mouseY); break;
        }
        if (category == null && selected == null && mouseY >= overviewTop() && mouseY < overviewBottom()) {
            int span = (panelWidth - 30) / overviewColumns();
            int column = (mouseX - x - 12) / span;
            int row = (mouseY - overviewTop() + overviewScroll) / OVERVIEW_ROW_HEIGHT;
            int index = row * overviewColumns() + column;
            if (mouseX >= x + 12 && mouseX < x + 12 + span * overviewColumns() && index < tierTotals.length) {
                boolean complete = tierTotals[index] > 0 && tierCompletions[index] == tierTotals[index];
                context.drawTooltip(textRenderer, List.of(
                        Text.literal(CollectionProgress.targetLabel(CollectionProgress.OVERVIEW_MILESTONES.get(index)) + " blocks").formatted(Formatting.YELLOW),
                        Text.literal(tierCompletions[index] + " / " + tierTotals[index] + " collections completed"),
                        Text.literal(complete ? "Completed" : tierTotals[index] == 0 ? "No collections at this tier" : "Not completed")
                                .formatted(complete ? Formatting.GREEN : Formatting.RED)), mouseX, mouseY);
            }
        }
    }
    private int overviewColumns() { return Math.max(3, (panelWidth - 30) / 52); }
    private int overviewTop() { return gridY + 16; }
    private int overviewBottom() { return footerY - 47; }
    private int maxOverviewScroll() {
        int rows = (tierTotals.length + overviewColumns() - 1) / overviewColumns();
        return Math.max(0, rows * OVERVIEW_ROW_HEIGHT - (overviewBottom() - overviewTop()));
    }
    private void scrollOverview(int amount) {
        overviewScroll = Math.clamp(overviewScroll + amount, 0, maxOverviewScroll());
        rebuildSlots();
    }
    private void drawTierOverview(DrawContext context) {
        int columns = overviewColumns();
        int span = (panelWidth - 30) / columns;
        context.drawText(textRenderer, "Completed collections", x + 12, gridY + 2, MmmUi.TEXT, false);
        context.enableScissor(x + 12, overviewTop(), x + panelWidth - 12, overviewBottom());
        for (int i = 0; i < tierTotals.length; i++) {
            int mid = x + 12 + (i % columns) * span + span / 2;
            int top = overviewTop() + (i / columns) * OVERVIEW_ROW_HEIGHT - overviewScroll;
            if (top + OVERVIEW_ROW_HEIGHT <= overviewTop() || top >= overviewBottom()) continue;
            boolean complete = tierTotals[i] > 0 && tierCompletions[i] == tierTotals[i];
            context.drawItem(new ItemStack(complete ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE), mid - 8, top);
            String label = CollectionProgress.targetLabel(CollectionProgress.OVERVIEW_MILESTONES.get(i));
            context.drawText(textRenderer, label, mid - textRenderer.getWidth(label) / 2, top + 18, MmmUi.LABEL, false);
            String total = tierCompletions[i] + "/" + tierTotals[i];
            context.drawText(textRenderer, total, mid - textRenderer.getWidth(total) / 2, top + 29,
                    complete ? MmmUi.SUCCESS : MmmUi.TEXT, false);
        }
        context.disableScissor();
        if (maxOverviewScroll() > 0) {
            int track = overviewBottom() - overviewTop();
            int thumb = Math.max(10, track * track / (track + maxOverviewScroll()));
            int top = overviewTop() + (track - thumb) * overviewScroll / maxOverviewScroll();
            context.fill(x + panelWidth - 11, overviewTop(), x + panelWidth - 9, overviewBottom(), 0xFF222225);
            context.fill(x + panelWidth - 11, top, x + panelWidth - 9, top + thumb, MmmUi.LABEL);
        }
        context.drawText(textRenderer, "Categories", x + 12, footerY - 43, MmmUi.LABEL, false);
    }
    private void drawCollectionDetail(DrawContext context) {
        long value = count(selected);
        context.drawItem(selected.icon, x + 12, y + 49);
        MmmUi.drawTextWithin(context, textRenderer, numbers.format(value) + " blocks mined", x + 38, y + 52,
                panelWidth - 50, MmmUi.TEXT, false);
        Text ranking = rankText(selected);
        context.drawText(textRenderer, net.minecraft.util.Language.getInstance().reorder(
                textRenderer.trimToWidth(ranking, panelWidth - 24)), x + 12, y + 65, MmmUi.TEXT, false);
        var goals = CollectionProgress.milestones(selected.id);
        int completed = CollectionProgress.completed(selected.id, value);
        String status = completed == goals.size() ? "COLLECTION MAXED OUT!" : completed + "/" + goals.size() + " milestones unlocked";
        center(context, status, y + 81, completed == goals.size() ? MmmUi.SUCCESS : MmmUi.TEXT);
        for (int i = detailPage * detailPageSize(); i < Math.min(goals.size(), (detailPage + 1) * detailPageSize()); i++) {
            int mid = detailPaneX(i) + 16;
            String target = CollectionProgress.targetLabel(goals.get(i));
            context.drawText(textRenderer, target, mid - textRenderer.getWidth(target) / 2, detailPaneY(i) + 35,
                    value >= goals.get(i) ? MmmUi.SUCCESS : MmmUi.LABEL, false);
        }
        if (detailPages() > 1) center(context, "Milestones " + (detailPage + 1) + "/" + detailPages(), footerY - 18, MmmUi.LABEL);
    }

    private void center(DrawContext context, String value, int yy, int color) {
        String text = MmmUi.truncate(textRenderer, value, panelWidth - 20);
        context.drawText(textRenderer, text, x + (panelWidth - textRenderer.getWidth(text)) / 2, yy, color, false);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
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
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!searchField.isFocused() && client.options.inventoryKey.matchesKey(key, scan)) { close(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return MmmUi.shouldPauseGame(); }
    @Override public void renderBackground(DrawContext context, int x, int y, float delta) {}
}
