package com.mmm.server.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

final class ServerInstallCredentials
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CREDENTIALS_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("mmm-server-sync-credentials.json");
    private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    String installId;
    String installToken;

    static ServerInstallCredentials load()
    {
        ServerInstallCredentials credentials = null;
        if (Files.exists(CREDENTIALS_PATH))
        {
            try (Reader reader = Files.newBufferedReader(CREDENTIALS_PATH))
            {
                credentials = GSON.fromJson(reader, ServerInstallCredentials.class);
            }
            catch (Exception exception)
            {
                System.err.println("[MMM Server Sync] Failed to load install credentials: " + exception.getMessage());
            }
        }
        if (credentials == null)
        {
            credentials = new ServerInstallCredentials();
        }
        credentials.normalize();
        credentials.save();
        return credentials;
    }

    private void normalize()
    {
        this.installId = clean(this.installId);
        if (this.installId.isBlank())
        {
            this.installId = UUID.randomUUID().toString();
        }
        this.installToken = clean(this.installToken);
        if (this.installToken.isBlank())
        {
            this.installToken = UUID.randomUUID().toString() + UUID.randomUUID();
        }
    }

    private void save()
    {
        try
        {
            Files.createDirectories(CREDENTIALS_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CREDENTIALS_PATH))
            {
                GSON.toJson(this, writer);
            }
            try
            {
                Files.setPosixFilePermissions(CREDENTIALS_PATH, OWNER_ONLY_PERMISSIONS);
            }
            catch (UnsupportedOperationException ignored)
            {
                // Windows and some server filesystems do not expose POSIX permissions.
            }
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Unable to persist MMM server install credentials.", exception);
        }
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.trim();
    }
}
