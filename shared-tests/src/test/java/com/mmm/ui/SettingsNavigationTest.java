package com.mmm.ui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsNavigationTest
{
    private record Row(String label, String description, String configName, boolean heading) implements SettingsNavigation.Row {}
    private record Section(String title, String description, String category, String categoryLabel, List<Row> rows)
            implements SettingsNavigation.Section<Row> {}

    @Test void overviewGroupsCategoriesWithoutExposingSettings()
    {
        var root = SettingsNavigation.<Row, Section>build(List.of(
                section("Everyday settings", "GENERAL", row("Tracking")),
                section("Digging helpers", "GENERAL", row("Flat Digger")),
                section("Goals", "NOTIFICATIONS", row("Daily Goal")),
                section("HUD content", "HUD", row("Title")),
                section("HUD colors", "COLORS", row("Color")),
                section("Visuals", "VISUALS", row("Animation")),
                section("Highlight", "BLOCK_ESP", row("Blocks")),
                section("Graph", "SPEED_GRAPH", row("Line")),
                section("Website sync", "SYNC", row("Enabled")),
                section("Dev tools", "ABOUT", row("Generate"))));
        assertTrue(root.rows().isEmpty());
        assertEquals(List.of("Everyday settings", "Digging & goals", "HUD", "Mining visuals", "Website sync", "Dev tools"),
                root.children().stream().map(SettingsNavigation.Page::title).toList());
        assertEquals(2, find(root, "Digging & goals").children().size());
        assertEquals(2, find(root, "HUD").children().size());
        assertEquals(3, find(root, "Mining visuals").children().size());
    }

    @Test void initialCategoryPrefersAnExactDirectMatchThenFirstChildOrSelf()
    {
        var everyday = new SettingsNavigation.Page<Row, Section>("Everyday settings", null, List.of(), List.of());
        var first = new SettingsNavigation.Page<Row, Section>("Everyday Settings", null, List.of(), List.of(everyday));
        var root = new SettingsNavigation.Page<Row, Section>("Settings", null, List.of(), List.of(first, everyday));
        assertSame(everyday, root.initialCategory());

        var fallback = new SettingsNavigation.Page<Row, Section>("Settings", null, List.of(), List.of(first));
        assertSame(first, fallback.initialCategory());
        assertSame(everyday, everyday.initialCategory());
    }

    @Test void rowGroupsPreserveExistingPagesAndEveryRowInPresentationOrder()
    {
        Row own = row("Root"), a = row("Repeated"), b = row("Repeated"), last = row("Last");
        Section section = section("Controls", "HUD", own, a, b, last);
        var empty = new SettingsNavigation.Page<Row, Section>("Empty", section, List.of(), List.of());
        var nested = new SettingsNavigation.Page<>("Nested", section, List.of(a, b), List.of());
        var branch = new SettingsNavigation.Page<>("Branch", section, List.<Row>of(), List.of(empty, nested));
        var sibling = new SettingsNavigation.Page<>("Sibling", section, List.of(last), List.of());
        var root = new SettingsNavigation.Page<>("Root", section, List.of(own), List.of(branch, sibling));

        var groups = root.rowGroups();
        assertEquals(List.of(root, nested, sibling), groups);
        var rows = groups.stream().flatMap(page -> page.rows().stream()).toList();
        assertEquals(section.rows().size(), rows.size());
        for (int i = 0; i < rows.size(); i++) assertSame(section.rows().get(i), rows.get(i));
        assertTrue(empty.rowGroups().isEmpty());
        assertThrows(UnsupportedOperationException.class, groups::clear);
    }

    @Test void toolsAreDirectHomeTilesForOnlyTheSupportedDestinations()
    {
        Row keys = row("Hotkeys"), history = row("History");
        Section section = section("Tools", "GENERAL", keys, history);
        var home = SettingsNavigation.<Row, Section>build(List.of(section));
        var directory = home.children().get(0);
        assertEquals("Tools", directory.title());
        assertTrue(directory.rows().isEmpty());
        assertEquals(List.of("Hotkeys", "History"), directory.children().stream().map(SettingsNavigation.Page::title).toList());
        assertSame(keys, directory.children().get(0).rows().get(0));
        assertSame(history, SettingsNavigation.search(List.of(section), "hist").get(0).rows().get(0));
        assertNull(find(home, "Leaderboards"));
        assertNull(ToolDirectory.find("Website Link"));
        for (ToolDirectory tool : ToolDirectory.values()) {
            assertSame(tool, ToolDirectory.find(tool.label));
            assertFalse(tool.description.isEmpty());
        }
    }

    @Test void topicsPreserveEveryOriginalRowExactlyOnceAndDiscardEmptyHeadings()
    {
        Row first = row("One"), second = row("Two");
        Section section = section("Controls", "HUD", heading("EMPTY"), heading("FIRST"), first, heading("SECOND"), second);
        var root = SettingsNavigation.<Row, Section>build(List.of(section));
        assertNull(find(root, "Empty"));
        assertSame(section, find(root, "First").source());
        var counts = new IdentityHashMap<Row, Integer>();
        allRows(root).forEach(row -> counts.merge(row, 1, Integer::sum));
        assertEquals(2, counts.size());
        assertEquals(1, counts.get(first));
        assertEquals(1, counts.get(second));
        assertThrows(UnsupportedOperationException.class, () -> root.children().clear());
        assertThrows(UnsupportedOperationException.class, () -> find(root, "First").rows().clear());
    }

    @Test void mainHudTopicsSplitByPurposeOnlyInHudContent()
    {
        Row totals = row("Mining Totals"), speed = row("Mining Speed"), goal = row("Goal ETA"), background = row("Main Background");
        var root = SettingsNavigation.<Row, Section>build(List.of(section("HUD content", "HUD",
                row("MMM Header"), totals, row("World Total"), speed, goal, background)));
        assertEquals(2, find(root, "Mining totals").rows().size());
        assertSame(speed, find(root, "Speed & time").rows().get(0));
        assertSame(goal, find(root, "Goal display").rows().get(0));
        assertSame(background, find(root, "HUD background").rows().get(0));
        var other = SettingsNavigation.<Row, Section>build(List.of(section("Other", "OTHER", totals, speed)));
        assertEquals(List.of(totals, speed), find(other, "Other").rows());
    }

    @Test void searchMatchesLabelsDescriptionsAndConfigNamesWithoutOpeningPages()
    {
        Row setting = new Row("World Playtime", "Saved duration", "hudWorldPlaytimeVisible", false);
        Row other = row("Other");
        var sections = List.of(section("Content", "HUD", setting, other));
        for (String query : List.of(" WORLD PLAYTIME ", "saved DURATION", "hudworldplaytimevisible"))
            assertSame(setting, SettingsNavigation.search(sections, query).get(0).rows().get(0));
        assertTrue(SettingsNavigation.search(sections, "missing").isEmpty());
        assertEquals(List.of(setting, other), SettingsNavigation.search(sections, null).get(0).rows());
        assertEquals(List.of(setting, other), SettingsNavigation.search(sections, "  ").get(0).rows());
    }

    @Test void sectionMetadataMatchesAllRowsAndRetainsSourceIdentity()
    {
        Row setting = row("Choice");
        Section section = new Section("Colors", "Appearance options", "COLORS", "Theme", List.of(setting));
        for (String query : List.of("colors", "appearance", "theme"))
        {
            var match = SettingsNavigation.search(List.of(section), query).get(0);
            assertSame(section, match.section());
            assertSame(setting, match.rows().get(0));
        }
    }

    @Test void headingMatchesIncludeChildrenAndChildMatchesKeepTheirHeading()
    {
        Row position = heading("POSITION"), x = row("Horizontal"), y = row("Vertical"), unrelated = row("Color");
        var sections = List.of(section("Controls", "HUD", position, x, y, heading("STYLE"), unrelated));
        assertEquals(List.of(position, x, y), SettingsNavigation.search(sections, "position").get(0).rows());
        assertEquals(List.of(position, y), SettingsNavigation.search(sections, "vertical").get(0).rows());
        assertThrows(UnsupportedOperationException.class, () -> SettingsNavigation.search(sections, "vertical").get(0).rows().clear());
    }

    @Test void nullableSearchMetadataAndEmptyInventoriesAreSafe()
    {
        Row setting = new Row("Choice", null, null, false);
        Section section = new Section("Example", null, "OTHER", null, List.of(setting));
        assertTrue(SettingsNavigation.search(List.of(section), "missing").isEmpty());
        assertEquals(List.of(setting), SettingsNavigation.search(List.of(section), "choice").get(0).rows());
        assertTrue(SettingsNavigation.<Row, Section>build(List.of()).children().isEmpty());
    }

    @Test void friendlyHeadingsRetainHudAcronym()
    {
        assertEquals("", SettingsNavigation.friendlyHeading(""));
        assertEquals("Main HUD", SettingsNavigation.friendlyHeading("MAIN HUD"));
        assertEquals("Block highlight", SettingsNavigation.friendlyHeading("BLOCK ESP"));
    }

    @Test void clippingRetainsPartialRowsButDistinguishesClippedControls()
    {
        assertEquals(28, SettingsNavigation.visibleHeight(72, 52, 96, 174));
        assertEquals(50, SettingsNavigation.visibleHeight(124, 52, 96, 174));
        assertEquals(0, SettingsNavigation.visibleHeight(44, 52, 96, 174));
        assertEquals(0, SettingsNavigation.visibleHeight(174, 52, 96, 174));
        assertEquals(18, SettingsNavigation.visibleHeight(101, 18, 96, 174));
        assertEquals(12, SettingsNavigation.visibleHeight(90, 18, 96, 174));
        assertEquals(10, SettingsNavigation.visibleHeight(164, 18, 96, 174));
    }

    private static Row row(String label) { return new Row(label, "", "", false); }
    private static Row heading(String label) { return new Row(label, "", "", true); }
    private static Section section(String title, String category, Row... rows)
    {
        return new Section(title, "", category, category, List.of(rows));
    }
    private static List<Row> allRows(SettingsNavigation.Page<Row, Section> page)
    {
        List<Row> rows = new ArrayList<>(page.rows());
        page.children().forEach(child -> rows.addAll(allRows(child)));
        return rows;
    }
    private static SettingsNavigation.Page<Row, Section> find(SettingsNavigation.Page<Row, Section> page, String title)
    {
        if (page.title().equals(title)) return page;
        for (var child : page.children())
        {
            var found = find(child, title);
            if (found != null) return found;
        }
        return null;
    }
}
