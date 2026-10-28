package com.mmm.sync;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

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

        Map<String, SourceLeaderboardEntry> rowsByIdentity = new LinkedHashMap<>();
        Map<String, IdentityEvidence> identityEvidence = new LinkedHashMap<>();
        Set<String> fakeUsernames = new LinkedHashSet<>();
        long removedDigs = 0L;

        for (SourceLeaderboardEntry entry : validEntries)
        {
            String username = entry.username();
            String identityKey = "name:" + username.toLowerCase(Locale.ROOT);
            PlayerListEntry playerListEntry = CarpetFakePlayerDetector.findPlayerListEntry(client, username);

            if (playerListEntry != null && CarpetFakePlayerDetector.isConfirmedFakePlayer(client, playerListEntry))
            {
                fakeUsernames.add(username.toLowerCase(Locale.ROOT));
                removedDigs += entry.digs();
                continue;
            }

            IdentityEvidence evidence = null;
            if (playerListEntry != null
                    && playerListEntry.getProfile() != null
                    && playerListEntry.getProfile().getId() != null
                    && playerListEntry.getProfile().getName() != null
                    && playerListEntry.getProfile().getName().isBlank() == false)
            {
                username = playerListEntry.getProfile().getName();
                identityKey = "uuid:" + playerListEntry.getProfile().getId();
                evidence = new IdentityEvidence(username, playerListEntry.getProfile().getId().toString());
            }

            SourceLeaderboardEntry normalized = new SourceLeaderboardEntry(username, entry.digs(), entry.rank());
            SourceLeaderboardEntry existing = rowsByIdentity.get(identityKey);
            if (existing == null)
            {
                rowsByIdentity.put(identityKey, normalized);
            }
            else
            {
                removedDigs += Math.min(existing.digs(), normalized.digs());
                if (normalized.digs() > existing.digs()
                        || (normalized.digs() == existing.digs() && normalized.rank() < existing.rank()))
                {
                    rowsByIdentity.put(identityKey, normalized);
                }
            }

            if (evidence != null)
            {
                identityEvidence.put(username.toLowerCase(Locale.ROOT), evidence);
            }
        }

        List<SourceLeaderboardEntry> filteredEntries = rowsByIdentity.values().stream()
                .sorted(Comparator.comparingInt(SourceLeaderboardEntry::rank))
                .toList();
        return new FilterResult(
                filteredEntries,
                Set.copyOf(fakeUsernames),
                removedDigs > 0L,
                removedDigs,
                Map.copyOf(identityEvidence));
    }

    static long resolveTotal(SourceLeaderboardSnapshot snapshot, List<SourceLeaderboardEntry> entries)
    {
        long reportedTotal = snapshot == null ? 0L : Math.max(0L, snapshot.totalDigs());
        long rowTotal = entries == null ? 0L : entries.stream().mapToLong(SourceLeaderboardEntry::digs).sum();
        return Math.max(reportedTotal, rowTotal);
    }

    static long resolveTotal(SourceLeaderboardSnapshot snapshot, FilterResult filtered)
    {
        long reportedTotal = snapshot == null ? 0L : Math.max(0L, snapshot.totalDigs());
        long adjustedReportedTotal = Math.max(0L, reportedTotal - filtered.removedDigs());
        long rowTotal = filtered.entries().stream().mapToLong(SourceLeaderboardEntry::digs).sum();
        return Math.max(adjustedReportedTotal, rowTotal);
    }

    record IdentityEvidence(String username, String minecraftUuid) {}

    record FilterResult(
            List<SourceLeaderboardEntry> entries,
            Set<String> fakeUsernames,
            boolean filterCollapsedScoreboard,
            long removedDigs,
            Map<String, IdentityEvidence> identityEvidence
    )
    {
        IdentityEvidence identityFor(SourceLeaderboardEntry entry)
        {
            if (entry == null || entry.username() == null)
            {
                return null;
            }
            return this.identityEvidence.get(entry.username().toLowerCase(Locale.ROOT));
        }
    }
}
