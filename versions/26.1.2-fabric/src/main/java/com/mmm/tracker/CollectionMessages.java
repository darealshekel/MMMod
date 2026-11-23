package com.mmm.tracker;

import java.util.concurrent.CompletableFuture;

final class CollectionMessages {
    private CollectionMessages() {}
    static boolean enabled() { return false; }
    static CompletableFuture<Boolean> send(String message) {
        return CompletableFuture.completedFuture(false);
    }
}
