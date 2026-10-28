package com.mmm.sync;

import com.mmm.MMM;
import com.mmm.storage.AtomicTextStorage;
import com.mmm.storage.SharedStoragePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

final class SessionSyncState
{
    private static final Path SYNCED_SESSIONS_FILE = SharedStoragePaths.root().resolve("synced-session-keys.txt");
    private static final String VERSION_PREFIX = "#version=";
    private static final String CURRENT_VERSION = "stored-session-ack-v2";
    private static final Set<String> SYNCED_SESSION_KEYS = new LinkedHashSet<>();
    private static boolean loaded;

    private SessionSyncState()
    {
    }

    static synchronized boolean isSynced(String sessionKey)
    {
        loadIfNeeded();
        return normalizeSessionKey(sessionKey).isBlank() == false
                && SYNCED_SESSION_KEYS.contains(normalizeSessionKey(sessionKey));
    }

    static synchronized void markSynced(String sessionKey)
    {
        String normalized = normalizeSessionKey(sessionKey);
        if (normalized.isBlank())
        {
            return;
        }

        loadIfNeeded();
        if (SYNCED_SESSION_KEYS.add(normalized))
        {
            persist();
        }
    }

    private static void loadIfNeeded()
    {
        if (loaded)
        {
            return;
        }
        loaded = true;

        if (Files.exists(SYNCED_SESSIONS_FILE) == false)
        {
            return;
        }

        try
        {
            AtomicTextStorage.ReadResult result = AtomicTextStorage.readWithBackup(
                    SYNCED_SESSIONS_FILE,
                    SessionSyncState::isValidStateFile);
            if (result.value() == null)
            {
                return;
            }

            String storedVersion = "";
            for (String line : result.value().lines().toList())
            {
                if (line != null && line.startsWith(VERSION_PREFIX))
                {
                    storedVersion = line.substring(VERSION_PREFIX.length()).trim();
                    continue;
                }

                String normalized = normalizeSessionKey(line);
                if (normalized.isBlank() == false)
                {
                    SYNCED_SESSION_KEYS.add(normalized);
                }
            }

            if (CURRENT_VERSION.equals(storedVersion) == false)
            {
                if (SYNCED_SESSION_KEYS.isEmpty() == false)
                {
                    MMM.LOGGER.info("[MMM] Resyncing saved sessions once after sync acknowledgement update.");
                }
                SYNCED_SESSION_KEYS.clear();
                persist();
            }
            else if (result.recoveredFromBackup())
            {
                MMM.LOGGER.warn("[MMM] Recovered synced session acknowledgements from backup.");
                persist();
            }
        }
        catch (IOException e)
        {
            MMM.LOGGER.warn("[MMM] Failed to load synced session keys from {}: {}", SYNCED_SESSIONS_FILE, e.getMessage());
        }
    }

    private static void persist()
    {
        try
        {
            StringBuilder contents = new StringBuilder(VERSION_PREFIX)
                    .append(CURRENT_VERSION)
                    .append(System.lineSeparator());
            for (String sessionKey : SYNCED_SESSION_KEYS)
            {
                contents.append(sessionKey).append(System.lineSeparator());
            }
            AtomicTextStorage.write(
                    SYNCED_SESSIONS_FILE,
                    contents.toString(),
                    true,
                    SessionSyncState::isValidStateFile);
        }
        catch (IOException e)
        {
            MMM.LOGGER.warn("[MMM] Failed to save synced session keys to {}: {}", SYNCED_SESSIONS_FILE, e.getMessage());
        }
    }

    private static String normalizeSessionKey(String sessionKey)
    {
        return sessionKey == null ? "" : sessionKey.trim();
    }

    private static boolean isValidStateFile(String contents)
    {
        if (contents == null || contents.isBlank())
        {
            return false;
        }
        for (String line : contents.lines().toList())
        {
            String normalized = normalizeSessionKey(line);
            if (normalized.isBlank() || normalized.startsWith(VERSION_PREFIX))
            {
                continue;
            }
            if (normalized.matches("sess_[0-9]+") == false)
            {
                return false;
            }
        }
        return true;
    }
}
