package com.mmm.sync;

import com.google.gson.JsonObject;
import com.mmm.storage.AsyncPersistence;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SyncReliabilityFixTest {
    @TempDir Path directory;

    private static JsonObject payload(String player, String world, String scope, int total) {
        JsonObject payload = new JsonObject(), source = new JsonObject();
        payload.addProperty("minecraft_uuid", player);
        payload.addProperty("sync_scope", scope);
        payload.addProperty("total", total);
        source.addProperty("key", world);
        source.addProperty("source_key", world);
        payload.add("world", source);
        return payload;
    }

    @Test void liveKeysSeparateAccountsWorldsAndScopes() {
        var original = payload("a", "one", "source", 100);
        assertEquals(SyncQueueManager.liveStateKey(original), SyncQueueManager.liveStateKey(payload("a", "one", "source", 200)));
        assertNotEquals(SyncQueueManager.liveStateKey(original), SyncQueueManager.liveStateKey(payload("b", "one", "source", 100)));
        assertNotEquals(SyncQueueManager.liveStateKey(original), SyncQueueManager.liveStateKey(payload("a", "two", "source", 100)));
        assertNotEquals(SyncQueueManager.liveStateKey(original), SyncQueueManager.liveStateKey(payload("a", "one", "personal", 100)));
    }

    @Test void rejectedAttemptDoesNotDeleteReplacementAndPersistsDuringBlockedSend() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), delivered = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        Path file = directory.resolve("queue.json");
        PendingSyncQueue queue = new PendingSyncQueue(file, item -> {
            if (attempts.incrementAndGet() == 1) {
                started.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return SyncSendResult.drop(422, "old data rejected", "");
            }
            assertEquals(200, item.payload.get("total").getAsInt());
            delivered.countDown();
            return SyncSendResult.success(200, "{}");
        }, new PendingSyncQueue.Listener() {});
        try {
            queue.initialize();
            queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "same", payload("a", "world", "source", 100), true);
            queue.requestFlush("test");
            assertTrue(started.await(3, TimeUnit.SECONDS));
            queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "same", payload("a", "world", "source", 200), true);
            assertTrue(AsyncPersistence.flush(Duration.ofSeconds(3)));
            assertEquals(200, new PendingSyncStore(file).load().items.getFirst().payload.get("total").getAsInt());
            release.countDown();
            assertTrue(delivered.await(3, TimeUnit.SECONDS));
        } finally { release.countDown(); queue.shutdown(); }
        assertEquals(2, attempts.get());
        assertEquals(0, queue.snapshot().queueSize());
    }
}
