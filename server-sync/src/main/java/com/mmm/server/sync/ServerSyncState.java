package com.mmm.server.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

final class ServerSyncState
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STATE_PATH = FabricLoader.getInstance().getConfigDir().resolve("mmm-server-sync-state.json");

    Map<String, PlayerRecord> players = new LinkedHashMap<>();
    String installStatus = "unknown";
    String lastSuccessfulSyncAt;
    String lastAttemptAt;
    String nextAttemptAt;
    int consecutiveFailures;
    boolean dataChangedSinceLastSuccessfulSync;
    transient boolean dirty;

    static ServerSyncState load()
    {
        ServerSyncState state = new ServerSyncState();
        if (Files.exists(STATE_PATH))
        {
            try (Reader reader = Files.newBufferedReader(STATE_PATH))
            {
                ServerSyncState loaded = GSON.fromJson(reader, ServerSyncState.class);
                if (loaded != null && loaded.players != null)
                {
                    state = loaded;
                }
            }
            catch (Exception exception)
            {
                System.err.println("[MMM Server Sync] Failed to load state: " + exception.getMessage());
            }
        }
        state.normalize();
        return state;
    }

    void save()
    {
        try
        {
            Files.createDirectories(STATE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(STATE_PATH))
            {
                GSON.toJson(this, writer);
            }
            this.dirty = false;
        }
        catch (IOException exception)
        {
            System.err.println("[MMM Server Sync] Failed to save state: " + exception.getMessage());
        }
    }

    PlayerRecord record(UUID uuid, String username)
    {
        String key = uuid == null ? normalizeUsername(username) : uuid.toString();
        PlayerRecord record = this.players.computeIfAbsent(key, ignored -> new PlayerRecord());
        record.uuid = uuid == null ? null : uuid.toString();
        if (username != null && username.isBlank() == false)
        {
            record.username = username.trim();
            record.usernameLower = normalizeUsername(username);
        }
        return record;
    }

    PlayerRecord findByUsername(String username)
    {
        String key = normalizeUsername(username);
        if (key.isBlank())
        {
            return null;
        }
        for (PlayerRecord record : this.players.values())
        {
            if (key.equals(record.usernameLower))
            {
                return record;
            }
        }
        return null;
    }

    void normalize()
    {
        if (this.players == null)
        {
            this.players = new LinkedHashMap<>();
        }
        this.installStatus = normalizeInstallStatus(this.installStatus);
        this.consecutiveFailures = Math.max(0, this.consecutiveFailures);
        this.players.values().forEach(PlayerRecord::normalize);
    }

    void markDataChanged()
    {
        this.dataChangedSinceLastSuccessfulSync = true;
        this.dirty = true;
    }

    private static String normalizeInstallStatus(String value)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized)
        {
            case "pending", "approved", "rejected" -> normalized;
            default -> "unknown";
        };
    }

    static String normalizeUsername(String username)
    {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    static final class PlayerRecord
    {
        String uuid;
        String username;
        String usernameLower;
        Map<String, Long> blockBreakdown = new LinkedHashMap<>();
        long pickaxeUses;
        long shovelUses;
        long axeUses;
        String lastMinedAt;

        void incrementBlock(String blockId)
        {
            if (ServerBlockCatalog.isValid(blockId) == false)
            {
                return;
            }
            this.blockBreakdown.merge(blockId.toLowerCase(Locale.ROOT), 1L, Long::sum);
            this.lastMinedAt = Instant.now().toString();
        }

        void incrementTool(String itemId)
        {
            String id = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
            if (id.endsWith("_pickaxe"))
            {
                this.pickaxeUses += 1L;
            }
            else if (id.endsWith("_shovel"))
            {
                this.shovelUses += 1L;
            }
            else if (id.endsWith("_axe") && id.endsWith("_pickaxe") == false)
            {
                this.axeUses += 1L;
            }
        }

        long toolUseTotal()
        {
            return Math.max(0L, this.pickaxeUses) + Math.max(0L, this.shovelUses) + Math.max(0L, this.axeUses);
        }

        long breakdownTotal()
        {
            return ServerBlockCatalog.validTotal(this.blockBreakdown);
        }

        Map<String, Long> sortedBreakdown()
        {
            Map<String, Long> sorted = new LinkedHashMap<>();
            this.blockBreakdown.entrySet().stream()
                    .filter(entry -> ServerBlockCatalog.isValid(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0L)
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                    .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
            return sorted;
        }

        private void normalize()
        {
            this.username = this.username == null ? "" : this.username.trim();
            this.usernameLower = normalizeUsername(this.usernameLower == null || this.usernameLower.isBlank() ? this.username : this.usernameLower);
            if (this.blockBreakdown == null)
            {
                this.blockBreakdown = new LinkedHashMap<>();
            }
            this.pickaxeUses = Math.max(0L, this.pickaxeUses);
            this.shovelUses = Math.max(0L, this.shovelUses);
            this.axeUses = Math.max(0L, this.axeUses);
        }
    }
}
