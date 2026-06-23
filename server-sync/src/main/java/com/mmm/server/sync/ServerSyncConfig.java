package com.mmm.server.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

final class ServerSyncConfig
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mmm-server-sync.json");

    boolean enabled = true;
    String endpoint = "https://sync.mmmaniacs.com/v1/sync";
    String sourceName = "";
    String sourceKey = "";
    String reporterUsername = "MMMServerSync";
    String host = "";
    int syncIntervalSeconds = 86_400;
    int maxPlayersPerSync = 512;

    static ServerSyncConfig load()
    {
        ServerSyncConfig config = new ServerSyncConfig();
        if (Files.exists(CONFIG_PATH))
        {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH))
            {
                ServerSyncConfig loaded = GSON.fromJson(reader, ServerSyncConfig.class);
                if (loaded != null)
                {
                    config = loaded;
                }
            }
            catch (Exception exception)
            {
                System.err.println("[MMM Server Sync] Failed to load config: " + exception.getMessage());
            }
        }
        config.normalize();
        config.save();
        return config;
    }

    void save()
    {
        try
        {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH))
            {
                GSON.toJson(this, writer);
            }
        }
        catch (IOException exception)
        {
            System.err.println("[MMM Server Sync] Failed to save config: " + exception.getMessage());
        }
    }

    String resolvedSourceName(MinecraftServer server)
    {
        String configured = clean(this.sourceName);
        if (configured.isBlank() == false)
        {
            return configured;
        }

        String motd = server == null ? "" : clean(server.getServerMotd());
        return motd.isBlank() ? "Minecraft Server" : motd;
    }

    String resolvedSourceKey(MinecraftServer server)
    {
        String configured = clean(this.sourceKey);
        if (configured.isBlank() == false)
        {
            return configured;
        }
        return slug(resolvedSourceName(server));
    }

    int syncIntervalTicks()
    {
        return Math.max(20, this.syncIntervalSeconds * 20);
    }

    private void normalize()
    {
        this.endpoint = clean(this.endpoint);
        if (this.endpoint.isBlank())
        {
            this.endpoint = "https://sync.mmmaniacs.com/v1/sync";
        }
        this.sourceName = clean(this.sourceName);
        this.sourceKey = clean(this.sourceKey);
        this.reporterUsername = clean(this.reporterUsername);
        if (this.reporterUsername.isBlank() || this.reporterUsername.length() > 16)
        {
            this.reporterUsername = "MMMServerSync";
        }
        this.host = clean(this.host);
        // Migrate the unreleased one-minute default without overriding deliberate custom intervals.
        if (this.syncIntervalSeconds == 60)
        {
            this.syncIntervalSeconds = 86_400;
        }
        this.syncIntervalSeconds = Math.max(900, Math.min(604_800, this.syncIntervalSeconds));
        this.maxPlayersPerSync = Math.max(1, Math.min(2048, this.maxPlayersPerSync));
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.replace('\u00A0', ' ').trim();
    }

    private static String slug(String value)
    {
        String slug = clean(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "minecraft-server" : slug;
    }
}
