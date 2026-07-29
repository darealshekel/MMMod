package com.mmm.storage;

import com.mmm.MMM;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Serializes and coalesces periodic persistence away from Minecraft's render thread. */
public final class AsyncPersistence
{
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mmm-persistence");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<String, Runnable> PENDING_OPERATIONS = new ConcurrentHashMap<>();
    private static final Set<String> SCHEDULED_KEYS = ConcurrentHashMap.newKeySet();

    private AsyncPersistence() {}

    public static void submit(String key, Runnable operation)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        PENDING_OPERATIONS.put(key, operation);
        scheduleDrain(key);
    }

    private static void scheduleDrain(String key)
    {
        if (SCHEDULED_KEYS.add(key))
        {
            EXECUTOR.execute(() -> drain(key));
        }
    }

    private static void drain(String key)
    {
        try
        {
            Runnable operation;
            while ((operation = PENDING_OPERATIONS.remove(key)) != null)
            {
                runOperation(key, operation);
            }
        }
        finally
        {
            SCHEDULED_KEYS.remove(key);
            if (PENDING_OPERATIONS.containsKey(key))
            {
                scheduleDrain(key);
            }
        }
    }

    private static void runOperation(String key, Runnable operation)
    {
        try
        {
            operation.run();
        }
        catch (RuntimeException exception)
        {
            MMM.LOGGER.warn("[MMM] Background persistence failed for {}: {}", key, exception.getMessage());
        }
    }

    public static void cancel(String key)
    {
        if (key != null)
        {
            PENDING_OPERATIONS.remove(key);
        }
    }

    public static boolean flush(Duration timeout)
    {
        long timeoutMs = Math.max(1L, timeout == null ? 3_000L : timeout.toMillis());
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try
        {
            while (true)
            {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L)
                {
                    return false;
                }
                Future<?> marker = EXECUTOR.submit(() -> { });
                marker.get(remainingNanos, TimeUnit.NANOSECONDS);
                if (PENDING_OPERATIONS.isEmpty() && SCHEDULED_KEYS.isEmpty())
                {
                    return true;
                }
            }
        }
        catch (Exception exception)
        {
            MMM.LOGGER.warn("[MMM] Timed out while flushing background persistence: {}", exception.getMessage());
            return false;
        }
    }
}
