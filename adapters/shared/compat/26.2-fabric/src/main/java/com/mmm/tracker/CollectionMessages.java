package com.mmm.tracker;

import java.util.concurrent.CompletableFuture;

final class CollectionMessages {
    private CollectionMessages() {}
    static boolean enabled() { return com.mmm.config.Configs.Generic.SHARE_COLLECTION_MILESTONES.getBooleanValue(); }
    static CompletableFuture<Boolean> send(String message) {
        return com.mmm.social.PublicChatClient.sendCollectionMessage(message);
    }
}
