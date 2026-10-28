package com.mmm.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmm.tracker.GoalMilestonePolicy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/** Session state is synchronized; every delivered event is rechecked on the client thread. */
final class SocialSession {
    record Identity(String uuid, String clientId, String token, Object connection) {}

    interface Transport {
        CompletableFuture<SocialHttp.Response> post(String path, String token, JsonObject payload);
        AutoCloseable events(String query, String token, SocialHttp.Events listener);
    }

    interface Sink {
        void execute(Runnable action);
        boolean isCurrent(Identity identity);
        void chat(String username, String message, String rawMessage);
        void milestone(String username, int threshold, long current, long target);
        void error(String message);
        void clearPresence();
        void state(JsonObject event);
        void presence(JsonObject event);
        void friends(JsonObject event);
    }

    private final Transport transport;
    private final Sink sink;
    private final LongSupplier clock;
    private final Set<String> seen = new LinkedHashSet<>();
    private final Deque<String> advancements = new ArrayDeque<>();
    private Identity identity;
    private String seenUuid, seenClientId;
    private long generation, connectionId;
    private AutoCloseable stream;
    private boolean connecting, connected, sending, sendingPresence;
    private Boolean lastPresence;
    private long lastPresenceAt, nextConnectionAt, nextAdvancementAt;

    SocialSession(Transport transport, Sink sink, LongSupplier clock) {
        this.transport = transport;
        this.sink = sink;
        this.clock = clock;
    }

    synchronized void identify(Identity next) {
        if (Objects.equals(identity, next)) return;
        disconnect();
        if (next != null && (!Objects.equals(seenUuid, next.uuid()) || !Objects.equals(seenClientId, next.clientId()))) {
            seen.clear();
            seenUuid = next.uuid();
            seenClientId = next.clientId();
        }
        identity = next;
    }

    synchronized void disconnect() {
        generation++;
        connectionId++;
        identity = null;
        connected = connecting = sending = sendingPresence = false;
        nextConnectionAt = nextAdvancementAt = lastPresenceAt = 0;
        lastPresence = null;
        // Keep delivered IDs across dimension transitions/reconnects for this account.
        advancements.clear();
        AutoCloseable previous = stream;
        stream = null;
        if (previous != null) {
            try { previous.close(); } catch (Exception ignored) { }
        }
        sink.clearPresence();
    }

    synchronized void tick(boolean active) {
        if (identity == null) return;
        long now = clock.getAsLong();
        if (!sending && !advancements.isEmpty() && now >= nextAdvancementAt) {
            String message = advancements.peekFirst();
            long version = generation;
            nextAdvancementAt = now + 2_500;
            send(message, false).thenAccept(ok -> {
                synchronized (SocialSession.this) {
                    if (version == generation && ok) advancements.removeFirstOccurrence(message);
                }
            });
        }
        if (connected) sendPresence(active, now);
        else if (!connecting && now >= nextConnectionAt) connect();
    }

    private void connect() {
        connecting = true;
        long version = generation;
        long connection = ++connectionId;
        String query = "?room=" + encode(PublicChatClient.PUBLIC_ROOM_ID)
                + "&minecraftUuid=" + encode(identity.uuid()) + "&clientId=" + encode(identity.clientId());
        try {
            AutoCloseable opened = transport.events(query, identity.token(), new SocialHttp.Events() {
                @Override public void opened() {
                    synchronized (SocialSession.this) {
                        if (version != generation || connection != connectionId) return;
                        connecting = false;
                        connected = true;
                        lastPresence = null;
                        lastPresenceAt = 0;
                    }
                }
                @Override public void event(String name, String data) {
                    synchronized (SocialSession.this) {
                        if (version != generation || connection != connectionId) return;
                        receive(version, name, data);
                    }
                }
                @Override public void ended(int status) {
                    synchronized (SocialSession.this) {
                        if (version != generation || connection != connectionId) return;
                        connected = connecting = false;
                        stream = null;
                        nextConnectionAt = clock.getAsLong() + (status == 401 || status == 403 ? 60_000 : 5_000);
                    }
                }
            });
            if (version == generation && connection == connectionId && (connecting || connected)) stream = opened;
            else opened.close();
        } catch (Exception error) {
            connected = connecting = false;
            nextConnectionAt = clock.getAsLong() + 5_000;
        }
    }

    synchronized CompletableFuture<Boolean> send(String raw, boolean reportErrors) {
        String message = normalizeMessage(raw);
        if (identity == null) {
            if (reportErrors) sink.error("Website link required.");
            return CompletableFuture.completedFuture(false);
        }
        if (message.isEmpty()) return CompletableFuture.completedFuture(false);
        if (message.length() > PublicChatClient.MAX_MESSAGE_LENGTH || sending) {
            if (reportErrors) deliver(generation, () -> sink.error(message.length() > PublicChatClient.MAX_MESSAGE_LENGTH
                    ? "MMM Chat messages may contain at most 200 characters." : "Please wait for your previous message to finish sending."));
            return CompletableFuture.completedFuture(false);
        }
        long version = generation;
        JsonObject payload = payload();
        payload.addProperty("message", message);
        sending = true;
        try {
            return transport.post("/chat", identity.token(), payload).handle((response, error) -> {
                synchronized (SocialSession.this) {
                    if (version != generation) return false;
                    sending = false;
                    if (error != null || response == null || !response.successful()) {
                        if (reportErrors) deliver(version, () -> sink.error(response == null
                                ? "Could not reach MMM Chat." : responseError(response)));
                        return false;
                    }
                    try {
                        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (!body.has("ok") || !body.get("ok").getAsBoolean() || !body.has("event")
                                || !validChat(body.getAsJsonObject("event"))) throw new IllegalArgumentException();
                        receive(version, "chat", body.get("event").toString());
                        return true;
                    } catch (RuntimeException invalid) {
                        if (reportErrors) deliver(version, () -> sink.error("MMM Chat returned an invalid response."));
                        return false;
                    }
                }
            });
        } catch (RuntimeException error) {
            sending = false;
            if (reportErrors) deliver(version, () -> sink.error("Could not send that message."));
            return CompletableFuture.completedFuture(false);
        }
    }

    synchronized void queueAdvancement(String message) {
        if (identity != null && advancements.size() < 32) advancements.addLast(message);
    }

    synchronized void publishMilestone(int threshold, long current, long target) {
        if (identity == null || !GoalMilestonePolicy.isMilestoneThreshold(threshold)) return;
        JsonObject payload = payload();
        payload.addProperty("roomId", PublicChatClient.PUBLIC_ROOM_ID);
        payload.addProperty("threshold", threshold);
        payload.addProperty("current", Math.max(0, current));
        payload.addProperty("target", Math.max(1, target));
        try { transport.post("/milestone", identity.token(), payload).exceptionally(error -> null); }
        catch (RuntimeException ignored) { }
    }

    private void sendPresence(boolean active, long now) {
        if (sendingPresence || (Boolean.valueOf(active).equals(lastPresence) && now - lastPresenceAt < 20_000)) return;
        long version = generation;
        JsonObject payload = payload();
        payload.addProperty("active", active);
        sendingPresence = true;
        try {
            transport.post("/presence", identity.token(), payload).whenComplete((response, error) -> {
                synchronized (SocialSession.this) {
                    if (version != generation) return;
                    sendingPresence = false;
                    if (error != null || response == null || !response.successful()) return;
                    lastPresence = active;
                    lastPresenceAt = clock.getAsLong();
                    try {
                        JsonObject state = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (state.has("friends") && state.get("friends").isJsonArray())
                            deliver(version, () -> sink.friends(state));
                    } catch (RuntimeException ignored) { }
                }
            });
        } catch (RuntimeException ignored) { sendingPresence = false; }
    }

    private JsonObject payload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("minecraftUuid", identity.uuid());
        payload.addProperty("clientId", identity.clientId());
        return payload;
    }

    private void receive(long version, String type, String data) {
        try {
            JsonObject event = JsonParser.parseString(data).getAsJsonObject();
            switch (type) {
                case "chat":
                    if (!validChat(event)) return;
                    deliverEvent(version, string(event, "eventId"), () -> sink.chat(string(event, "username"),
                            normalizeMessage(string(event, "message")), normalizeMessage(string(event, "rawMessage"))));
                    break;
                case "milestone":
                    String id = string(event, "eventId");
                    String username = string(event, "username");
                    int threshold = event.get("threshold").getAsInt();
                    long current = event.get("current").getAsLong();
                    long target = event.get("target").getAsLong();
                    if (id.isEmpty() || id.length() > 256 || !MmmChatIgnoreList.isValidUsername(username)
                            || !GoalMilestonePolicy.isMilestoneThreshold(threshold) || current < 0 || target <= 0) return;
                    deliverEvent(version, id, () -> sink.milestone(username, threshold, current, target));
                    break;
                case "social-state": deliver(version, () -> sink.state(event)); break;
                case "presence": deliver(version, () -> sink.presence(event)); break;
                default: break;
            }
        } catch (RuntimeException ignored) {
            // One malformed event must not disconnect an otherwise healthy stream.
        }
    }

    private boolean markSeen(String id) {
        if (!seen.add(id)) return false;
        if (seen.size() > 256) seen.remove(seen.iterator().next());
        return true;
    }

    private void deliverEvent(long version, String id, Runnable action) {
        deliver(version, () -> { if (markSeen(id)) action.run(); });
    }

    private void deliver(long version, Runnable action) {
        Identity expected = identity;
        sink.execute(() -> {
            synchronized (SocialSession.this) {
                if (version == generation && expected != null && sink.isCurrent(expected)) action.run();
            }
        });
    }

    static boolean validChat(JsonObject event) {
        String id = string(event, "eventId");
        String message = normalizeMessage(string(event, "message"));
        return !id.isEmpty() && id.length() <= 256 && MmmChatIgnoreList.isValidUsername(string(event, "username"))
                && !message.isEmpty() && message.length() <= PublicChatClient.MAX_MESSAGE_LENGTH
                && normalizeMessage(string(event, "rawMessage")).length() <= PublicChatClient.MAX_MESSAGE_LENGTH;
    }

    static String normalizeMessage(String value) {
        return value == null ? "" : value.replaceAll("\\u00a7.", "").replace("\u00a7", "")
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").replaceAll("\\s+", " ").trim();
    }

    private static String responseError(SocialHttp.Response response) {
        try {
            String error = normalizeMessage(string(JsonParser.parseString(response.body()).getAsJsonObject(), "error"));
            if (!error.isEmpty()) return error.substring(0, Math.min(error.length(), 200));
        } catch (RuntimeException ignored) { }
        return response.status() == 429 ? "You are sending messages too quickly." : "Could not send that message.";
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString().trim() : "";
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
