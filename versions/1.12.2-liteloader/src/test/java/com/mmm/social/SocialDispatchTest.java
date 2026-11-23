package com.mmm.social;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SocialDispatchTest {
    @Test void networkDeliveryDoesNotWaitForMinecraftTaskQueueWhileHoldingSessionLock() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Minecraft client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        var gameTasks = new ArrayDeque<>();
        field("tasks").set(client, gameTasks);
        field("thread").set(client, Thread.currentThread());
        Field singleton = field("INSTANCE");
        Object previousClient = singleton.get(null);
        var constructor = Class.forName("com.mmm.social.PublicChatClient$ClientSink").getDeclaredConstructor();
        constructor.setAccessible(true);
        var clientSink = (SocialSession.Sink) constructor.newInstance();
        var transport = new SocialSessionTest.FakeTransport();
        var sink = new SocialSessionTest.FakeSink() {
            @Override public void execute(Runnable action) { clientSink.execute(action); }
            @Override public void chat(String name, String message, String raw) {
                if (message.equals("fail")) throw new IllegalStateException("synthetic display failure");
                super.chat(name, message, raw);
            }
        };
        sink.identity = new SocialSession.Identity("uuid", "client", "test-token", new Object());
        var session = new SocialSession(transport, sink, () -> 1_000);
        var finished = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        Thread callback = new Thread(() -> {
            try {
                transport.event("chat", "{\"eventId\":\"test\",\"username\":\"Miner\",\"message\":\"hello\"}");
            } catch (Throwable error) { failure.set(error); }
            finally { finished.countDown(); }
        }, "MMM test social callback");
        callback.setDaemon(true);
        try {
            singleton.set(null, client);
            session.identify(sink.identity);
            session.tick(false);
            // Vanilla 1.12.2 executes scheduled tasks under this monitor. A task can
            // acquire SocialSession's monitor while a network callback holds it.
            synchronized (gameTasks) {
                callback.start();
                assertTrue(finished.await(2, TimeUnit.SECONDS),
                        "MMM network callback waits for Minecraft's task queue while holding the session lock");
            }
            assertTrue(sink.messages.isEmpty(), "Network threads must not update the UI directly");
            PublicChatClient.drainClientTasks();
            assertEquals(1, sink.messages.size());
            Thread burst = new Thread(() -> {
                for (int i = 0; i < 1_000; i++)
                    transport.event("chat", "{\"eventId\":\"burst-" + i
                            + "\",\"username\":\"Miner\",\"message\":\"hello\"}");
            });
            burst.start();
            burst.join(3_000);
            assertFalse(burst.isAlive());
            PublicChatClient.drainClientTasks();
            assertEquals(65, sink.messages.size(), "At most 64 queued actions may run per tick");
            for (int i = 0; i < 5; i++) PublicChatClient.drainClientTasks();
            assertEquals(257, sink.messages.size(), "A stalled client retains at most 256 social actions");
            Thread invalid = new Thread(() -> {
                transport.event("chat", "{\"eventId\":\"bad-display\",\"username\":\"Miner\",\"message\":\"fail\"}");
                transport.event("chat", "{\"eventId\":\"after-bad-display\",\"username\":\"Miner\",\"message\":\"hello\"}");
            });
            invalid.start();
            invalid.join(3_000);
            assertFalse(invalid.isAlive());
            PublicChatClient.drainClientTasks();
            assertEquals(258, sink.messages.size(), "A display exception must not crash the game or block later events");
            Thread stale = new Thread(() -> transport.event("chat",
                    "{\"eventId\":\"stale\",\"username\":\"Miner\",\"message\":\"hello\"}"));
            stale.start();
            stale.join(3_000);
            assertFalse(stale.isAlive());
        } finally {
            callback.join(3_000);
            session.disconnect();
            singleton.set(null, previousClient);
        }
        assertNull(failure.get());
        assertFalse(callback.isAlive());
        PublicChatClient.drainClientTasks();
        assertEquals(258, sink.messages.size(), "Disconnected sessions must discard queued messages");
        PublicChatClient.disconnect();
    }

    private static Field field(String name) throws Exception {
        Field field = Minecraft.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
