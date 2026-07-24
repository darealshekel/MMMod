package com.mmm.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Keeps one stable local identity for a multiplayer server across MMMod versions. */
public final class WorldIdentity
{
    private static final String MULTIPLAYER_PREFIX = "server_";

    private WorldIdentity()
    {
    }

    public static String canonicalWorldId(String worldId, String kind, String host)
    {
        String fallback = normalizeWorldId(worldId);
        if (isMultiplayer(kind, host) && normalizeHost(host).isBlank() == false)
        {
            return multiplayerWorldId(host);
        }
        return fallback;
    }

    public static String multiplayerWorldId(String host)
    {
        String normalizedHost = normalizeHost(host);
        return normalizedHost.isBlank()
                ? MULTIPLAYER_PREFIX + "unknown"
                : MULTIPLAYER_PREFIX + shortHash(normalizedHost);
    }

    public static Set<String> legacyWorldIds(String canonicalWorldId, String kind, String host)
    {
        Set<String> aliases = new LinkedHashSet<>();
        String normalizedHost = normalizeHost(host);
        if (isMultiplayer(kind, host) && normalizedHost.isBlank() == false)
        {
            aliases.add(host.trim());
            aliases.add(normalizedHost);
        }
        aliases.remove(normalizeWorldId(canonicalWorldId));
        return aliases;
    }

    public static boolean matchesCurrentWorld(String candidateWorldId,
                                              String currentWorldId,
                                              String currentKind,
                                              String currentHost)
    {
        String candidate = normalizeWorldId(candidateWorldId);
        String current = canonicalWorldId(currentWorldId, currentKind, currentHost);
        if (candidate.equals(current))
        {
            return true;
        }

        String normalizedHost = normalizeHost(currentHost);
        return isMultiplayer(currentKind, currentHost)
                && normalizedHost.isBlank() == false
                && candidate.equalsIgnoreCase(normalizedHost);
    }

    private static boolean isMultiplayer(String kind, String host)
    {
        return "multiplayer".equalsIgnoreCase(kind == null ? "" : kind.trim())
                || normalizeHost(host).isBlank() == false;
    }

    private static String normalizeWorldId(String worldId)
    {
        return worldId == null || worldId.isBlank() ? "default" : worldId.trim();
    }

    private static String normalizeHost(String host)
    {
        return host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    }

    private static String shortHash(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(6, digest.length); i++)
            {
                builder.append(String.format("%02x", digest[i]));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException exception)
        {
            return Integer.toHexString(value.hashCode());
        }
    }
}
