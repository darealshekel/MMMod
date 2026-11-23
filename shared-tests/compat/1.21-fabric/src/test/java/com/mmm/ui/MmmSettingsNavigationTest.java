package com.mmm.ui;

import com.mmm.ui.MmmSettingsScreen.ControlKind;
import com.mmm.ui.MmmSettingsScreen.SettingRow;
import com.mmm.ui.SettingsNavigation.Page;
import com.mmm.ui.MmmSettingsScreen.SettingsSection;
import com.mmm.ui.MmmSettingsScreen.SidebarItem;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MmmSettingsNavigationTest
{
    @Test void inventoryGroupsRowsUnderNamedCategories() throws Exception
    {
        Page<SettingRow, SettingsSection> root = SettingsNavigation.<SettingRow, SettingsSection>build(inventory());
        assertTrue(root.rows().isEmpty());
        assertTrue(root.children().size() <= 7);
        var initial = root.initialCategory();
        assertEquals("Everyday settings", initial.title());
        assertTrue(initial.rowGroups().stream().flatMap(group -> group.rows().stream())
                .anyMatch(row -> row.label().equals("Mining Tracker") && row.kind() == ControlKind.BOOLEAN));
        assertTrue(root.children().stream().anyMatch(page -> page.title().equals("HUD")));
        var digging = root.children().stream().filter(page -> page.title().equals("Digging & goals"))
                .findFirst().orElseThrow();
        var inline = digging.rowGroups().stream().flatMap(group -> group.rows().stream())
                .map(SettingRow::label).toList();
        assertTrue(inline.containsAll(List.of("Flat Digger", "Goal Tracking")));
    }

    @Test void everySettingOccursExactlyOnceWithItsOriginalConfigAndAction() throws Exception
    {
        List<SettingsSection> sections = inventory();
        List<SettingRow> expected = MmmSettingsScreen.searchSections(sections, "").stream()
                .flatMap(section -> section.rows().stream()).filter(row -> row.kind() != ControlKind.HEADING).toList();
        List<SettingRow> actual = allRows(SettingsNavigation.<SettingRow, SettingsSection>build(sections));
        assertEquals(expected.size(), actual.size());
        var counts = new IdentityHashMap<SettingRow, Integer>();
        actual.forEach(row -> counts.merge(row, 1, Integer::sum));
        expected.forEach(row -> assertEquals(1, counts.get(row), row.label()));
    }

    @Test void noTopicExposesMoreThanEightSettings() throws Exception
    {
        assertBounded(SettingsNavigation.<SettingRow, SettingsSection>build(inventory()));
    }

    @Test void largeHudGroupIsSplitByPurpose() throws Exception
    {
        Page<SettingRow, SettingsSection> hud = find(SettingsNavigation.<SettingRow, SettingsSection>build(inventory()), "HUD content");
        assertTrue(hud.rows().isEmpty());
        assertEquals(8, find(hud, "Mining totals").rows().size());
        assertEquals(6, find(hud, "Speed & time").rows().size());
        assertEquals(5, find(hud, "Goal display").rows().size());
        assertEquals(2, find(hud, "HUD background").rows().size());
    }

    @Test void searchStillFindsEverySettingWithoutOpeningItsCategory() throws Exception
    {
        List<SettingsSection> sections = inventory();
        for (SettingRow row : allRows(SettingsNavigation.<SettingRow, SettingsSection>build(sections)))
        {
            assertTrue(MmmSettingsScreen.searchSections(sections, row.label()).stream()
                    .flatMap(section -> section.rows().stream()).anyMatch(found -> found == row), row.label());
        }
    }

    @Test void searchStillIncludesDescriptionAndInternalConfigName() throws Exception
    {
        List<SettingsSection> sections = inventory();
        assertEquals(1, MmmSettingsScreen.searchSections(sections, " RAINBOW ").size());
        for (SettingRow row : allRows(SettingsNavigation.<SettingRow, SettingsSection>build(sections)))
        {
            if (row.config() != null)
                assertTrue(MmmSettingsScreen.searchSections(sections, row.config().getName()).stream()
                        .flatMap(section -> section.rows().stream()).anyMatch(found -> found == row), row.label());
        }
        assertTrue(MmmSettingsScreen.searchSections(sections, "no-such-mmm-setting").isEmpty());
    }

    @Test void headingsSearchTheirChildrenButNeverBecomeEmptyTopics()
    {
        SettingRow setting = new SettingRow("One", "description", null, ControlKind.BOOLEAN);
        var section = new SettingsSection(SidebarItem.HUD, "Example", "", List.of(
                SettingRow.heading("EMPTY"), SettingRow.heading("POSITION"), setting));
        Page<SettingRow, SettingsSection> root = SettingsNavigation.<SettingRow, SettingsSection>build(List.of(section));
        assertEquals(List.of(setting), allRows(root));
        assertEquals(List.of("POSITION", "One"), MmmSettingsScreen.searchSections(List.of(section), "position")
                .getFirst().rows().stream().map(SettingRow::label).toList());
        assertBounded(root);
    }

    @Test void optionalGraphAndDeveloperSectionsRemainReachable()
    {
        SettingRow graph = new SettingRow("Graph", "", null, ControlKind.OPTION);
        SettingRow developer = new SettingRow("Generate Dev Run", "", null, ControlKind.ACTION);
        Page<SettingRow, SettingsSection> root = SettingsNavigation.<SettingRow, SettingsSection>build(List.of(
                new SettingsSection(SidebarItem.SPEED_GRAPH, "Speed graph", "", List.of(graph)),
                new SettingsSection(SidebarItem.ABOUT, "Dev tools", "", List.of(developer))));
        assertTrue(allRows(root).containsAll(List.of(graph, developer)));
        assertNotNull(find(root, "Mining visuals"));
    }

    @Test void narrowScrollingKeepsPartialRowsButRejectsClippedControls()
    {
        // At 320x180 GUI size, one wheel tick used to hide both adjacent stacked rows.
        assertEquals(28, MmmSettingsScreen.visibleHeight(72, 52, 96, 174));
        assertEquals(50, MmmSettingsScreen.visibleHeight(124, 52, 96, 174));
        assertEquals(0, MmmSettingsScreen.visibleHeight(44, 52, 96, 174));
        assertEquals(0, MmmSettingsScreen.visibleHeight(174, 52, 96, 174));
        assertEquals(18, MmmSettingsScreen.visibleHeight(101, 18, 96, 174));
        assertNotEquals(18, MmmSettingsScreen.visibleHeight(90, 18, 96, 174));
        assertNotEquals(18, MmmSettingsScreen.visibleHeight(164, 18, 96, 174));
        for (int offset = 0; offset < 52; offset++)
        {
            int first = MmmSettingsScreen.visibleHeight(100 - offset, 52, 96, 174);
            int next = MmmSettingsScreen.visibleHeight(152 - offset, 52, 96, 174);
            assertTrue(first + next > 0, "Blank viewport at offset " + offset);
        }
    }

    private static List<SettingsSection> inventory()
    {
        return MmmSettingsScreen.settingsInventory();
    }

    private static List<SettingRow> allRows(Page<SettingRow, SettingsSection> page)
    {
        List<SettingRow> result = new ArrayList<>(page.rows());
        page.children().forEach(child -> result.addAll(allRows(child)));
        return result;
    }

    private static void assertBounded(Page<SettingRow, SettingsSection> page)
    {
        assertTrue(page.rows().size() <= 8, page.title() + ": " + page.rows().size());
        assertTrue(page.rows().isEmpty() || page.children().isEmpty(), page.title());
        page.children().forEach(MmmSettingsNavigationTest::assertBounded);
    }

    private static Page<SettingRow, SettingsSection> find(Page<SettingRow, SettingsSection> page, String title)
    {
        if (page.title().equals(title)) return page;
        for (Page<SettingRow, SettingsSection> child : page.children())
        {
            Page<SettingRow, SettingsSection> found = find(child, title);
            if (found != null) return found;
        }
        return null;
    }
}
