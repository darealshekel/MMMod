package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncPersistenceCoalescingTest
{
    @Test
    void orderedRetirementCannotBeSkippedOrDeleteTheNextSession() throws Exception
    {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var operations = new java.util.concurrent.CopyOnWriteArrayList<String>();
        String key = "ordered-" + System.nanoTime();
        try
        {
            AsyncPersistence.submit(key, () -> {
                started.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                operations.add("old-write");
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            AsyncPersistence.submitOrdered(key, () -> operations.add("delete"));
            AsyncPersistence.submit(key, () -> operations.add("new-write"));
        }
        finally { release.countDown(); }
        assertTrue(AsyncPersistence.flush(Duration.ofSeconds(5)));
        assertEquals(java.util.List.of("old-write", "delete", "new-write"), operations);
    }
    private static int retainedKeys() throws Exception
    {
        var field = AsyncPersistence.class.getDeclaredField("PENDING");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).size();
    }

    @Test
    void retiresCompletedAndFailedWorldKeys() throws Exception
    {
        for (int i = 0; i < 2_000; i++)
        {
            AsyncPersistence.submit("world-" + i, () -> { });
        }
        AsyncPersistence.submit("failed-world", () -> { throw new IllegalStateException("test failure"); });
        assertTrue(AsyncPersistence.flush(Duration.ofSeconds(5)));
        assertEquals(0, retainedKeys());
    }

    @Test
    void preservesFinalWriteDuringConcurrentRetirement() throws Exception
    {
        AtomicInteger saved = new AtomicInteger();
        Thread writer = new Thread(() -> {
            for (int i = 1; i <= 10_000; i++)
            {
                int value = i;
                AsyncPersistence.submit("racing-world", () -> saved.set(value));
            }
        });
        writer.start();
        writer.join(5_000);
        assertTrue(!writer.isAlive());
        assertTrue(AsyncPersistence.flush(Duration.ofSeconds(5)));
        assertEquals(10_000, saved.get());
        assertEquals(0, retainedKeys());
    }

    @Test
    void keepsOnlyLatestOperationWhileExecutorIsBusy() throws Exception
    {
        String suffix = Long.toUnsignedString(System.nanoTime());
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        AtomicInteger staleRuns = new AtomicInteger();
        AtomicInteger latestRuns = new AtomicInteger();

        AsyncPersistence.submit("blocker-" + suffix, () -> {
            blockerStarted.countDown();
            try
            {
                releaseBlocker.await(2L, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(blockerStarted.await(2L, TimeUnit.SECONDS));

        String key = "coalesce-" + suffix;
        AsyncPersistence.submit(key, staleRuns::incrementAndGet);
        AsyncPersistence.submit(key, latestRuns::incrementAndGet);
        releaseBlocker.countDown();

        assertTrue(AsyncPersistence.flush(Duration.ofSeconds(3L)));
        assertEquals(0, staleRuns.get());
        assertEquals(1, latestRuns.get());
    }
}
