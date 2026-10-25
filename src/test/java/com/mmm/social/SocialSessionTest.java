package com.mmm.social;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SocialSessionTest {
    private final AtomicLong now = new AtomicLong(1_000);
    private final FakeTransport transport = new FakeTransport();
    private final FakeSink sink = new FakeSink();
    private final SocialSession session = new SocialSession(transport, sink, now::get);
    private final SocialSession.Identity identity = new SocialSession.Identity("uuid", "client id", "secret", new Object());

    @BeforeEach void identify() { sink.identity = identity; session.identify(identity); }

    @Test void connectsWithRoomIdentityAndAuthenticationButNotTokenInUrl() {
        session.tick(false);
        assertEquals(1, transport.listeners.size());
        assertTrue(transport.query.contains(PublicChatClient.PUBLIC_ROOM_ID));
        assertTrue(transport.query.contains("minecraftUuid=uuid&clientId=client+id"));
        assertFalse(transport.query.contains("secret"));
        assertEquals("secret", transport.token);
        session.tick(false);
        assertEquals(1, transport.listeners.size());
    }

    @Test void postsNormalizedChatAndDeduplicatesHttpAndStreamEchoes() {
        session.tick(false);
        CompletableFuture<Boolean> sent = session.send("  hello\nworld\u0000  ", true);
        assertEquals("hello world", transport.posts.getFirst().body.get("message").getAsString());
        assertEquals("uuid", transport.posts.getFirst().body.get("minecraftUuid").getAsString());
        assertEquals("client id", transport.posts.getFirst().body.get("clientId").getAsString());
        transport.complete(0, 200, "{\"ok\":true,\"event\":" + chat("one") + "}");
        assertTrue(sent.join());
        transport.event("chat", chat("one"));
        assertTrue(sink.messages.isEmpty());
        sink.drain();
        assertEquals(List.of("Miner:hello:raw"), sink.messages);
    }

    @Test void rejectsEmptyLongAndConcurrentMessagesWithoutHttp() {
        assertFalse(session.send("\n\t", false).join());
        assertFalse(session.send("a".repeat(201), false).join());
        session.send("one", false);
        assertFalse(session.send("two", false).join());
        assertEquals(1, transport.posts.size());
    }

    @Test void errorsResolveFalseAndAllowSubsequentSend() {
        var first = session.send("one", true);
        transport.complete(0, 429, "{}");
        assertFalse(first.join());
        sink.drain();
        assertEquals(List.of("You are sending messages too quickly."), sink.errors);
        var second = session.send("two", false);
        transport.posts.get(1).future.completeExceptionally(new IllegalStateException("offline"));
        assertFalse(second.join());
        var third = session.send("three", false);
        transport.complete(2, 200, "{\"ok\":true,\"event\":{}}");
        assertFalse(third.join());
        session.send("four", false);
        assertEquals(4, transport.posts.size());
    }

    @Test void ignoresMalformedEventsWithoutBreakingFollowingEvents() {
        session.tick(false);
        transport.event("chat", "invalid json");
        transport.event("chat", "{\"eventId\":\"bad\",\"username\":\"not a username\",\"message\":\"hi\"}");
        transport.event("milestone", "{}");
        transport.event("chat", chat("good"));
        sink.drain();
        assertEquals(1, sink.messages.size());
    }

    @Test void validatesRemoteMilestonesAndDeduplicates() {
        session.tick(false);
        String milestone = "{\"eventId\":\"goal\",\"username\":\"Miner\",\"threshold\":150,\"current\":1500,\"target\":1000}";
        transport.event("milestone", milestone);
        transport.event("milestone", milestone);
        transport.event("milestone", milestone.replace("goal", "bad").replace("150,", "23,"));
        sink.drain();
        assertEquals(List.of("Miner:150:1500:1000"), sink.milestones);
    }

    @Test void disconnectDropsQueuedUiAndLateResponsesAndClearsAdvancements() {
        session.tick(false);
        transport.event("chat", chat("queued"));
        session.queueAdvancement("queued advancement");
        var pending = session.send("pending", false);
        session.disconnect();
        assertEquals(1, transport.closes);
        transport.complete(0, 200, "{\"ok\":true,\"event\":" + chat("old") + "}");
        assertFalse(pending.join());
        transport.event("chat", chat("late"));
        sink.drain();
        assertTrue(sink.messages.isEmpty());
        session.identify(identity);
        session.tick(false);
        assertEquals(1, transport.posts.size());
    }

    @Test void checksLiveIdentityEvenBeforeNextTickAndReplacesToken() {
        session.tick(false);
        transport.event("chat", chat("queued"));
        sink.identity = new SocialSession.Identity("uuid", "client id", "new token", identity.connection());
        sink.drain();
        assertTrue(sink.messages.isEmpty());
        session.identify(sink.identity);
        session.tick(false);
        assertEquals("new token", transport.token);
        assertEquals(1, transport.closes);
    }

    @Test void backoffIsSixtySecondsForAuthAndFiveForDisconnect() {
        session.tick(false);
        transport.listeners.getFirst().ended(401);
        now.addAndGet(59_999);
        session.tick(false);
        assertEquals(1, transport.listeners.size());
        now.incrementAndGet();
        session.tick(false);
        assertEquals(2, transport.listeners.size());
        transport.listeners.get(1).ended(0);
        now.addAndGet(4_999);
        session.tick(false);
        assertEquals(2, transport.listeners.size());
        now.incrementAndGet();
        session.tick(false);
        assertEquals(3, transport.listeners.size());
    }

    @Test void presenceSendsOnChangeAndHeartbeatAndUpdatesFriends() {
        session.tick(false);
        transport.listeners.getFirst().opened();
        session.tick(false);
        assertEquals("/presence", transport.posts.getFirst().path);
        assertFalse(transport.posts.getFirst().body.get("active").getAsBoolean());
        transport.complete(0, 200, "{\"friends\":[\"Miner\"]}");
        sink.drain();
        assertEquals(1, sink.friends);
        session.tick(false);
        assertEquals(1, transport.posts.size());
        session.tick(true);
        transport.complete(1, 200, "{}");
        now.addAndGet(20_000);
        session.tick(true);
        assertEquals(3, transport.posts.size());
    }

    @Test void oldPresenceCannotRepopulateNewWorld() {
        session.tick(false);
        transport.listeners.getFirst().opened();
        session.tick(true);
        session.disconnect();
        transport.complete(0, 200, "{\"friends\":[\"Miner\"]}");
        sink.drain();
        assertEquals(0, sink.friends);
    }

    @Test void advancementIsRetriedThenRemovedAfterAcknowledgment() {
        session.queueAdvancement("earned an advancement");
        session.tick(false);
        transport.complete(0, 503, "{}");
        now.addAndGet(2_500);
        session.tick(false);
        transport.complete(1, 200, "{\"ok\":true,\"event\":" + chat("advancement") + "}");
        now.addAndGet(2_500);
        session.tick(false);
        assertEquals(2, transport.posts.size());
    }

    @Test void publishesMilestoneProtocolWithRoomAndClampedCounts() {
        session.publishMilestone(25, -1, 0);
        session.publishMilestone(26, 2, 3);
        var post = transport.posts.getFirst();
        assertEquals("/milestone", post.path);
        assertEquals(PublicChatClient.PUBLIC_ROOM_ID, post.body.get("roomId").getAsString());
        assertEquals(0, post.body.get("current").getAsLong());
        assertEquals(1, post.body.get("target").getAsLong());
        assertEquals(1, transport.posts.size());
    }

    @Test void formattingCodesCannotInjectColorsAndWhitespaceIsNormalized() {
        assertEquals("hello world", SocialSession.normalizeMessage("\u00a7chello\nworld"));
        assertEquals("", SocialSession.normalizeMessage(null));
    }

    @Test void deliveredMessagesAndMilestonesDoNotReplayAfterReconnect() {
        session.tick(false);
        String goal = "{\"eventId\":\"goal\",\"username\":\"Miner\",\"threshold\":100,\"current\":1000,\"target\":1000}";
        transport.event("chat", chat("old"));
        transport.event("milestone", goal);
        sink.drain();
        session.identify(null);
        sink.identity = new SocialSession.Identity("uuid", "client id", "secret", new Object());
        session.identify(sink.identity);
        session.tick(false);
        transport.event("chat", chat("old"));
        transport.event("milestone", goal);
        transport.event("chat", chat("new"));
        sink.drain();
        assertEquals(2, sink.messages.size());
        assertEquals(1, sink.milestones.size());
    }

    @Test void queuedButUndeliveredMessagesCanBeReplayedAfterTransition() {
        session.tick(false);
        transport.event("chat", chat("pending"));
        session.disconnect();
        sink.drain();
        session.identify(identity);
        session.tick(false);
        transport.event("chat", chat("pending"));
        sink.drain();
        assertEquals(1, sink.messages.size());
    }

    @Test void differentAccountGetsItsOwnMessageHistory() {
        session.tick(false);
        transport.event("chat", chat("one"));
        sink.drain();
        sink.identity = new SocialSession.Identity("another uuid", "client id", "secret", new Object());
        session.identify(sink.identity);
        session.tick(false);
        transport.event("chat", chat("one"));
        sink.drain();
        assertEquals(2, sink.messages.size());
    }

    private static String chat(String id) {
        return "{\"eventId\":\"" + id + "\",\"username\":\"Miner\",\"message\":\"hello\",\"rawMessage\":\"raw\"}";
    }

    static final class FakeTransport implements SocialSession.Transport {
        record Post(String path, JsonObject body, CompletableFuture<SocialHttp.Response> future) {}
        final List<Post> posts = new ArrayList<>();
        final List<SocialHttp.Events> listeners = new ArrayList<>();
        String query, token;
        int closes;
        @Override public CompletableFuture<SocialHttp.Response> post(String path, String token, JsonObject body) {
            var future = new CompletableFuture<SocialHttp.Response>();
            posts.add(new Post(path, body, future));
            return future;
        }
        @Override public AutoCloseable events(String query, String token, SocialHttp.Events listener) {
            this.query = query;
            this.token = token;
            listeners.add(listener);
            return () -> closes++;
        }
        void complete(int index, int status, String body) { posts.get(index).future.complete(new SocialHttp.Response(status, body)); }
        void event(String type, String data) { listeners.getLast().event(type, data); }
    }

    static final class FakeSink implements SocialSession.Sink {
        SocialSession.Identity identity;
        final List<Runnable> queued = new ArrayList<>();
        final List<String> messages = new ArrayList<>(), milestones = new ArrayList<>(), errors = new ArrayList<>();
        int friends;
        void drain() { List.copyOf(queued).forEach(Runnable::run); queued.clear(); }
        @Override public void execute(Runnable action) { queued.add(action); }
        @Override public boolean isCurrent(SocialSession.Identity value) { return value.equals(identity); }
        @Override public void chat(String name, String message, String raw) { messages.add(name + ":" + message + ":" + raw); }
        @Override public void milestone(String name, int threshold, long current, long target) { milestones.add(name + ":" + threshold + ":" + current + ":" + target); }
        @Override public void error(String message) { errors.add(message); }
        @Override public void clearPresence() {}
        @Override public void state(JsonObject event) {}
        @Override public void presence(JsonObject event) {}
        @Override public void friends(JsonObject event) { friends++; }
    }
}
