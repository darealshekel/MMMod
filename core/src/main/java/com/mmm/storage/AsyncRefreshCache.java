package com.mmm.storage;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One background refresh at a time; invalidated reads never publish an older generation. */
final class AsyncRefreshCache<T>
{
    private final Executor executor;
    private final Supplier<T> loader;
    private final Consumer<RuntimeException> onFailure;
    private final long intervalMs;
    private T value;
    private long generation;
    private long nextRefreshAtMs;
    private boolean refreshing;

    AsyncRefreshCache(Executor executor, Supplier<T> loader, long intervalMs, Consumer<RuntimeException> onFailure)
    {
        this.executor = executor;
        this.loader = loader;
        this.intervalMs = intervalMs;
        this.onFailure = onFailure;
    }

    synchronized T get(long now)
    {
        if (!refreshing && now >= nextRefreshAtMs)
        {
            refreshing = true;
            long requestedGeneration = generation;
            executor.execute(() -> refresh(requestedGeneration, now));
        }
        return value;
    }

    synchronized void invalidate()
    {
        generation++;
        value = null;
        nextRefreshAtMs = 0L;
    }

    private void refresh(long requestedGeneration, long requestedAtMs)
    {
        T result = null;
        boolean success = false;
        try
        {
            result = loader.get();
            success = true;
        }
        catch (RuntimeException failure)
        {
            onFailure.accept(failure);
        }
        finally
        {
            synchronized (this)
            {
                if (generation == requestedGeneration)
                {
                    if (success)
                    {
                        value = result;
                    }
                    nextRefreshAtMs = requestedAtMs + intervalMs;
                }
                refreshing = false;
            }
        }
    }
}
