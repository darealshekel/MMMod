package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingSyncQueueTest
{
    @TempDir
    Path tempDir;

    @Test
    void replacingLiveStateKeepsOnlyTheNewestPayload()
    {
        PendingSyncQueue queue = new PendingSyncQueue(
                this.tempDir.resolve("queue.json"),
                item -> SyncSendResult.success(200, "{}"),
                new PendingSyncQueue.Listener() {});
        queue.initialize();

        JsonObject stale = new JsonObject();
        stale.addProperty("total", 100L);
        JsonObject fresh = new JsonObject();
        fresh.addProperty("total", 200L);

        queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "cloud-live-state", stale, true);
        queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "cloud-live-state", fresh, true);

        assertEquals(1, queue.snapshotItemsForTests().size());
        assertEquals(200L, queue.snapshotItemsForTests().getFirst().payload.get("total").getAsLong());
        queue.shutdown();
    }

    @Test
    void freshLiveStateIsDeliveredBeforeOlderFinishedSessions() throws Exception
    {
        List<SyncItemType> delivered = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch deliveredBoth = new CountDownLatch(2);
        PendingSyncQueue queue = new PendingSyncQueue(
                this.tempDir.resolve("priority-queue.json"),
                item -> {
                    delivered.add(item.type);
                    deliveredBoth.countDown();
                    return SyncSendResult.success(200, "{}");
                },
                new PendingSyncQueue.Listener() {});
        queue.initialize();

        queue.enqueue(SyncItemType.CLOUD_FINISHED_SESSION, "old-session", new JsonObject(), false);
        queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "cloud-live-state", new JsonObject(), true);
        queue.requestFlush("test");

        org.junit.jupiter.api.Assertions.assertTrue(deliveredBoth.await(2, TimeUnit.SECONDS));
        assertEquals(List.of(SyncItemType.CLOUD_LIVE_STATE, SyncItemType.CLOUD_FINISHED_SESSION), delivered);
        queue.shutdown();
    }
}
