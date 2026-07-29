package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncPersistenceTest
{
    @Test
    void keepsOnlyTheLatestOperationQueuedForEachKey() throws Exception
    {
        String key = "test-" + UUID.randomUUID();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger latestValue = new AtomicInteger();

        AsyncPersistence.submit(key, () -> {
            firstStarted.countDown();
            try
            {
                releaseFirst.await(2L, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            executions.incrementAndGet();
        });
        assertTrue(firstStarted.await(2L, TimeUnit.SECONDS));

        for (int value = 1; value <= 100; value++)
        {
            int captured = value;
            AsyncPersistence.submit(key, () -> {
                latestValue.set(captured);
                executions.incrementAndGet();
            });
        }

        releaseFirst.countDown();
        assertTrue(AsyncPersistence.flush(Duration.ofSeconds(3L)));
        assertEquals(2, executions.get());
        assertEquals(100, latestValue.get());
    }
}
