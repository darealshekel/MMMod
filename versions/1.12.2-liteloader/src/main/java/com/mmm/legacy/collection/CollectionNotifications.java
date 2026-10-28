package com.mmm.legacy.collection;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.text.LiteralText;

/** Social integration owns transport; a missing/failed transport leaves the durable outbox intact. */
public final class CollectionNotifications {
    public static final int MAX_MESSAGE_LENGTH = 200;
    private static Function<String, CompletableFuture<Boolean>> sender;
    private CollectionNotifications() {}

    public static void setSender(Function<String, CompletableFuture<Boolean>> transport) {
        sender = Objects.requireNonNull(transport);
    }
    public static CompletableFuture<Boolean> send(String message) {
        try {
            return sender == null ? CompletableFuture.completedFuture(false) : Objects.requireNonNull(sender.apply(message));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(false);
        }
    }
    public static void local(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.addMessage(new LiteralText("\u00a7a[MMM] " + message), false);
    }
}
