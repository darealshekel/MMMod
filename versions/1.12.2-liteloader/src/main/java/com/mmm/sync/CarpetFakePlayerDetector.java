package com.mmm.sync;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;

final class CarpetFakePlayerDetector
{
    private CarpetFakePlayerDetector()
    {
    }

    static PlayerInfo findPlayerInfo(Minecraft client, String username)
    {
        if (client == null || client.getNetworkHandler() == null || username == null || username.isBlank())
        {
            return null;
        }

        for (PlayerInfo entry : client.getNetworkHandler().getOnlinePlayers())
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

    static boolean isConfirmedFakePlayer(Minecraft client, PlayerInfo entry)
    {
        if (entry == null || entry.getProfile() == null || entry.getProfile().getId() == null)
        {
            return true;
        }

        String profileName = entry.getProfile().getName();
        if (profileName == null || profileName.isBlank())
        {
            return true;
        }

        UUID expectedOfflineId = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + profileName).getBytes(StandardCharsets.UTF_8));
        return isLikelyOnlineMode(client) && entry.getProfile().getId().equals(expectedOfflineId);
    }

    private static boolean isLikelyOnlineMode(Minecraft client)
    {
        if (client == null || client.player == null || client.getNetworkHandler() == null)
        {
            return false;
        }

        UUID localPlayerId = client.player.getUuid();
        for (PlayerInfo entry : client.getNetworkHandler().getOnlinePlayers())
        {
            if (entry != null
                    && entry.getProfile() != null
                    && localPlayerId.equals(entry.getProfile().getId()))
            {
                return entry.getProfile().getId().version() == 4;
            }
        }

        return false;
    }
}
