package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.config.value.*;
import com.mmm.hotkey.MmmHotkey;
import com.mmm.legacy.settings.*;
import com.mmm.sync.*;
import java.util.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import com.mmm.legacy.LegacySettingsModel.Page;

public final class LegacyMenuScreen extends LegacyScreen {
    private Page page = Page.GENERAL;
    private TextFieldWidget search;
    private int offset, sideOffset, contentLeft, contentWidth, maxScroll, sideWidth;
    private String query = "";
    private final List<Placed> placed = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private SettingButton dragging;
    private record Row(String label, String description, String section, IConfigBase config, boolean slider, String value, Runnable action) {}
    private record Placed(Row row, int x, int y, int width, int controlWidth) {}
    private record Card(String title, int x, int y, int width, int height) {}

    public LegacyMenuScreen(Screen parent) { super(parent, "MMM Settings"); }
    @Override public void init() {
        boolean focused = search != null && search.isFocused();
        int cursor = search == null ? query.length() : search.getCursor();
        int selection = search == null ? cursor : search.getSelectionEnd();
        super.init();
        panelWidth = Math.max(1, Math.min(1060, width - 16)); left = (width - panelWidth) / 2;
        buttons.clear(); actions.clear(); placed.clear(); cards.clear();
        button(900, left + panelWidth - 56, 10, 56, "Done");
        sideWidth = width < 460 ? 82 : 106;
        contentLeft = left + sideWidth + 12; contentWidth = panelWidth - sideWidth - 12;
        List<Page> pages = Arrays.stream(Page.values()).filter(this::hasPage).toList();
        int sideCapacity = SettingsUi.sidebarCapacity(height);
        sideOffset = SettingsUi.clampScroll(sideOffset, pages.size(), sideCapacity);
        for (int i = 0; i < sideCapacity && i + sideOffset < pages.size(); i++) {
            Page item = pages.get(i + sideOffset);
            int id = item.ordinal();
            addButton(new ActionButton(id, left, 44 + i * 22, sideWidth, item.label, page == item));
            actions.put(id, () -> { page = item; query = ""; offset = 0; init(); });
        }
        if (pages.size() > sideCapacity) {
            action(40, left, height - 27, sideWidth / 2 - 2, "<", () -> { sideOffset--; init(); });
            action(41, left + sideWidth / 2 + 2, height - 27, sideWidth / 2 - 2, ">", () -> { sideOffset++; init(); });
        }
        search = new TextFieldWidget(50, textRenderer, contentLeft + 4, 46, Math.max(24, contentWidth - 30), 18);
        search.setMaxLength(128); search.setText(query); search.setFocused(focused);
        search.setCursor(Math.min(cursor, query.length())); search.setSelectionEnd(Math.min(selection, query.length()));
        action(42, contentLeft + contentWidth - 22, 44, 22, "x", () -> { query = ""; offset = 0; init(); });
        List<Row> rows = rows();
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (Row row : rows) groups.computeIfAbsent(row.section, ignored -> new ArrayList<>()).add(row);
        int columns = SettingsUi.columns(contentWidth);
        int cardWidth = SettingsUi.cardWidth(contentWidth);
        int[] ends = new int[columns];
        int id = 100;
        for (var group : groups.entrySet()) {
            int column = columns == 2 && ends[1] < ends[0] ? 1 : 0;
            int x = contentLeft + column * (cardWidth + 12);
            int top = 87 + ends[column] - offset;
            int h = 28 + group.getValue().size() * 36;
            cards.add(new Card(group.getKey(), x, top, cardWidth, h));
            for (int i = 0; i < group.getValue().size(); i++) {
                Row row = group.getValue().get(i); int y = top + 26 + i * 36;
                int control = SettingsUi.controlWidth(cardWidth);
                placed.add(new Placed(row, x + 8, y, cardWidth - 16, control));
                if (y >= 87 && y + 30 <= height - 30) {
                    int controlX = x + cardWidth - control - (row.config == null ? 8 : 30);
                    if (row.config != null) {
                        SettingButton b = new SettingButton(id, controlX, y + 4, control, row.config, row.slider);
                        addButton(b); actions.put(id++, () -> { if (!b.slider) edit(row.config); });
                        action(id++, x + cardWidth - 26, y + 4, 18, "R", () -> { row.config.resetToDefault(); Configs.saveToFile(); init(); });
                    } else action(id++, controlX, y + 4, control, row.value, row.action);
                }
            }
            ends[column] += h + 12;
        }
        maxScroll = Math.max(0, Arrays.stream(ends).max().orElse(0) - (height - 117));
        if (offset > maxScroll) { offset = maxScroll; init(); return; }
        action(43, contentLeft + contentWidth - 48, height - 26, 22, "<", () -> scroll(-Math.max(36, height - 150)));
        action(44, contentLeft + contentWidth - 22, height - 26, 22, ">", () -> scroll(Math.max(36, height - 150)));
        buttons.stream().filter(b -> b.id == 43).forEach(b -> b.active = offset > 0);
        buttons.stream().filter(b -> b.id == 44).forEach(b -> b.active = offset < maxScroll);
    }
    private boolean hasPage(Page p) {
        return p == Page.GENERAL || p == Page.SYNC || p == Page.SESSIONS || p == Page.PROJECTS || p == Page.TIMER || p == Page.HOTKEYS
                || LegacySettingsModel.settings().stream().anyMatch(s -> s.page() == p)
                || LegacyUiRegistry.entries().stream().anyMatch(e -> e.category().equalsIgnoreCase(p.name()));
    }
    private void action(int id, int x, int y, int w, String label, Runnable action) {
        button(id, x, y, w, label); actions.put(id, action);
    }
    private void command(List<Row> rows, Page category, String section, String label, String value, Runnable action) {
        if (query.isBlank() ? category != page : !SettingsUi.matches(label + " " + section + " " + category.label, query)) return;
        rows.add(new Row(label, "", section, null, false, value, action));
    }
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        command(rows, Page.GENERAL, "Mining helpers", "Perimeter Block List", "Open", () -> minecraft.openScreen(new PerimeterBlocksScreen(this)));
        command(rows, Page.HUD, "Layout", "Move HUD", "Open", () -> minecraft.openScreen(new HudEditorScreen(this)));
        command(rows, Page.SESSIONS, "Sessions", "Session Summary", "Open", () -> minecraft.openScreen(new LegacySessionScreen(this, null)));
        command(rows, Page.SESSIONS, "Sessions", "Saved Sessions", "Open", () -> minecraft.openScreen(new LegacyHistoryScreen(this)));
        command(rows, Page.PROJECTS, "Projects", "Project Manager", "Open", () -> minecraft.openScreen(new ProjectManagerScreen(this)));
        command(rows, Page.TIMER, "Block timer", "Timer", "Open", () -> minecraft.openScreen(new TimerScreen(this)));
        command(rows, Page.SYNC, "Website sync", "Website Account", "Open", () -> minecraft.openScreen(new LegacyLinkScreen(this)));
        command(rows, Page.SYNC, "Website sync", "Sync Scoreboard", "Select", () -> minecraft.openScreen(new LegacyScoreboardScreen(this)));
        for (LegacyUiRegistry.Entry entry : LegacyUiRegistry.entries()) {
            Page category;
            try { category = Page.valueOf(entry.category().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { category = Page.GENERAL; }
            command(rows, category, category.label, entry.label(), "Open", () -> minecraft.openScreen(entry.screen().apply(this)));
        }
        for (var setting : LegacySettingsModel.filter(LegacySettingsModel.settings(), page, query))
            rows.add(new Row(setting.label(), setting.description(), setting.section(), setting.config(), setting.slider(), "", null));
        if (page == Page.HOTKEYS || !query.isBlank()) for (MmmHotkey key : LegacySettingsModel.hotkeys()) {
            if (!query.isBlank() && !SettingsUi.matches(key.getPrettyName() + " " + key.getComment() + " Hotkeys " + key.getStorageString(), query)) continue;
            rows.add(new Row(key.getPrettyName(), key.getComment(), "Hotkeys", null, false,
                    key.getStorageString().isBlank() ? "Unassigned" : key.getStorageString().replace(',', '+'),
                    () -> minecraft.openScreen(new HotkeyEditorScreen(this, key, LegacySettingsModel.hotkeys()))));
        }
        return rows;
    }
    private void edit(IConfigBase config) {
        if (config instanceof IConfigBoolean b) { b.toggleBooleanValue(); Configs.saveToFile(); init(); }
        else if (config instanceof ConfigOptionList o) minecraft.openScreen(new OptionPickerScreen(this, o));
        else minecraft.openScreen(new ValueEditorScreen(this, config));
    }
    private void scroll(int amount) { offset = Math.max(0, Math.min(maxScroll, offset + amount)); init(); }
    @Override protected void buttonClicked(ButtonWidget button) {
        if (actions.containsKey(button.id)) actions.get(button.id).run(); else super.buttonClicked(button);
    }
    @Override public void render(int mouseX, int mouseY, float delta) {
        shell(); search.render();
        int statusX = left + 100, statusWidth = panelWidth - 164;
        boolean linked = WebsiteLinkManager.isCurrentPlayerLinked();
        String account = linked ? "Linked: " + WebsiteLinkManager.getPersistedUsername()
                : WebsiteLinkManager.hasPersistedLink() ? "Wrong linked account" : "Website link required";
        headerStatus(account, statusX, 8, statusWidth, linked ? 0xFF69D59A : 0xFFFFBA69);
        headerStatus(CloudSyncManager.getStatusSummary(), statusX, 21, statusWidth, 0xFFCCCCCC);
        if (query.isEmpty() && !search.isFocused()) fit("Search settings and hotkeys", contentLeft + 8, 51, contentWidth - 38, 0xFF949494);
        fit(query.isBlank() ? page.label : "Search results", contentLeft, 72, contentWidth, 0xFFF5F5F5);
        Placed hovered = null;
        LegacyDrawContext context = new LegacyDrawContext();
        try (var clip = context.clip(contentLeft, 87, contentLeft + contentWidth, height - 30)) {
            for (Card card : cards) {
                fill(card.x, card.y, card.x + card.width, card.y + card.height, SettingsUi.opacity(0xE00D0D0D));
                context.drawBorder(card.x, card.y, card.width, card.height, SettingsUi.opacity(0xFF272727));
                fit(card.title, card.x + 8, card.y + 8, card.width - 16, Configs.getMenuColor());
            }
            for (Placed p : placed) {
                int labelWidth = Math.max(12, p.width - p.controlWidth - (p.row.config == null ? 8 : 30));
                List<String> lines = textRenderer.split(p.row.label, labelWidth);
                for (int line = 0; line < Math.min(2, lines.size()); line++) fit(lines.get(line), p.x, p.y + 6 + line * 10, labelWidth, 0xFFF5F5F5);
                if (mouseX >= p.x && mouseX < p.x + p.width && mouseY >= p.y && mouseY < p.y + 32 && mouseY >= 87 && mouseY < height - 30) hovered = p;
            }
        }
        if (cards.isEmpty()) fit("No matching settings", contentLeft, 100, contentWidth, 0xFF949494);
        if (page == Page.SYNC && query.isBlank()) fit(CloudSyncManager.getStatusSummary(), contentLeft, height - 20, contentWidth - 56, 0xFF949494);
        super.render(mouseX, mouseY, delta);
        if (mouseX >= statusX && mouseX < statusX + statusWidth && mouseY >= 6 && mouseY < 33) {
            List<String> status = new ArrayList<>(textRenderer.split(account, Math.min(280, width - 24)));
            status.addAll(textRenderer.split(CloudSyncManager.getStatusDetail(), Math.min(280, width - 24)));
            status.add("Click to open Website Account");
            renderTooltip(status, mouseX, mouseY);
        }
        if (maxScroll > 0) {
            int track = Math.max(1, height - 117), thumb = Math.max(12, track * track / (track + maxScroll));
            int y = 87 + (track - thumb) * offset / maxScroll;
            fill(contentLeft + contentWidth - 4, 87, contentLeft + contentWidth - 2, height - 30, 0xFF272727);
            fill(contentLeft + contentWidth - 4, y, contentLeft + contentWidth - 2, y + thumb, Configs.getMenuColor());
        }
        if (hovered != null && dragging == null) {
            List<String> tooltip = new ArrayList<>(textRenderer.split(hovered.row.label, Math.min(280, width - 24)));
            if (!hovered.row.description.isBlank()) tooltip.addAll(textRenderer.split(hovered.row.description, Math.min(280, width - 24)));
            if (hovered.row.config != null) tooltip.addAll(textRenderer.split("Value: " + hovered.row.config.getStringValue() + " / Default: " + hovered.row.config.getDefaultStringValue(), Math.min(280, width - 24)));
            renderTooltip(tooltip, mouseX, mouseY);
        }
    }
    private void headerStatus(String value, int x, int y, int available, int color) {
        String visible = textRenderer.trim(value, Math.max(0, available));
        text(visible, x + available - textRenderer.getWidth(visible), y, color);
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        if (button == 0 && x >= left + 100 && x < left + panelWidth - 64 && y >= 6 && y < 33) {
            minecraft.openScreen(new LegacyLinkScreen(this));
            return;
        }
        search.mouseClicked(x, y, button);
        if (button == 0) for (ButtonWidget b : buttons) if (b instanceof SettingButton s && s.slider && x >= b.x && x < b.x + b.getWidth() && y >= b.y && y < b.y + 20) {
            dragging = s; s.slide(x); return;
        }
        super.mouseClicked(x, y, button);
    }
    @Override protected void mouseDragged(int x, int y, int b, long elapsed) { if (dragging != null) dragging.slide(x); }
    @Override protected void mouseReleased(int x, int y, int b) {
        if (dragging != null) { dragging = null; Configs.saveToFile(); } super.mouseReleased(x, y, b);
    }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (!query.isEmpty()) { query = ""; offset = 0; init(); } else super.keyPressed(c, key); return;
        }
        if (!search.isFocused() && !Character.isISOControl(c)) search.setFocused(true);
        search.keyPressed(c, key);
        if (!query.equals(search.getText())) { query = search.getText(); offset = 0; init(); search.setFocused(true); }
    }
    @Override public void handleMouse() { super.handleMouse(); int wheel = Mouse.getEventDWheel(); if (wheel != 0) {
        int x = Mouse.getEventX() * width / minecraft.width;
        if (x < contentLeft) { sideOffset += wheel > 0 ? -1 : 1; init(); } else scroll(wheel > 0 ? -36 : 36);
    }}
    @Override public void tick() { search.tick(); }
    @Override public void removed() { if (dragging != null) Configs.saveToFile(); super.removed(); }
}
