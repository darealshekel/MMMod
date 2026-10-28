package com.mmm.tags;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BoundedRequestsTest
{
    @Test
    void limitsConcurrencyAndPreservesAllResultsInOrder()
    {
        ArrayDeque<Runnable> pending = new ArrayDeque<>();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        List<Integer> input = IntStream.range(0, 240).boxed().toList();
        var result = BoundedRequests.map(input, 4, number -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            CompletableFuture<Integer> future = new CompletableFuture<>();
            pending.add(() -> { active.decrementAndGet(); future.complete(number); });
            return future;
        });
        assertEquals(4, pending.size());
        while (!pending.isEmpty()) pending.remove().run();
        assertEquals(input, result.join());
        assertEquals(4, peak.get());
        assertEquals(0, active.get());
    }

    @Test
    void sequentialBatchesKeepFallbackLimitGlobal()
    {
        ArrayDeque<Runnable> pending = new ArrayDeque<>();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        var result = BoundedRequests.map(List.of(1, 2, 3), 1, batch ->
                BoundedRequests.map(IntStream.range(0, 80).boxed().toList(), 4, number -> {
                    peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                    CompletableFuture<Integer> future = new CompletableFuture<>();
                    pending.add(() -> { active.decrementAndGet(); future.complete(number); });
                    return future;
                }));
        while (!pending.isEmpty()) pending.remove().run();
        assertEquals(3, result.join().size());
        assertEquals(4, peak.get());
    }

    @Test
    void handlesEmptyMissingAndFailedResponses()
    {
        assertEquals(List.of(), BoundedRequests.map(List.of(), 4, value -> CompletableFuture.completedFuture(value)).join());
        assertNull(BoundedRequests.map(List.of("missing"), 4, value -> CompletableFuture.completedFuture(null)).join().getFirst());
        assertTrue(BoundedRequests.map(List.of(1), 4,
                value -> CompletableFuture.failedFuture(new IllegalStateException("offline"))).isCompletedExceptionally());
        assertThrows(IllegalArgumentException.class, () -> BoundedRequests.map(List.of(), 0, CompletableFuture::completedFuture));
    }
}
