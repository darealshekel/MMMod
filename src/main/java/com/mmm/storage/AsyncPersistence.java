package com.mmm.storage;

import com.mmm.MMM;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Serializes and coalesces periodic persistence away from Minecraft's render thread. */
public final class AsyncPersistence
{
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mmm-persistence");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<String, PendingOperation> PENDING = new ConcurrentHashMap<>();

    private AsyncPersistence() {}

    public static void submit(String key, Runnable operation)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        PendingOperation pending = PENDING.computeIfAbsent(key, ignored -> new PendingOperation());
        pending.latest.set(operation);
        schedule(key, pending);
    }

    public static void cancel(String key)
    {
        if (key == null)
        {
            return;
        }
        PendingOperation pending = PENDING.get(key);
        if (pending != null)
        {
            pending.latest.set(null);
        }
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

    private static void schedule(String key, PendingOperation pending)
    {
        if (pending.scheduled.compareAndSet(false, true))
        {
            EXECUTOR.execute(() -> drain(key, pending));
        }
    }

    private static void drain(String key, PendingOperation pending)
    {
        try
        {
            Runnable operation;
            while ((operation = pending.latest.getAndSet(null)) != null)
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
        }
        finally
        {
            pending.scheduled.set(false);
            if (pending.latest.get() != null)
            {
                schedule(key, pending);
            }
        }
    }

    private static final class PendingOperation
    {
        private final AtomicReference<Runnable> latest = new AtomicReference<>();
        private final AtomicBoolean scheduled = new AtomicBoolean();
    }
}
