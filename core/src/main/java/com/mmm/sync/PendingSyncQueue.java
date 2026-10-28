package com.mmm.sync;

import com.google.gson.JsonObject;
import com.mmm.MMM;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public final class PendingSyncQueue
{
    private static final String LOG_PREFIX = "[MMM_SYNC]";

    public interface Sender
    {
        SyncSendResult send(QueuedSyncItem item);
    }

    public interface Listener
    {
        default void onLoaded(Snapshot snapshot) {}
        default void onLoadFailed(String detail) {}
        default void onItemQueued(QueuedSyncItem item, boolean replaced, Snapshot snapshot) {}
        default void onItemAttemptStarted(QueuedSyncItem item, Snapshot snapshot) {}
        default void onFlushStarted(String reason, Snapshot snapshot) {}
        default void onFlushFinished(String reason, Snapshot snapshot) {}
        default void onItemSucceeded(QueuedSyncItem item, SyncSendResult result, Snapshot snapshot) {}
        default void onRetryScheduled(QueuedSyncItem item, SyncSendResult result, long nextRetryAtMs, Snapshot snapshot) {}
        default void onItemDropped(QueuedSyncItem item, SyncSendResult result, Snapshot snapshot) {}
        default void onPersistenceFailed(String detail, Snapshot snapshot) {}
    }

    public record Snapshot(int queueSize, long lastSuccessfulSyncAtMs, long nextAttemptAtMs, boolean flushActive, Map<SyncItemType, Integer> countsByType)
    {
        public int countFor(SyncItemType type)
        {
            return this.countsByType.getOrDefault(type, 0);
        }
    }

    private final Object lock = new Object();
    private final PendingSyncStore store;
    private final Sender sender;
    private final Listener listener;
    private final ExecutorService flushExecutor;
    private final Predicate<QueuedSyncItem> dueItemFilter;
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);

    private final List<QueuedSyncItem> items = new ArrayList<>();
    private long lastSuccessfulSyncAtMs;
    private volatile boolean flushActive;

    public PendingSyncQueue(Path storePath, Sender sender, Listener listener)
    {
        this(storePath, sender, listener, item -> true, defaultThreadFactory());
    }

    PendingSyncQueue(Path storePath, Sender sender, Listener listener, Predicate<QueuedSyncItem> dueItemFilter)
    {
        this(storePath, sender, listener, dueItemFilter, defaultThreadFactory());
    }

    PendingSyncQueue(Path storePath, Sender sender, Listener listener, ThreadFactory threadFactory)
    {
        this(storePath, sender, listener, item -> true, threadFactory);
    }

    PendingSyncQueue(Path storePath, Sender sender, Listener listener, Predicate<QueuedSyncItem> dueItemFilter, ThreadFactory threadFactory)
    {
        this.store = new PendingSyncStore(storePath);
        this.sender = sender;
        this.listener = listener;
        this.dueItemFilter = dueItemFilter == null ? (item -> true) : dueItemFilter;
        this.flushExecutor = Executors.newSingleThreadExecutor(threadFactory);
    }

    private static ThreadFactory defaultThreadFactory()
    {
        return runnable -> {
            Thread thread = new Thread(runnable, "MMM-SyncQueue");
            thread.setDaemon(true);
            return thread;
        };
    }

    public void initialize()
    {
        try
        {
            PendingSyncStore.StoredState state = this.store.load();
            synchronized (this.lock)
            {
                this.items.clear();
                this.items.addAll(state.items);
                this.lastSuccessfulSyncAtMs = state.lastSuccessfulSyncAtMs;
            }
            notifyListenerSafely("loaded", () -> this.listener.onLoaded(snapshot()));
        }
        catch (Exception exception)
        {
            String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            notifyListenerSafely("load-failed", () -> this.listener.onLoadFailed(detail));
        }
    }

    public void shutdown()
    {
        this.flushExecutor.shutdown();
        try
        {
            if (this.flushExecutor.awaitTermination(2, TimeUnit.SECONDS) == false)
            {
                this.flushExecutor.shutdownNow();
                this.flushExecutor.awaitTermination(1, TimeUnit.SECONDS);
            }
        }
        catch (InterruptedException interruptedException)
        {
            this.flushExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    int removeMatching(Predicate<QueuedSyncItem> predicate)
    {
        if (predicate == null)
        {
            return 0;
        }

        synchronized (this.lock)
        {
            int previousSize = this.items.size();
            this.items.removeIf(predicate);
            int removed = previousSize - this.items.size();
            if (removed > 0)
            {
                persistLocked();
            }
            return removed;
        }
    }

    public void enqueue(SyncItemType type, String dedupeKey, JsonObject payload, boolean replaceExisting)
    {
        enqueue(type, dedupeKey, payload, replaceExisting, "unspecified");
    }

    public void enqueue(SyncItemType type, String dedupeKey, JsonObject payload, boolean replaceExisting, String triggerReason)
    {
        QueuedSyncItem changedItem;
        boolean replaced = false;

        synchronized (this.lock)
        {
            long now = System.currentTimeMillis();
            QueuedSyncItem existing = replaceExisting ? findByTypeAndKey(type, dedupeKey) : null;
            if (existing != null)
            {
                existing.payload = payload == null ? new JsonObject() : payload.deepCopy();
                existing.triggerReason = normalizeTrigger(triggerReason);
                changedItem = existing.copy();
                replaced = true;
            }
            else
            {
                QueuedSyncItem item = QueuedSyncItem.create(type, dedupeKey, payload, now, triggerReason);
                this.items.add(item);
                this.items.sort(Comparator.comparingLong(value -> value.createdAtMs));
                changedItem = item.copy();
            }

            persistLocked();
        }

        boolean replacedExisting = replaced;
        notifyListenerSafely("item-queued", () -> this.listener.onItemQueued(changedItem, replacedExisting, snapshot()));
    }

    public void requestFlush(String reason)
    {
        if (hasDueItem() == false)
        {
            return;
        }

        if (this.flushScheduled.compareAndSet(false, true) == false)
        {
            MMM.LOGGER.info("{} send-skipped-flush-guard reason={} detail=flush_already_scheduled_or_active",
                    LOG_PREFIX,
                    reason == null || reason.isBlank() ? "manual" : reason);
            return;
        }

        this.flushExecutor.execute(() -> flushLoop(reason == null || reason.isBlank() ? "manual" : reason));
    }

    public void forceFlush(String reason)
    {
        if (shouldResetRetryBackoff(reason))
        {
            synchronized (this.lock)
            {
                for (QueuedSyncItem item : this.items)
                {
                    item.nextRetryAtMs = 0L;
                }
                persistLocked();
            }
        }
        requestFlush(reason);
    }

    private static boolean shouldResetRetryBackoff(String reason)
    {
        String normalized = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        return normalized.contains("manual") || normalized.contains("website link");
    }

    public Snapshot snapshot()
    {
        synchronized (this.lock)
        {
            return snapshotLocked();
        }
    }

    public long nextAttemptAtMs(SyncItemType... types)
    {
        synchronized (this.lock)
        {
            long now = System.currentTimeMillis();
            long nextFutureAttemptAtMs = Long.MAX_VALUE;
            for (QueuedSyncItem item : this.items)
            {
                if (matchesAnyType(item.type, types) == false)
                {
                    continue;
                }
                if (item.nextRetryAtMs <= now)
                {
                    return 0L;
                }
                nextFutureAttemptAtMs = Math.min(nextFutureAttemptAtMs, item.nextRetryAtMs);
            }
            return nextFutureAttemptAtMs == Long.MAX_VALUE ? 0L : nextFutureAttemptAtMs;
        }
    }
    public boolean hasDueItem()    {
        synchronized (this.lock)
        {
            long now = System.currentTimeMillis();
            return this.items.stream()
                    .anyMatch(item -> item.nextRetryAtMs <= now && this.dueItemFilter.test(item));
        }
    }

    List<QueuedSyncItem> snapshotItemsForTests()
    {
        synchronized (this.lock)
        {
            List<QueuedSyncItem> copy = new ArrayList<>();
            for (QueuedSyncItem item : this.items)
            {
                copy.add(item.copy());
            }
            return copy;
        }
    }

    private void flushLoop(String reason)
    {
        this.flushActive = true;

        try
        {
            notifyListenerSafely("flush-started", () -> this.listener.onFlushStarted(reason, snapshot()));
            while (true)
            {
                QueuedSyncItem item = nextDueItem();
                if (item == null)
                {
                    logNoDueItem(reason);
                    return;
                }

                notifyListenerSafely("attempt-started", () -> this.listener.onItemAttemptStarted(item.copy(), snapshot()));
                SyncSendResult result;
                try
                {
                    result = this.sender.send(item.copy());
                    if (result == null)
                    {
                        result = SyncSendResult.retry(-1, "Sync sender returned no result.", "");
                    }
                }
                catch (Exception exception)
                {
                    String detail = exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage();
                    MMM.LOGGER.warn("{} send-loop-failed id={} type={} trigger={} detail={}",
                            LOG_PREFIX,
                            item.id,
                            item.type,
                            normalizeTrigger(item.triggerReason),
                            detail);
                    result = SyncSendResult.retry(-1, detail, "");
                }
                handleResult(item, result);
            }
        }
        finally
        {
            this.flushActive = false;
            this.flushScheduled.set(false);
            notifyListenerSafely("flush-finished", () -> this.listener.onFlushFinished(reason, snapshot()));
        }
    }

    private void handleResult(QueuedSyncItem attemptedItem, SyncSendResult result)
    {
        long now = System.currentTimeMillis();

        synchronized (this.lock)
        {
            QueuedSyncItem current = findById(attemptedItem.id);
            if (current == null)
            {
                MMM.LOGGER.warn("{} send-skipped-item-invalid id={} reason=item_missing_during_handle",
                        LOG_PREFIX,
                        attemptedItem.id);
                return;
            }

            if (result.outcome() == SyncSendResult.Outcome.SUCCESS)
            {
                if (current.payload.equals(attemptedItem.payload) == false)
                {
                    current.nextRetryAtMs = 0L;
                    current.retryCount = 0;
                    persistLocked();
                    MMM.LOGGER.info(
                            "{} item-retained-newer-payload id={} type={} note=payload_changed_during_send queueSize={}",
                            LOG_PREFIX,
                            attemptedItem.id,
                            attemptedItem.type,
                            snapshotLocked().queueSize()
                    );
                    return;
                }

                this.items.removeIf(item -> item.id.equals(attemptedItem.id));
                this.lastSuccessfulSyncAtMs = now;
                persistLocked();
                notifyListenerSafely("item-succeeded", () -> this.listener.onItemSucceeded(attemptedItem, result, snapshotLocked()));
                return;
            }

            if (result.outcome() == SyncSendResult.Outcome.DROP)
            {
                this.items.removeIf(item -> item.id.equals(attemptedItem.id));
                persistLocked();
                notifyListenerSafely("item-dropped", () -> this.listener.onItemDropped(attemptedItem, result, snapshotLocked()));
                return;
            }

            current.retryCount++;
            current.lastRetryAtMs = now;
            current.nextRetryAtMs = now + SyncRetryPolicy.computeDelayMs(current.retryCount);
            persistLocked();
            notifyListenerSafely("retry-scheduled", () -> this.listener.onRetryScheduled(current.copy(), result, current.nextRetryAtMs, snapshotLocked()));
        }
    }

    private QueuedSyncItem nextDueItem()
    {
        synchronized (this.lock)
        {
            long now = System.currentTimeMillis();
            return this.items.stream()
                    .filter(item -> item.nextRetryAtMs <= now)
                    .filter(this.dueItemFilter)
                    .min(Comparator
                            .comparingInt(PendingSyncQueue::queuePriority)
                            .thenComparingInt(item -> item.retryCount)
                            .thenComparingLong(PendingSyncQueue::queueCreatedAtSortValue))
                    .map(QueuedSyncItem::copy)
                    .orElse(null);
        }
    }

    private static int queuePriority(QueuedSyncItem item)
    {
        if (item == null || item.type == null)
        {
            return 99;
        }

        return switch (item.type)
        {
            case WEBSITE_LINK_CLAIM -> 0;
            case CLOUD_LIVE_STATE -> 1;
            case CLOUD_FINISHED_SESSION -> 2;
            case PLAYER_TOTAL_DIGS -> 3;
        };
    }

    private static long queueCreatedAtSortValue(QueuedSyncItem item)
    {
        if (item == null)
        {
            return Long.MAX_VALUE;
        }

        return item.type == SyncItemType.CLOUD_FINISHED_SESSION ? -item.createdAtMs : item.createdAtMs;
    }

    private static boolean matchesAnyType(SyncItemType itemType, SyncItemType... types)
    {
        if (itemType == null || types == null || types.length == 0)
        {
            return false;
        }
        for (SyncItemType type : types)
        {
            if (itemType == type)
            {
                return true;
            }
        }
        return false;
    }

    private static String normalizeTrigger(String triggerReason)
    {
        return triggerReason == null || triggerReason.isBlank() ? "unspecified" : triggerReason.trim();
    }

    private QueuedSyncItem findByTypeAndKey(SyncItemType type, String dedupeKey)    {
        String normalizedKey = dedupeKey == null ? "" : dedupeKey;
        for (QueuedSyncItem item : this.items)
        {
            if (item.type == type && normalizedKey.equals(item.dedupeKey))
            {
                return item;
            }
        }

        return null;
    }

    private QueuedSyncItem findById(String id)
    {
        for (QueuedSyncItem item : this.items)
        {
            if (item.id.equals(id))
            {
                return item;
            }
        }

        return null;
    }

    private Snapshot snapshotLocked()
    {
        EnumMap<SyncItemType, Integer> counts = new EnumMap<>(SyncItemType.class);
        long now = System.currentTimeMillis();
        long nextFutureAttemptAtMs = Long.MAX_VALUE;
        boolean hasImmediatelyDueItem = false;
        for (QueuedSyncItem item : this.items)
        {
            counts.merge(item.type, 1, Integer::sum);
            if (item.nextRetryAtMs <= now)
            {
                hasImmediatelyDueItem = true;
            }
            else
            {
                nextFutureAttemptAtMs = Math.min(nextFutureAttemptAtMs, item.nextRetryAtMs);
            }
        }

        long nextAttemptAtMs = hasImmediatelyDueItem || nextFutureAttemptAtMs == Long.MAX_VALUE
                ? 0L
                : nextFutureAttemptAtMs;
        return new Snapshot(
                this.items.size(),
                this.lastSuccessfulSyncAtMs,
                nextAttemptAtMs,
                this.flushActive,
                Map.copyOf(counts));
    }
    private void persistLocked()
    {
        List<QueuedSyncItem> itemSnapshot = this.items.stream().map(QueuedSyncItem::copy).toList();
        long successfulSyncSnapshot = this.lastSuccessfulSyncAtMs;
        try
        {
            this.flushExecutor.execute(() -> {
                try
                {
                    this.store.save(itemSnapshot, successfulSyncSnapshot);
                }
                catch (Exception exception)
                {
                    String detail = exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage();
                    notifyListenerSafely("persistence-failed", () -> this.listener.onPersistenceFailed(detail, snapshot()));
                }
            });
        }
        catch (RuntimeException exception)
        {
            String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            notifyListenerSafely("persistence-failed", () -> this.listener.onPersistenceFailed(detail, snapshotLocked()));
        }
    }

    private void notifyListenerSafely(String event, Runnable callback)
    {
        try
        {
            callback.run();
        }
        catch (RuntimeException exception)
        {
            String detail = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            MMM.LOGGER.warn("{} listener-failed event={} detail={}", LOG_PREFIX, event, detail);
        }
    }

    static String formatInstant(long timestampMs)
    {
        if (timestampMs <= 0L)
        {
            return "never";
        }

        return Instant.ofEpochMilli(timestampMs).toString();
    }

    private void logNoDueItem(String reason)
    {
        synchronized (this.lock)
        {
            if (this.items.isEmpty())
            {
                MMM.LOGGER.info("{} send-skipped-queue-empty reason={}", LOG_PREFIX, reason);
                return;
            }

            long now = System.currentTimeMillis();
            long nextRetryAt = this.items.stream()
                    .mapToLong(item -> item.nextRetryAtMs)
                    .min()
                    .orElse(0L);
            long waitMs = Math.max(0L, nextRetryAt - now);
            MMM.LOGGER.info(
                    "{} send-skipped-item-not-due reason={} queueSize={} nextRetryAt={} waitMs={}",
                    LOG_PREFIX,
                    reason,
                    this.items.size(),
                    formatInstant(nextRetryAt),
                    waitMs
            );
        }
    }
}
