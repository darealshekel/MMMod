package com.mmm.storage;

import com.mmm.MMM;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** UUID-owned session storage shared by versions, never by different accounts. */
public final class AccountSessionStorage {
    private static volatile String account = "";
    private static volatile boolean ownsLegacy;

    private AccountSessionStorage() {}

    public static synchronized boolean activate(String uuid, String linkedUuid) {
        String key = normalize(uuid);
        if (key.isEmpty() || key.equals(account)) return false;
        account = key;
        ownsLegacy = false;
        try { ownsLegacy = claimLegacy(SharedStoragePaths.root(), key, linkedUuid); }
        catch (IOException error) { MMM.LOGGER.warn("[MMM] Could not establish legacy session ownership", error); }
        return true;
    }

    public static Path root() {
        return account.isEmpty() ? SharedStoragePaths.root().resolve("players/unidentified")
                : accountRoot(SharedStoragePaths.root(), account);
    }

    public static boolean ownsLegacy() { return ownsLegacy; }

    public static String normalize(String uuid) {
        String key = uuid == null ? "" : uuid.replace("-", "").toLowerCase(Locale.ROOT);
        return key.matches("[a-f0-9]{32}") ? key : "";
    }

    static Path accountRoot(Path shared, String uuid) {
        String key = normalize(uuid);
        if (key.isEmpty()) throw new IllegalArgumentException("A valid player UUID is required");
        return shared.resolve("players").resolve(key);
    }

    static boolean claimLegacy(Path shared, String uuid, String linkedUuid) throws IOException {
        String key = normalize(uuid);
        if (key.isEmpty()) return false;
        Files.createDirectories(shared);
        Path owner = shared.resolve("legacy-session-owner.txt");
        try (var channel = FileChannel.open(shared.resolve("legacy-session-owner.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.lock()) {
            if (!lock.isValid()) throw new IOException("Could not lock legacy session ownership");
            if (Files.isRegularFile(owner)) return key.equals(Files.readString(owner).trim());
            // A saved website identity is the only existing ownership evidence.
            // Unlinked/ambiguous history stays untouched for explicit recovery.
            if (!key.equals(normalize(linkedUuid))) return false;
            AtomicTextStorage.write(owner, key, true, value -> !normalize(value.trim()).isEmpty());
            return true;
        }
    }

    public static Path legacyFile(String directory, String name) {
        return ownsLegacy ? SharedStoragePaths.root().resolve(directory).resolve(name) : null;
    }
}
