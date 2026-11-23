package com.mmm.smoke;

import com.mmm.hud.MinedBlockOverlayState;
import com.mmm.ui.ProgressShareData;
import com.mmm.ui.SettingsNavigation;
import java.util.List;
import java.util.Map;

/** Fast JDK-only contract checks, also runnable without launching Gradle or Minecraft. */
public final class HudCoreSmoke
{
    private record Row(String label, String description, String configName, boolean heading) implements SettingsNavigation.Row {}
    private record Section(String title, String description, String category, String categoryLabel, List<Row> rows)
            implements SettingsNavigation.Section<Row> {}

    public static void main(String[] args)
    {
        var state = new MinedBlockOverlayState();
        check(state.mined("minecraft:stone", 0), "initial block");
        check(!state.mined("minecraft:stone", 9_000_000_000L), "same block replaced icon");
        check(state.opacity(19_000_000_000L) == 1, "hold shorter than ten seconds since last break");
        check(Math.abs(state.opacity(19_400_000_000L) - 0.125F) < 0.0001, "fade curve");
        check(state.opacity(19_800_000_000L) == 0, "fade did not finish");
        check(state.mined("minecraft:dirt", 20_000_000_000L), "switch did not restart");
        state.clear();
        check(state.opacity(12_000_000_000L) == 0, "clear retained state");
        var progress = MinedBlockOverlayState.Progress.from("minecraft:red_terracotta", Map.of("minecraft:terracotta", 5000L));
        check(progress.count() == 5000 && progress.collectionId().equals("minecraft:terracotta"), "combined collection");
        for (int width : new int[] {320, 640, 960}) for (double scale : new double[] {0.25, 1, 3})
        {
            var layout = MinedBlockOverlayState.Layout.fit(width, 240, 360, scale, 820, 460);
            check(layout.x() >= 0 && layout.x() + 360 * layout.scale() <= width + 0.5, "horizontal bounds");
            check(layout.y() >= 0 && layout.y() + 36 * layout.scale() <= 240.5, "vertical bounds");
        }
        var row = new Row("World Playtime", "Saved world duration", "hudWorldPlaytimeVisible", false);
        var heading = new Row("PLAYTIME", "", "", true);
        var section = new Section("HUD content", "", "HUD", "HUD", List.of(heading, row));
        var sections = List.of(section);
        var root = SettingsNavigation.<Row, Section>build(sections);
        check(root.rows().isEmpty() && root.children().getFirst().title().equals("HUD"), "collapsed settings");
        check(root.children().getFirst().children().getFirst().rows().getFirst() == row, "lost row identity");
        check(SettingsNavigation.search(sections, " HUDWORLDPLAYTIMEVISIBLE ").getFirst().rows().getLast() == row, "config search");
        check(SettingsNavigation.search(sections, "saved world").getFirst().rows().getLast() == row, "description search");
        check(SettingsNavigation.search(sections, "missing").isEmpty(), "false search result");
        check(SettingsNavigation.visibleHeight(72, 52, 96, 174) == 28, "partial row clip");
        var share = ProgressShareData.session(123456, 3661000, 7200000, 1200, 1500);
        String caption = share.caption("Miner", "Session Summary", "World", "");
        check(caption.contains("123,456 blocks mined") && caption.contains("Session IGT: 01:01:01"), "session share format");
        check(!caption.contains("https://"), "unlinked profile leaked");
        check(ProgressShareData.collection("minecraft:stone", 125_000_000).metrics().getLast().value().equals("Completed"), "completed collection share");
        System.out.println("PASS: overlay timing/counts/layout, settings navigation/search/clipping, share text");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
