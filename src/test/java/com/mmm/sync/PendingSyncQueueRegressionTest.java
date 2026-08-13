package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingSyncQueueRegressionTest
{
    @TempDir
    Path tempDir;

    @Test
    void senderExceptionSchedulesRetryWithoutStickingFlush()
            throws Exception
    {
        AtomicInteger attempts = new AtomicInteger();
        PendingSyncQueue queue = queue(item -> {
            if (attempts.incrementAndGet() == 1)
            {
                throw new IllegalStateException("temporary failure");
            }
            return SyncSendResult.success(200, "{}");
        });
        try
        {
            queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "live", payload(1), true, "test exception");
            queue.requestFlush("test");
            await(() -> retryCount(queue) == 1 && queue.snapshot().flushActive() == false);

            queue.forceFlush("manual test retry");
            await(() -> queue.snapshot().queueSize() == 0 && queue.snapshot().flushActive() == false);

            assertEquals(2, attempts.get());
            assertFalse(queue.snapshot().flushActive());
        }
        finally
        {
            queue.shutdown();
        }
    }

    @Test
    void requestTimeoutSchedulesRetryAndClearsActiveFlush()
            throws Exception
    {
        PendingSyncQueue queue = queue(item -> {
            throw new java.util.concurrent.CompletionException(
                    new java.net.http.HttpTimeoutException("request timed out"));
        });
        try
        {
            queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "timeout", payload(7), true, "test timeout");
            queue.requestFlush("test timeout");
            await(() -> retryCount(queue) == 1 && queue.snapshot().flushActive() == false);

            assertEquals(1, queue.snapshot().queueSize());
            assertTrue(queue.snapshotItemsForTests().get(0).nextRetryAtMs > System.currentTimeMillis());
        }
        finally
        {
            queue.shutdown();
        }
    }
    @Test
    void nullSenderResultBecomesRetry()
            throws Exception
    {
        PendingSyncQueue queue = queue(item -> null);
        try
        {
            queue.enqueue(SyncItemType.PLAYER_TOTAL_DIGS, "digs", payload(2), true, "test null");
            queue.requestFlush("test");
            await(() -> retryCount(queue) == 1 && queue.snapshot().flushActive() == false);

            assertEquals(1, queue.snapshot().queueSize());
            assertFalse(queue.snapshot().flushActive());
        }
        finally
        {
            queue.shutdown();
        }
    }

    @Test
    void replacingDedupeKeyKeepsNewestPayloadAndTrigger()
    {
        PendingSyncQueue queue = queue(item -> SyncSendResult.success(200, "{}"));
        try
        {
            queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "same", payload(1), true, "first");
            queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "same", payload(2), true, "second");

            QueuedSyncItem item = queue.snapshotItemsForTests().get(0);
            assertEquals(1, queue.snapshot().queueSize());
            assertEquals(2, item.payload.get("value").getAsInt());
            assertEquals("second", item.triggerReason);
        }
        finally
        {
            queue.shutdown();
        }
    }

    @Test
    void immediatelyDueItemWinsOverFutureRetryTimestamp()
            throws Exception
    {
        PendingSyncQueue queue = queue(item -> SyncSendResult.retry(503, "later", ""));
        try
        {
            queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "retry", payload(1), true, "retry");
            queue.requestFlush("test");
            await(() -> retryCount(queue) == 1 && queue.snapshot().flushActive() == false);
            queue.enqueue(SyncItemType.PLAYER_TOTAL_DIGS, "due", payload(2), true, "due now");

            assertEquals(0L, queue.nextAttemptAtMs(
                    SyncItemType.CLOUD_LIVE_STATE,
                    SyncItemType.PLAYER_TOTAL_DIGS));
            assertEquals(0L, queue.snapshot().nextAttemptAtMs());
        }
        finally
        {
            queue.shutdown();
        }
    }

    @Test
    void queuedItemAndTriggerSurviveRestart()
    {
        Path store = this.tempDir.resolve("restart-queue.json");
        PendingSyncQueue first = new PendingSyncQueue(store, item -> SyncSendResult.success(200, "{}"), new PendingSyncQueue.Listener() {});
        first.initialize();
        first.enqueue(SyncItemType.CLOUD_FINISHED_SESSION, "session", payload(3), true, "saved session");
        first.shutdown();

        PendingSyncQueue second = new PendingSyncQueue(store, item -> SyncSendResult.success(200, "{}"), new PendingSyncQueue.Listener() {});
        try
        {
            second.initialize();
            QueuedSyncItem restored = second.snapshotItemsForTests().get(0);
            assertEquals(1, second.snapshot().queueSize());
            assertEquals("saved session", restored.triggerReason);
            assertTrue(restored.isValid());
        }
        finally
        {
            second.shutdown();
        }
    }

    @Test
    void malformedPrimaryQueueRecoversFromValidatedBackup()
            throws Exception
    {
        Path store = this.tempDir.resolve("recover-queue.json");
        PendingSyncQueue first = new PendingSyncQueue(store, item -> SyncSendResult.success(200, "{}"), new PendingSyncQueue.Listener() {});
        first.initialize();
        first.enqueue(SyncItemType.CLOUD_LIVE_STATE, "first", payload(1), true, "first saved item");
        first.enqueue(SyncItemType.PLAYER_TOTAL_DIGS, "second", payload(2), true, "creates backup");
        first.shutdown();

        java.nio.file.Files.writeString(store, "{broken");

        PendingSyncQueue recovered = new PendingSyncQueue(store, item -> SyncSendResult.success(200, "{}"), new PendingSyncQueue.Listener() {});
        try
        {
            recovered.initialize();
            assertEquals(1, recovered.snapshot().queueSize());
            assertEquals("first", recovered.snapshotItemsForTests().get(0).dedupeKey);
        }
        finally
        {
            recovered.shutdown();
        }
    }

    @Test
    void staleAccountItemsCanBeRemovedAndStayRemovedAfterRestart()
    {
        Path store = this.tempDir.resolve("account-queue.json");
        PendingSyncQueue first = new PendingSyncQueue(store, item -> SyncSendResult.success(200, "{}"), new PendingSyncQueue.Listener() {});
        first.initialize();
        JsonObject oldAccount = payload(1);
        oldAccount.addProperty("minecraft_uuid", "old-account");
        JsonObject currentAccount = payload(2);
        currentAccount.addProperty("minecraft_uuid", "current-account");
        first.enqueue(SyncItemType.CLOUD_LIVE_STATE, "old", oldAccount, true, "old account");
        first.enqueue(SyncItemType.PLAYER_TOTAL_DIGS, "current", currentAccount, true, "current account");

        int removed = first.removeMatching(item -> item.type != SyncItemType.WEBSITE_LINK_CLAIM
                && item.payload.get("minecraft_uuid").getAsString().equals("current-account") == false);
        assertEquals(1, removed);
        assertEquals(1, first.snapshot().queueSize());
        assertEquals("current-account", first.snapshotItemsForTests().get(0).payload.get("minecraft_uuid").getAsString());
        first.shutdown();

        PendingSyncQueue second = new PendingSyncQueue(store, item -> SyncSendResult.success(200, "{}"), new PendingSyncQueue.Listener() {});
        try
        {
            second.initialize();
            assertEquals(1, second.snapshot().queueSize());
            assertEquals("current-account", second.snapshotItemsForTests().get(0).payload.get("minecraft_uuid").getAsString());
        }
        finally
        {
            second.shutdown();
        }
    }
    private PendingSyncQueue queue(PendingSyncQueue.Sender sender)
    {
        PendingSyncQueue queue = new PendingSyncQueue(
                this.tempDir.resolve("queue-" + System.nanoTime() + ".json"),
                sender,
                new PendingSyncQueue.Listener() {});
        queue.initialize();
        return queue;
    }

    private static JsonObject payload(int value)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("value", value);
        return payload;
    }

    private static int retryCount(PendingSyncQueue queue)
    {
        return queue.snapshotItemsForTests().isEmpty()
                ? 0
                : queue.snapshotItemsForTests().get(0).retryCount;
    }

    private static void await(BooleanSupplier condition)
            throws Exception
    {
        long deadline = System.currentTimeMillis() + 3_000L;
        while (condition.getAsBoolean() == false && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for asynchronous queue state.");
    }
}
