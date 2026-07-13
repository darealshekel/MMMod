package com.mmm.sync;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
        Set<String> fakeUsernames = CarpetFakePlayerDetector.findLikelyFakeUsernames(client, validEntries);
        List<SourceLeaderboardEntry> filteredEntries = validEntries.stream()
                .filter(entry -> fakeUsernames.contains(entry.username().toLowerCase(Locale.ROOT)) == false)
                .toList();

        // Some proxy/plugin setups expose incomplete tab profiles or use names
        // that resemble automation accounts. Never let a heuristic collapse the
        // whole scoreboard; the API can review explicit exclusions separately.
        boolean filterCollapsedScoreboard = filteredEntries.size() < Math.min(3, validEntries.size());
        return new FilterResult(
                filterCollapsedScoreboard ? validEntries : filteredEntries,
                fakeUsernames,
                filterCollapsedScoreboard
        );
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
