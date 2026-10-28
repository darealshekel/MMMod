package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingSyncListenerFailureTest
{
    @TempDir
    Path tempDir;

    @Test
    void listenerExceptionCannotStrandQueuedWork()
            throws Exception
    {
        PendingSyncQueue queue = new PendingSyncQueue(
                this.tempDir.resolve("listener-failure.json"),
                item -> SyncSendResult.success(200, "{}"),
                new PendingSyncQueue.Listener()
                {
                    @Override
                    public void onItemAttemptStarted(QueuedSyncItem item, PendingSyncQueue.Snapshot snapshot)
                    {
                        throw new IllegalStateException("test listener failure");
                    }
                });
        try
        {
            queue.initialize();
            JsonObject payload = new JsonObject();
            payload.addProperty("value", 1);
            queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "live", payload, true, "test");
            queue.requestFlush("test");

            await(() -> queue.snapshot().queueSize() == 0);
            assertFalse(queue.snapshot().flushActive());
            assertTrue(queue.snapshot().lastSuccessfulSyncAtMs() > 0L);
            assertEquals(0, queue.snapshotItemsForTests().size());
        }
        finally
        {
            queue.shutdown();
        }
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
