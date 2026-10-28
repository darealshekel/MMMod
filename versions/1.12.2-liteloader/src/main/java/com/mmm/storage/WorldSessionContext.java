package com.mmm.storage;

import com.mmm.util.MmmLog;
import com.mmm.config.Configs;
import net.minecraft.client.Minecraft;

public final class WorldSessionContext
{
    private static String currentWorldId = "default";
    private static String currentWorldName = "Unknown";
    private static String currentWorldKind = "unknown";
    private static String currentWorldSourceType = "server";
    private static String currentWorldHost = "";
    private static String lastDebugFingerprint = "";

    private WorldSessionContext()
    {
    }

    public static void update(Minecraft client)
    {
        WorldInfo info = resolve(client);
        maybeDebugResolvedWorld(info);
        currentWorldId = info.id();
        currentWorldName = info.displayName();
        currentWorldKind = info.kind();
        currentWorldSourceType = info.sourceType();
        currentWorldHost = info.host();
    }

    public static WorldInfo resolve(Minecraft client)
    {
        if (client == null) return getCurrentWorldInfo();
        if (client.getCurrentServerEntry() != null)
        {
            String address = client.getCurrentServerEntry().ip;
            String displayName = client.getCurrentServerEntry().name;
            if (displayName == null || displayName.isBlank())
            {
                displayName = "Multiplayer Server";
            }

            String host = address == null ? "" : address.trim();
            // Keep local history stable without leaking raw server IPs/domains into paths or exports.
            String resolvedId = host.isBlank() ? sanitise(displayName) : WorldIdentity.multiplayerWorldId(host);
            return new WorldInfo(resolvedId, displayName.trim(), "multiplayer", host, "server");
        }

        if (client.getServer() != null)
        {
            String levelName = client.getServer().getWorldName();
            String worldKey = resolveSingleplayerWorldKey(client, levelName);
            return new WorldInfo(worldKey, levelName, "singleplayer", "", resolveSingleplayerSourceType(client));
        }

        return new WorldInfo(currentWorldId, currentWorldName, currentWorldKind, currentWorldHost, currentWorldSourceType);
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
        return new WorldInfo(currentWorldId, currentWorldName, currentWorldKind, currentWorldHost, currentWorldSourceType);
    }

    private static String resolveSingleplayerSourceType(Minecraft client)
    {
        try
        {
            return client.getServer() != null && client.getServer().isHardcore()
                    ? "hsp"
                    : "ssp";
        }
        catch (RuntimeException exception)
        {
            MmmLog.LOGGER.debug("[MMM] Could not read hardcore world state: {}", exception.getMessage());
            return "ssp";
        }
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
        MmmLog.LOGGER.info(
                "[MMM_DEBUG] world-context-resolved displayName={} kind={} sourceType={}",
                info.displayName(),
                info.kind(),
                info.sourceType()
        );
    }

    private static String resolveSingleplayerWorldKey(Minecraft client, String levelName)
    {
        try
        {
            String folderName = client.getServer().getWorldSaveName();
            if (folderName != null && !folderName.isBlank())
            {
                return WorldIdentity.singleplayerWorldId(client.gameDir.toPath().resolve("saves").resolve(folderName));
            }
        }
        catch (Exception e)
        {
            MmmLog.LOGGER.warn("[MMM] Failed to resolve singleplayer world folder for '{}': {}", levelName, e.getMessage());
        }

        return sanitise(levelName);
    }

    public record WorldInfo(String id, String displayName, String kind, String host, String sourceType)
    {
        public WorldInfo(String id, String displayName, String kind, String host)
        {
            this(id, displayName, kind, host, sourceTypeForKind(kind));
        }

        private static String sourceTypeForKind(String kind)
        {
            return "singleplayer".equals(kind) ? "ssp" : "server";
        }
    }
}
