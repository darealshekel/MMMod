package com.mmm.ui;

import com.mmm.leaderboard.*;
import com.mmm.util.BlockBreakdownCatalog;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class LeaderboardScreen extends Screen
{
    private static final int ROW_HEIGHT = 24;
    private final Screen parent;
    private final boolean chooseSidebar;
    private LeaderboardData.Kind kind = LeaderboardData.Kind.PLAYERS;
    private BlockRankingCategory category = BlockRankingCategory.ALL;
    private String selectedBlock, search = "", error;
    private LeaderboardData.Page data;
    private CompletableFuture<LeaderboardData.Page> pending;
    private TextFieldWidget searchField;
    private ButtonWidget previous, next, refresh;
    private int page = 1, offset, generation;
    private long searchAt, refreshAt;
    private boolean loading, removed;
    private int x, contentWidth, listTop, listBottom, blockChoiceY;
    private final List<String> blockIds = BlockBreakdownCatalog.blockIds();
    private List<String> visibleBlocks = List.of();

    public LeaderboardScreen(Screen parent) { this(parent, false); }
    public LeaderboardScreen(Screen parent, boolean chooseSidebar) {
        super(Text.literal("MMM Leaderboards")); this.parent = parent; this.chooseSidebar = chooseSidebar;
        if (chooseSidebar) {
            var selection = LeaderboardSidebar.selection();
            kind = selection.kind();
            selectedBlock = kind == LeaderboardData.Kind.BLOCKS ? selection.block() : null;
        }
    }
    private boolean picking() { return kind == LeaderboardData.Kind.BLOCKS && selectedBlock == null; }

    @Override protected void init() {
        removed = false;
        MmmUi.ensureCursorVisible();
        x = MmmUi.contentLeft(width) + 10;
        contentWidth = Math.max(1, width - x - 10);
        int y = MmmUi.TOP_BAR_HEIGHT + 8;
        int columns = contentWidth >= 200 ? 8 : 4;
        int cellWidth = contentWidth / columns;
        int i = 0;
        String[] compact = { "All", "Srv", "Wld", "TMC", "SSP", "HSP", "Geo", "Blk" };
        for (var entry : LeaderboardData.Kind.values()) {
            String label = textRenderer.getWidth(entry.label) <= cellWidth - 7 ? entry.label : compact[i];
            ButtonWidget button = button(x + i % columns * cellWidth, y + i / columns * 22, cellWidth - 3, label, () -> {
                kind = entry; selectedBlock = null; search = ""; page = 1; offset = 0; data = null; error = null;
                searchAt = 0; cancel(); clearAndInit();
            });
            button.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(entry.label)));
            button.active = entry != kind;
            i++;
        }
        y += ((i + columns - 1) / columns) * 22 + 4;
        blockChoiceY = y;
        if (picking()) {
            button(x, y, contentWidth, category.label + " category", () -> {
                category = BlockRankingCategory.values()[(category.ordinal() + 1) % BlockRankingCategory.values().length];
                offset = 0; clearAndInit(); filterBlocks();
            });
            y += 24;
        } else if (kind == LeaderboardData.Kind.BLOCKS) {
            button(x, y, contentWidth, blockName(selectedBlock) + " - Choose block", () -> {
                selectedBlock = null; search = ""; data = null; offset = 0; page = 1; cancel(); clearAndInit();
            });
            y += 24;
        }
        searchField = new TextFieldWidget(textRenderer, x, y, Math.max(20, contentWidth - 66), 20, Text.literal("Search rankings"));
        searchField.setMaxLength(64);
        searchField.setPlaceholder(Text.literal(picking() ? "Search blocks..." : kind == LeaderboardData.Kind.SERVERS ? "Search servers..." : "Search names..."));
        searchField.setText(search);
        searchField.setChangedListener(value -> {
            search = value; offset = 0; page = 1;
            cancel(); data = null; error = null;
            if (picking()) filterBlocks(); else searchAt = System.currentTimeMillis() + 350;
        });
        addDrawableChild(searchField);
        refresh = button(x + contentWidth - 62, y, 62, chooseSidebar ? "Use" : picking() ? "Clear" : "Refresh", () -> {
            if (chooseSidebar) { LeaderboardSidebar.select(kind, selectedBlock); close(); return; }
            if (picking()) searchField.setText(""); else { searchAt = 0; refreshAt = System.currentTimeMillis() + 2000; load(true); }
        });
        listTop = y + 34;
        listBottom = height - 36;
        int navWidth = Math.min(72, contentWidth / 4);
        previous = button(x, height - 28, navWidth, "< Prev", () -> { page--; offset = 0; load(false); });
        next = button(x + navWidth + 4, height - 28, navWidth, "Next >", () -> { page++; offset = 0; load(false); });
        button(x + contentWidth - navWidth, height - 28, navWidth, "Done", this::close);
        filterBlocks();
        if (data == null && !loading && !picking() && error == null && searchAt == 0) load(false);
        updateButtons();
    }
    private ButtonWidget button(int bx, int by, int bw, String label, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(MmmUi.truncate(textRenderer, label, bw - 4)), b -> action.run())
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(label)))
                .dimensions(bx, by, Math.max(1, bw), 20).build());
    }
    private void filterBlocks() {
        String query = search.toLowerCase(Locale.ROOT);
        visibleBlocks = blockIds.stream().filter(category::includes)
                .filter(id -> id.contains(query) || blockName(id).toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private static String blockName(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier != null && Registries.BLOCK.containsId(identifier)) return Registries.BLOCK.get(identifier).getName().getString();
        return id.replace("minecraft:", "").replace('_', ' ');
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
        var game = client;
        pending = LeaderboardClient.load(new LeaderboardData.Query(kind, selectedBlock, search, page), force);
        pending.whenComplete((result, failure) -> game.execute(() -> {
            if (removed || requestGeneration != generation) return;
            loading = false;
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
        if (searchAt > 0 && System.currentTimeMillis() >= searchAt) { searchAt = 0; load(false); }
        updateButtons();
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MmmUi.backdrop(context, width, height);
        MmmUi.drawMmmScreensSidebar(context, textRenderer, width, height, mouseX, mouseY, "LEADERBOARDS");
        String status = picking() ? visibleBlocks.size() + " blocks" : loading || searchAt > 0 ? "Loading rankings..."
                : error != null ? error : data == null ? "" : kind.label + " | " + data.page() + "/" + data.pages() + " | " + format(data.total()) + " entries";
        MmmUi.drawTextWithin(context, textRenderer, status, x, listTop - 11, contentWidth, error != null ? MmmUi.ERROR : MmmUi.MUTED, false);
        int count = picking() ? visibleBlocks.size() : data == null ? 0 : data.rows().size();
        int visible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        offset = Math.max(0, Math.min(offset, Math.max(0, count - visible)));
        if (listBottom > listTop) {
            context.enableScissor(x, listTop, x + contentWidth, listBottom);
            for (int i = offset; i < Math.min(count, offset + visible); i++) {
                int y = listTop + (i - offset) * ROW_HEIGHT;
                boolean hovered = mouseX >= x && mouseX < x + contentWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;
                context.fill(x, y, x + contentWidth, y + ROW_HEIGHT - 1, hovered ? MmmUi.accentSoft() : MmmUi.INSET);
                if (picking()) {
                    String id = visibleBlocks.get(i);
                    Identifier identifier = Identifier.tryParse(id);
                    if (identifier != null && Registries.ITEM.containsId(identifier))
                        context.drawItem(new ItemStack(Registries.ITEM.get(identifier)), x + 4, y + 5);
                    MmmUi.drawTextWithin(context, textRenderer, blockName(id), x + 26, y + 10, contentWidth - 36, MmmUi.TEXT, false);
                } else {
                    var row = data.rows().get(i);
                    int amountWidth = Math.min(118, contentWidth / 2);
                    MmmUi.drawTextWithin(context, textRenderer, "#" + row.rank() + " " + row.name(), x + 5, y + 4, contentWidth - amountWidth - 12, MmmUi.TEXT, false);
                    MmmUi.drawTextWithin(context, textRenderer, row.detail(), x + 5, y + 16, contentWidth - amountWidth - 12, MmmUi.MUTED, false);
                    MmmUi.drawTextRightWithin(context, textRenderer, format(row.blocks()), x + contentWidth - 6, y + 5, amountWidth, 0xFFFF5A1F, false);
                    MmmUi.drawTextRightWithin(context, textRenderer, "blocks mined", x + contentWidth - 6, y + 16, amountWidth, MmmUi.MUTED, false);
                }
            }
            context.disableScissor();
        }
        if (count == 0 && !loading && error == null && searchAt == 0)
            MmmUi.drawTextWithin(context, textRenderer, "No matching records.", x + 6, listTop + 12, contentWidth - 12, MmmUi.MUTED, false);
        if (count > visible && listBottom > listTop) {
            int thumb = Math.max(8, (listBottom - listTop) * visible / count);
            int y = listTop + (listBottom - listTop - thumb) * offset / Math.max(1, count - visible);
            context.fill(x + contentWidth - 2, y, x + contentWidth, y + thumb, MmmUi.accent());
        }
        super.render(context, mouseX, mouseY, delta);
        if (kind == LeaderboardData.Kind.BLOCKS) {
            Identifier icon = Identifier.tryParse(picking() ? "minecraft:" + category.icon : selectedBlock);
            if (icon != null && Registries.ITEM.containsId(icon))
                context.drawItem(new ItemStack(Registries.ITEM.get(icon)), x + 4, blockChoiceY + 2);
        }
    }
    private static String format(long value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && MmmUi.handleMmmScreensSidebarClick(this, parent, mouseX, mouseY, "LEADERBOARDS")) return true;
        if (button == 0 && picking() && mouseX >= x && mouseX < x + contentWidth && mouseY >= listTop && mouseY < listBottom) {
            int index = offset + (int) (mouseY - listTop) / ROW_HEIGHT;
            if (index < visibleBlocks.size()) {
                selectedBlock = visibleBlocks.get(index); search = ""; page = 1; offset = 0; searchAt = 0;
                clearAndInit(); return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (y >= listTop && y < listBottom && x >= this.x && x < this.x + contentWidth) {
            offset = Math.max(0, offset - (int) Math.signum(vertical) * 3); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void removed() { removed = true; cancel(); }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return MmmUi.shouldPauseGame(); }
    @Override public void renderBackground(DrawContext context, int x, int y, float delta) {}
}
