package com.mmm.sync;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

final class CarpetFakePlayerDetector
{
    private CarpetFakePlayerDetector()
    {
    }

    static PlayerInfo findPlayerListEntry(Minecraft client, String username)
    {
        if (client == null || client.getConnection() == null || username == null || username.isBlank())
        {
            return null;
        }

        for (PlayerInfo entry : client.getConnection().getOnlinePlayers())
        {
            if (entry != null
                    && entry.getProfile() != null
                    && entry.getProfile().name() != null
                    && entry.getProfile().name().equalsIgnoreCase(username))
            {
                return entry;
            }
        }

        return null;
    }

    static boolean isConfirmedFakePlayer(Minecraft client, PlayerInfo entry)
    {
        if (entry == null || entry.getProfile() == null || entry.getProfile().id() == null)
        {
            return true;
        }

        String profileName = entry.getProfile().name();
        if (profileName == null || profileName.isBlank())
        {
            return true;
        }

        UUID expectedOfflineId = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + profileName).getBytes(StandardCharsets.UTF_8));
        return isLikelyOnlineMode(client) && entry.getProfile().id().equals(expectedOfflineId);
    }

    private static boolean isLikelyOnlineMode(Minecraft client)
    {
        if (client == null || client.player == null || client.getConnection() == null)
        {
            return false;
        }

        UUID localPlayerId = client.player.getUUID();
        for (PlayerInfo entry : client.getConnection().getOnlinePlayers())
        {
            if (entry != null
                    && entry.getProfile() != null
                    && localPlayerId.equals(entry.getProfile().id()))
            {
                return entry.getProfile().id().version() == 4;
            }
        }

        return false;
    }
}
