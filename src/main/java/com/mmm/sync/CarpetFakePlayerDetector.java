package com.mmm.sync;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

final class CarpetFakePlayerDetector
{
    private static final Pattern SYNTHETIC_BASE_WITH_SUFFIX = Pattern.compile(
        "^(?:tp|dig|load|placer|piston|bore|trencher|digsort|fish|bb|nwe)\\d{1,2}$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SYNTHETIC_SUFFIX_WITH_BASE = Pattern.compile(
        "^\\d{1,2}(?:load|digsort|wide)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SYNTHETIC_DEFAULT_SKIN_WITH_SMALL_SUFFIX = Pattern.compile(
        "^(?:alex|steve)\\d$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SYNTHETIC_NUMERIC_ONLY = Pattern.compile("^\\d{1,3}$");

    private CarpetFakePlayerDetector()
    {
    }

    static Set<String> findLikelyFakeUsernames(MinecraftClient client, List<SourceLeaderboardEntry> entries)
    {
        Set<String> usernames = new LinkedHashSet<>();
        if (client == null || client.getNetworkHandler() == null || entries == null || entries.isEmpty())
        {
            return usernames;
        }

        Collection<PlayerListEntry> playerList = client.getNetworkHandler().getPlayerList();
        if (playerList == null || playerList.isEmpty())
        {
            return usernames;
        }

        for (SourceLeaderboardEntry entry : entries)
        {
            if (entry == null || entry.username() == null || entry.username().isBlank())
            {
                continue;
            }

            PlayerListEntry playerListEntry = findPlayerListEntry(playerList, entry.username());
            if (playerListEntry == null)
            {
                if (looksSynthetic(entry.username()))
                {
                    usernames.add(entry.username().toLowerCase(Locale.ROOT));
                }
                continue;
            }

            if (looksSynthetic(entry.username()) || isFakePlayer(playerListEntry))
            {
                usernames.add(entry.username().toLowerCase(Locale.ROOT));
            }
        }

        return usernames;
    }

    /**
     * Returns true when the given tab-list entry looks like a Carpet fake player
     * rather than a real Mojang account.
     *
     * Null profiles remain a reliable fake-player signal. Offline UUIDs are not:
     * legitimate players receive them on offline-mode and mixed proxy servers,
     * so UUID shape must never remove a public scoreboard row by itself.
     */
    private static boolean isFakePlayer(PlayerListEntry entry)
    {
        if (entry.getProfile() == null || entry.getProfile().getId() == null)
        {
            return true;
        }

        String profileName = entry.getProfile().getName();
        if (profileName == null || profileName.isBlank())
        {
            return true;
        }
        return false;
    }

    private static PlayerListEntry findPlayerListEntry(Collection<PlayerListEntry> playerList, String username)
    {
        for (PlayerListEntry entry : playerList)
        {
            if (entry != null
                    && entry.getProfile() != null
                    && entry.getProfile().getName() != null
                    && entry.getProfile().getName().equalsIgnoreCase(username))
            {
                return entry;
            }
        }

        return null;
    }

    private static boolean looksSynthetic(String username)
    {
        if (username == null || username.isBlank())
        {
            return false;
        }

        return SYNTHETIC_NUMERIC_ONLY.matcher(username).matches()
            || SYNTHETIC_BASE_WITH_SUFFIX.matcher(username).matches()
            || SYNTHETIC_SUFFIX_WITH_BASE.matcher(username).matches()
            || SYNTHETIC_DEFAULT_SKIN_WITH_SMALL_SUFFIX.matcher(username).matches()
            || "h4ck0s".equalsIgnoreCase(username);
    }
}
