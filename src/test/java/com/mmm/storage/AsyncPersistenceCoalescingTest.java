package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncPersistenceCoalescingTest
{
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
