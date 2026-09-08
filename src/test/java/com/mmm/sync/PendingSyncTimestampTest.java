package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingSyncTimestampTest
{
    @TempDir
    Path tempDir;

    @Test
    void failedSyncDoesNotUpdateSuccessfulTimestamp()
            throws Exception
    {
        PendingSyncQueue queue = new PendingSyncQueue(
                this.tempDir.resolve("failed.json"),
                item -> SyncSendResult.retry(503, "temporary", ""),
                new PendingSyncQueue.Listener() {});
        try
        {
            queue.initialize();
            queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "live", payload(), true, "automatic");
            queue.requestFlush("test");
            await(() -> queue.snapshotItemsForTests().get(0).retryCount == 1
                    && queue.snapshot().flushActive() == false);

            assertEquals(0L, queue.snapshot().lastSuccessfulSyncAtMs());
        }
        finally
        {
            queue.shutdown();
        }
    }

    @Test
    void successfulTimestampSurvivesQueueRestart()
            throws Exception
    {
        Path store = this.tempDir.resolve("successful.json");
        PendingSyncQueue first = new PendingSyncQueue(
                store,
                item -> SyncSendResult.success(200, "{}"),
                new PendingSyncQueue.Listener() {});
        first.initialize();
        first.enqueue(SyncItemType.CLOUD_LIVE_STATE, "live", payload(), true, "manual");
        first.requestFlush("manual");
        await(() -> first.snapshot().queueSize() == 0 && first.snapshot().lastSuccessfulSyncAtMs() > 0L);
        long successfulAt = first.snapshot().lastSuccessfulSyncAtMs();
        first.shutdown();

        PendingSyncQueue second = new PendingSyncQueue(
                store,
                item -> SyncSendResult.success(200, "{}"),
                new PendingSyncQueue.Listener() {});
        try
        {
            second.initialize();
            assertEquals(successfulAt, second.snapshot().lastSuccessfulSyncAtMs());
        }
        finally
        {
            second.shutdown();
        }
    }

    private static JsonObject payload()
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("value", 1);
        return payload;
    }

    private static void await(BooleanSupplier condition)
            throws Exception
    {
        long deadline = System.currentTimeMillis() + 3_000L;
        while (condition.getAsBoolean() == false && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for queue state.");
    }
}
