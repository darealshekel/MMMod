package com.mmm.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mmm.MMM;
import com.mmm.storage.AtomicJsonStorage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PendingSyncStore
{
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path path;

    PendingSyncStore(Path path)
    {
        this.path = path;
    }

    StoredState load() throws Exception
    {
        AtomicJsonStorage.ReadResult result = AtomicJsonStorage.readObjectWithBackup(this.path);
        if (result.value() == null)
        {
            return new StoredState(List.of(), 0L);
        }
        if (result.recoveredFromBackup())
        {
            MMM.LOGGER.warn("[MMM_SYNC] queue-state-recovered-from-backup source={}", result.source());
        }

        StoredState state = GSON.fromJson(result.value(), StoredState.class);
        if (state == null)
        {
            return new StoredState(List.of(), 0L);
        }

        List<QueuedSyncItem> validItems = new ArrayList<>();
        if (state.items != null)
        {
            for (QueuedSyncItem item : state.items)
            {
                if (item != null && item.isValid())
                {
                    validItems.add(item.copy());
                }
            }
        }

        validItems.sort(Comparator
                .comparingLong((QueuedSyncItem item) -> item.nextRetryAtMs)
                .thenComparingLong(item -> item.createdAtMs));

        long lastSuccessfulSyncAtMs = Math.max(0L, state.lastSuccessfulSyncAtMs);
        if (result.recoveredFromBackup())
        {
            save(validItems, lastSuccessfulSyncAtMs);
        }
        return new StoredState(validItems, lastSuccessfulSyncAtMs);
    }

    void save(List<QueuedSyncItem> items, long lastSuccessfulSyncAtMs) throws Exception
    {
        StoredState state = new StoredState(copyItems(items), Math.max(0L, lastSuccessfulSyncAtMs));
        AtomicJsonStorage.write(this.path, GSON.toJsonTree(state), true);
    }
    private List<QueuedSyncItem> copyItems(List<QueuedSyncItem> items)
    {
        List<QueuedSyncItem> copy = new ArrayList<>();
        for (QueuedSyncItem item : items)
        {
            copy.add(item.copy());
        }

        copy.sort(Comparator
                .comparingLong((QueuedSyncItem item) -> item.nextRetryAtMs)
                .thenComparingLong(item -> item.createdAtMs));

        return copy;
    }

    static final class StoredState
    {
        List<QueuedSyncItem> items;
        long lastSuccessfulSyncAtMs;

        StoredState(List<QueuedSyncItem> items, long lastSuccessfulSyncAtMs)
        {
            this.items = items;
            this.lastSuccessfulSyncAtMs = lastSuccessfulSyncAtMs;
        }
    }
}
