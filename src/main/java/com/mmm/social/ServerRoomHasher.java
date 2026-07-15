package com.mmm.social;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

public final class ServerRoomHasher
{
    private static final String ROOM_NAMESPACE = "mmm-mod-social-room-v1:";

    private ServerRoomHasher()
    {
    }

    public static String hash(String serverAddress)
    {
        String normalized = serverAddress == null ? "" : serverAddress.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(":25565"))
        {
            normalized = normalized.substring(0, normalized.length() - ":25565".length());
        }
        if (normalized.endsWith("."))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank())
        {
            return "";
        }

        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((ROOM_NAMESPACE + normalized).getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception)
        {
            return "";
        }
    }
}
