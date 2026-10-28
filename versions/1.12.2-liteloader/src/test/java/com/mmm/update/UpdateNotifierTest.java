package com.mmm.update;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpdateNotifierTest {
    @Test void disconnectDiscardsPendingDeliveryButCachesForNextConnection() {
        var future = new CompletableFuture<String>();
        AtomicInteger calls = new AtomicInteger();
        UpdateNotifier notices = new UpdateNotifier(() -> { calls.incrementAndGet(); return future; }, () -> 0);
        notices.join(Runnable::run);
        notices.onDisconnect();
        future.complete("1.0.21");
        assertNull(notices.poll(true));
        notices.join(Runnable::run);
        assertEquals(1, calls.get());
        assertNull(notices.poll(false));
        assertEquals("1.0.21", notices.poll(true));
        assertNull(notices.poll(true));
    }

    @Test void rapidReconnectSharesRequestAndOnlyCurrentJoinReceivesNotice() {
        var future = new CompletableFuture<String>();
        AtomicInteger calls = new AtomicInteger();
        UpdateNotifier notices = new UpdateNotifier(() -> { calls.incrementAndGet(); return future; }, () -> 0);
        notices.join(Runnable::run);
        notices.onDisconnect();
        notices.join(Runnable::run);
        future.complete("1.0.21");
        assertEquals(1, calls.get());
        assertEquals("1.0.21", notices.poll(true));
        assertNull(notices.poll(true));
    }

    @Test void successCachesForOneHour() {
        AtomicLong clock = new AtomicLong(-1_000_000L);
        AtomicInteger calls = new AtomicInteger();
        UpdateNotifier notices = new UpdateNotifier(() -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(null); }, clock::get);
        notices.join(Runnable::run);
        clock.addAndGet(59L * 60 * 1_000_000_000);
        notices.join(Runnable::run);
        assertEquals(1, calls.get());
        clock.addAndGet(60L * 1_000_000_000);
        notices.join(Runnable::run);
        assertEquals(2, calls.get());
    }

    @Test void failureIsSilentAndRetriesAfterFiveMinutes() {
        AtomicLong clock = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        UpdateNotifier notices = new UpdateNotifier(() -> {
            calls.incrementAndGet();
            CompletableFuture<String> result = new CompletableFuture<>();
            result.completeExceptionally(new IllegalStateException("offline"));
            return result;
        }, clock::get);
        notices.join(Runnable::run);
        assertNull(notices.poll(true));
        notices.join(Runnable::run);
        assertEquals(1, calls.get());
        clock.addAndGet(5L * 60 * 1_000_000_000);
        notices.join(Runnable::run);
        assertEquals(2, calls.get());
    }
}
