package com.mmm.storage;

import com.mmm.MMM;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Serializes and coalesces periodic persistence away from Minecraft's render thread. */
public final class AsyncPersistence
{
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mmm-persistence");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<String, AtomicLong> GENERATIONS = new ConcurrentHashMap<>();

    private AsyncPersistence() {}

    public static void submit(String key, Runnable operation)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        long generation = GENERATIONS.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        EXECUTOR.execute(() -> {
            AtomicLong current = GENERATIONS.get(key);
            if (current == null || current.get() != generation) return;
            try
            {
                operation.run();
            }
            catch (RuntimeException exception)
            {
                MMM.LOGGER.warn("[MMM] Background persistence failed for {}: {}", key, exception.getMessage());
            }
        });
    }

    public static void cancel(String key)
    {
        if (key != null) GENERATIONS.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    public static boolean flush(Duration timeout)
    {
        long timeoutMs = Math.max(1L, timeout == null ? 3_000L : timeout.toMillis());
        try
        {
            Future<?> marker = EXECUTOR.submit(() -> { });
            marker.get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        }
        catch (Exception exception)
        {
            MMM.LOGGER.warn("[MMM] Timed out while flushing background persistence: {}", exception.getMessage());
            return false;
        }
    }
}
