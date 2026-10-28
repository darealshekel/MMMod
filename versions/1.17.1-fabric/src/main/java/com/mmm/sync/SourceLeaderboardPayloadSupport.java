package com.mmm.sync;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;

final class SourceLeaderboardPayloadSupport
{
    private SourceLeaderboardPayloadSupport()
    {
    }

    static FilterResult filterEntries(MinecraftClient client, List<SourceLeaderboardEntry> entries)
    {
        List<SourceLeaderboardEntry> validEntries = entries == null
                ? List.of()
                : entries.stream()
                        .filter(SourceLeaderboardEntry::isValid)
                        .sorted(Comparator.comparingInt(SourceLeaderboardEntry::rank))
                        .toList();
        // A linked client reports scoreboard evidence, not player legitimacy.
        // Only explicit owner-managed exclusions may remove a public source row.
        return new FilterResult(validEntries, Set.of(), false);
    }

    static long resolveTotal(SourceLeaderboardSnapshot snapshot, List<SourceLeaderboardEntry> entries)
    {
        long reportedTotal = snapshot == null ? 0L : Math.max(0L, snapshot.totalDigs());
        long rowTotal = entries == null ? 0L : entries.stream().mapToLong(SourceLeaderboardEntry::digs).sum();
        return Math.max(reportedTotal, rowTotal);
    }

    record FilterResult(
            List<SourceLeaderboardEntry> entries,
            Set<String> fakeUsernames,
            boolean filterCollapsedScoreboard
    ) {}
}
