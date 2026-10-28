package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncRefreshCacheTest
{
    @Test
    void neverRunsLoaderOnCallerAndCoalescesRequests()
    {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger loads = new AtomicInteger();
        AsyncRefreshCache<Integer> cache = new AsyncRefreshCache<>(tasks::add, loads::incrementAndGet, 1000, failure -> fail(failure));
        for (int i = 0; i < 100; i++) assertNull(cache.get(0));
        assertEquals(0, loads.get());
        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertEquals(1, cache.get(999));
        assertTrue(tasks.isEmpty());
        assertEquals(1, cache.get(1000));
        tasks.remove().run();
        assertEquals(2, cache.get(1001));
    }

    @Test
    void invalidatedInFlightResultCannotOverwriteNewerHistory()
    {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger loads = new AtomicInteger();
        AsyncRefreshCache<Integer> cache = new AsyncRefreshCache<>(tasks::add, loads::incrementAndGet, 1000, failure -> fail(failure));
        cache.get(0);
        cache.invalidate();
        tasks.remove().run();
        assertNull(cache.get(1));
        tasks.remove().run();
        assertEquals(2, cache.get(2));
        cache.invalidate();
        assertNull(cache.get(3));
        tasks.remove().run();
        assertEquals(3, cache.get(4));
    }

    @Test
    void failedRefreshKeepsLastGoodValueAndCanRetry()
    {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AsyncRefreshCache<Integer> cache = new AsyncRefreshCache<>(tasks::add, () -> {
            int value = loads.incrementAndGet();
            if (value == 2) throw new IllegalStateException("test read failure");
            return value;
        }, 1000, failure -> failures.incrementAndGet());
        cache.get(0);
        tasks.remove().run();
        cache.get(1000);
        tasks.remove().run();
        assertEquals(1, cache.get(1001));
        assertEquals(1, failures.get());
        cache.get(2000);
        tasks.remove().run();
        assertEquals(3, cache.get(2001));
    }
}
