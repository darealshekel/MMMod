package com.mmm.storage;

import com.mmm.sync.ScoreboardSourceResolver;
import com.mmm.MMM;
import com.mmm.config.Configs;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

public final class WorldSessionContext
{
    private static String currentWorldId = "default";
    private static String currentWorldName = "Unknown";
    private static String currentWorldKind = "unknown";
    private static String currentWorldHost = "";
    private static String lastDebugFingerprint = "";

    private WorldSessionContext()
    {
    }

    public static void update(MinecraftClient client)
    {
        WorldInfo info = resolve(client);
        maybeDebugResolvedWorld(info);
        currentWorldId = info.id();
        currentWorldName = info.displayName();
        currentWorldKind = info.kind();
        currentWorldHost = info.host();
    }

    public static WorldInfo resolve(MinecraftClient client)
    {
        if (client.getCurrentServerEntry() != null)
        {
            String address = client.getCurrentServerEntry().address;
            String displayName = client.getCurrentServerEntry().name;
            if (displayName == null || displayName.isBlank())
            {
                displayName = "Multiplayer Server";
            }

            String host = address == null ? "" : address.trim();
            // Keep local history stable without leaking raw server IPs/domains into paths or exports.
            String resolvedId = host.isBlank() ? sanitise(displayName) : "server_" + shortHash(host);
            return new WorldInfo(resolvedId, displayName.trim(), "multiplayer", host);
        }

        if (client.getServer() != null)
        {
            String levelName = client.getServer().getSaveProperties().getLevelName();
            String worldKey = resolveSingleplayerWorldKey(client, levelName);
            return new WorldInfo(worldKey, levelName, "singleplayer", "");
        }

        return new WorldInfo(currentWorldId, currentWorldName, currentWorldKind, currentWorldHost);
    }

    public static String getCurrentWorldId()
    {
        return currentWorldId;
    }

    public static String getCurrentWorldName()
    {
        return currentWorldName;
    }

    public static WorldInfo getCurrentWorldInfo()
    {
        return new WorldInfo(currentWorldId, currentWorldName, currentWorldKind, currentWorldHost);
    }

    private static String sanitise(String value)
    {
        if (value == null || value.isBlank())
        {
            return "unknown";
        }

        return value.toLowerCase().replaceAll("[^a-z0-9._-]+", "_");
    }

    private static void maybeDebugResolvedWorld(WorldInfo info)
    {
        if (Configs.Generic.WEBSITE_SYNC_DEBUG.getBooleanValue() == false || info == null)
        {
            return;
        }

        String fingerprint = info.id() + "|" + info.displayName() + "|" + info.kind();
        if (fingerprint.equals(lastDebugFingerprint))
        {
            return;
        }

        lastDebugFingerprint = fingerprint;
        MMM.LOGGER.info(
                "[MMM_DEBUG] world-context-resolved worldId={} displayName={} kind={} host=redacted",
                info.id(),
                info.displayName(),
                info.kind()
        );
    }

    private static String shortHash(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(6, digest.length); i++)
            {
                builder.append(String.format("%02x", digest[i]));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException exception)
        {
            return Integer.toHexString(value.trim().toLowerCase().hashCode());
        }
    }

    private static String resolveSingleplayerWorldKey(MinecraftClient client, String levelName)
    {
        try
        {
            Path savePath = client.getServer().getSavePath(WorldSavePath.ROOT);
            Path folderPath = savePath.getFileName();
            if (folderPath != null)
            {
                String folderName = folderPath.toString();
                if (!folderName.isBlank())
                {
                    return sanitise(folderName);
                }
            }
        }
        catch (Exception e)
        {
            MMM.LOGGER.warn("[MMM] Failed to resolve singleplayer world folder for '{}': {}", levelName, e.getMessage());
        }

        return sanitise(levelName);
    }

    public record WorldInfo(String id, String displayName, String kind, String host) {}
}
