package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.config.value.IConfigBase;
import com.mmm.hotkey.MmmHotkey;
import com.mmm.legacy.LegacySettingsModel.Page;
import com.mmm.legacy.settings.*;
import com.mmm.ui.SettingsNavigation;
import com.mmm.ui.ToolDirectory;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.gui.screen.Screen;

/** Bind navigation to the actual LiteLoader controls, including optional registered screens. */
final class SettingsBoard {
    record Row(String label, String description, String section, IConfigBase config, boolean slider,
               MmmHotkey key, Function<Screen, Screen> action) implements SettingsNavigation.Row {
        public boolean heading() { return false; }
        public String configName() {
            if (config != null) return config.getName();
            if (key != null) return key.getStorageString();
            if (section.equals("Tools")) {
                if (ToolDirectory.PROJECTS.label.equals(label)) return "Project Manager";
                if (ToolDirectory.HISTORY.label.equals(label)) return "Saved Sessions";
                if (ToolDirectory.SUMMARY.label.equals(label)) return "Session Summary";
            }
            return "";
        }
        String value() {
            if (config != null) return SettingsUi.display(config);
            if (key == null) return "Open";
            return key.getStorageString().isBlank() ? "Unassigned" : key.getStorageString().replace(',', '+');
        }
    }
    record Section(String title, Page group, List<Row> rows) implements SettingsNavigation.Section<Row> {
        public String category() { return group.name(); }
        public String categoryLabel() { return group.label; }
        public String description() { return title.equals("Everyday settings") ? "Tracking, goal, and chat" : group.label; }
    }
    final List<Section> sections;
    final SettingsNavigation.Page<Row, Section> home;
    final Map<ToolDirectory, Function<Screen, Screen>> tools = new EnumMap<>(ToolDirectory.class);
    SettingsBoard() {
        List<Row> rows = new ArrayList<>();
        Map<String,Page> groups = new LinkedHashMap<>();
        List<Section> result = new ArrayList<>();
        command(rows, groups, Page.GENERAL, "Mining helpers", "Perimeter Block List", PerimeterBlocksScreen::new);
        command(rows, groups, Page.HUD, "Layout", "Move HUD", HudEditorScreen::new);
        tools.put(ToolDirectory.HOTKEYS, LegacyHotkeyScreen::new);
        tools.put(ToolDirectory.SUMMARY, s -> new LegacySessionScreen(s, null));
        tools.put(ToolDirectory.HISTORY, LegacyHistoryScreen::new);
        tools.put(ToolDirectory.PROJECTS, ProjectManagerScreen::new);
        command(rows, groups, Page.TIMER, "Block timer controls", "Timer", TimerScreen::new);
        command(rows, groups, Page.SYNC, "Website sync", "Website Link", LegacyLinkScreen::new);
        command(rows, groups, Page.SYNC, "Website sync", "Sync Scoreboard", LegacyScoreboardScreen::new);
        for (var entry : LegacyUiRegistry.entries()) {
            if (entry.id().equals("leaderboards") || entry.id().equals("scoreboard")) {
                tools.put(entry.id().equals("scoreboard") ? ToolDirectory.SCOREBOARD : ToolDirectory.LEADERBOARDS, entry.screen());
                continue;
            }
            Page group;
            try { group = Page.valueOf(entry.category().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { group = Page.GENERAL; }
            command(rows, groups, group, group.label, entry.label(), entry.screen());
        }
        for (var s : LegacySettingsModel.settings()) {
            groups.put(s.section(), s.page());
            rows.add(new Row(s.label(), s.description(), s.section(), s.config(), s.slider(), null, null));
        }
        var keys = LegacySettingsModel.hotkeys();
        List<Row> bindings = new ArrayList<>();
        for (var key : keys) bindings.add(new Row(key.getPrettyName(), key.getComment(), ToolDirectory.HOTKEYS.label, null, false, key,
                s -> new HotkeyEditorScreen(s, key, keys)));
        List<Row> quick = new ArrayList<>();
        for (String label : List.of("Mining Tracker", "Main HUD", "Move HUD", "Daily Goal", "MMM Chat Messages"))
            rows.stream().filter(r -> r.label.equals(label) && r.key == null).findFirst().ifPresent(quick::add);
        List<Row> destinations = new ArrayList<>();
        tools.forEach((tool, factory) -> destinations.add(new Row(tool.label, tool.description, "Tools", null, false, null, factory)));
        result.add(new Section("Tools", Page.GENERAL, List.copyOf(destinations)));
        result.add(new Section("Everyday settings", Page.GENERAL, List.copyOf(quick)));
        for (Page group : Page.values()) for (var entry : groups.entrySet()) if (entry.getValue() == group) {
            var options = rows.stream().filter(r -> r.section.equals(entry.getKey()) && !quick.contains(r)).toList();
            if (!options.isEmpty()) result.add(new Section(entry.getKey(), group, options));
        }
        home = SettingsNavigation.build(result);
        // Bindings live on the dedicated board and remain individually searchable from Settings.
        result.add(new Section(ToolDirectory.HOTKEYS.label, Page.HOTKEYS, List.copyOf(bindings)));
        sections = List.copyOf(result);
    }
    private static void command(List<Row> rows, Map<String,Page> groups, Page group, String section,
                                String label, Function<Screen, Screen> action) {
        groups.put(section, group);
        rows.add(new Row(label, "", section, null, false, null, action));
    }
    List<Row> search(String query) {
        var found = new LinkedHashSet<Row>();
        for (var match : SettingsNavigation.search(sections, query)) found.addAll(match.rows());
        // The old menu also supported non-adjacent search terms across label, category and binding.
        for (var section : sections) for (var row : section.rows)
            if (SettingsUi.matches(row.label + " " + row.description + " " + row.section + " " + section.categoryLabel()
                    + " " + row.configName(), query)) found.add(row);
        return List.copyOf(found);
    }
    static List<Row> options(SettingsNavigation.Page<Row, Section> page) {
        List<Row> rows = new ArrayList<>(page.rows());
        for (var child : page.children()) rows.addAll(options(child));
        return rows;
    }
}
