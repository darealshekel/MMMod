package com.mmm.storage;

import com.mmm.MMM;
import java.time.Duration;
import java.util.Objects;
import java.util.ArrayDeque;
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
        enqueue(key, operation, true);
    }

    public static void submitOrdered(String key, Runnable operation)
    {
        enqueue(key, operation, false);
    }

    private static void enqueue(String key, Runnable operation, boolean coalescible)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        PENDING.compute(key, (ignored, current) -> {
            PendingOperation pending = current == null ? new PendingOperation() : current;
            Operation last = pending.operations.peekLast();
            if (coalescible && last != null && last.coalescible()) pending.operations.removeLast();
            pending.operations.addLast(new Operation(operation, coalescible));
            schedule(key, pending);
            return pending;
        });
    }

    public static void cancel(String key)
    {
        if (key == null)
        {
            return;
        }
        PENDING.computeIfPresent(key, (ignored, pending) -> {
            pending.operations.removeIf(Operation::coalescible);
            return pending;
        });
    }

    public static boolean flush(Duration timeout)
    {
        long timeoutMs = Math.max(1L, timeout == null ? 3_000L : timeout.toMillis());
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try
        {
            do
            {
                Future<?> marker = EXECUTOR.submit(() -> { });
                marker.get(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (PENDING.isEmpty()) return true;
            }
            while (System.nanoTime() < deadline);
            return false;
        }
        catch (Exception exception)
        {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
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
        while (true)
        {
            AtomicReference<Operation> next = new AtomicReference<>();
            PENDING.computeIfPresent(key, (ignored, current) -> {
                if (current != pending) return current;
                next.set(pending.operations.pollFirst());
                if (next.get() == null)
                {
                    pending.scheduled.set(false);
                    return null;
                }
                return pending;
            });
            if (next.get() == null) return;
            try
            {
                next.get().action().run();
            }
            catch (RuntimeException exception)
            {
                MMM.LOGGER.warn("[MMM] Background persistence failed for {}: {}", key, exception.getMessage());
            }
        }
        }
        finally
        {
            PENDING.computeIfPresent(key, (ignored, current) -> {
                if (current != pending) return current;
                pending.scheduled.set(false);
                if (pending.operations.isEmpty()) return null;
                schedule(key, pending);
                return pending;
            });
        }
    }

    private record Operation(Runnable action, boolean coalescible) {}

    private static final class PendingOperation
    {
        private final ArrayDeque<Operation> operations = new ArrayDeque<>();
        private final AtomicBoolean scheduled = new AtomicBoolean();
    }
}
