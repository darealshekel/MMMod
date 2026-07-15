package com.mmm.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmm.MMM;
import com.mmm.config.Configs;
import com.mmm.sync.WebsiteLinkManager;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MilestoneSocialRelay
{
    private static final String BASE_ENDPOINT = System.getProperty("mmm.socialEndpoint", "https://www.mmmaniacs.com/api/mod-social");
    private static final long RECONNECT_DELAY_MS = 5_000L;
    private static final long AUTH_RETRY_DELAY_MS = 60_000L;
    private static final int MAX_SEEN_EVENTS = 128;
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "MMM milestone relay " + THREAD_IDS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    };
    private static final ExecutorService IO_EXECUTOR = Executors.newCachedThreadPool(THREAD_FACTORY);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10L))
            .executor(IO_EXECUTOR)
            .build();
    private static final Set<String> SEEN_EVENT_IDS = new HashSet<>();
    private static final Deque<String> SEEN_EVENT_ORDER = new ArrayDeque<>();

    private static volatile InputStream activeStream;
    private static volatile String activeRoomId = "";
    private static volatile boolean connecting;
    private static volatile boolean connected;
    private static volatile long generation;
    private static volatile long nextConnectionAttemptMs;
    private static int tickCounter;

    private MilestoneSocialRelay()
    {
    }

    public static void onClientTick(MinecraftClient client)
    {
        if (++tickCounter < 20)
        {
            return;
        }
        tickCounter = 0;

        String roomId = desiredRoomId(client);
        if (roomId.isBlank()
                || Configs.Generic.RECEIVE_GOAL_MILESTONES.getBooleanValue() == false
                || WebsiteLinkManager.isCurrentPlayerLinked() == false)
        {
            disconnect();
            return;
        }

        if (roomId.equals(activeRoomId) && (connecting || connected))
        {
            return;
        }
        if (System.currentTimeMillis() < nextConnectionAttemptMs)
        {
            return;
        }

        connect(client, roomId);
    }

    public static void publishMilestone(int threshold, MiningStats.GoalProgress progress)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null
                || client.player == null
                || progress == null
                || Configs.Generic.SHARE_GOAL_MILESTONES.getBooleanValue() == false
                || WebsiteLinkManager.isCurrentPlayerLinked() == false
                || (threshold != 25 && threshold != 50 && threshold != 75 && threshold != 100))
        {
            return;
        }

        String roomId = desiredRoomId(client);
        if (roomId.isBlank())
        {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("roomId", roomId);
        payload.addProperty("minecraftUuid", client.player.getUuidAsString());
        payload.addProperty("clientId", Configs.cloudClientId);
        payload.addProperty("threshold", threshold);
        payload.addProperty("current", Math.max(0L, progress.current()));
        payload.addProperty("target", Math.max(1L, progress.target()));

        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_ENDPOINT + "/milestone"))
                    .timeout(Duration.ofSeconds(15L))
                    .header("Content-Type", "application/json")
                    .header("x-mmm-client-sync-token", Configs.websiteSyncToken)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400 && Configs.Generic.WEBSITE_SYNC_DEBUG.getBooleanValue())
                        {
                            MMM.LOGGER.warn("[MMM] Milestone relay publish returned HTTP {}", response.statusCode());
                        }
                    })
                    .exceptionally(error -> {
                        if (Configs.Generic.WEBSITE_SYNC_DEBUG.getBooleanValue())
                        {
                            MMM.LOGGER.warn("[MMM] Milestone relay publish failed: {}", error.getMessage());
                        }
                        return null;
                    });
        }
        catch (Exception exception)
        {
            if (Configs.Generic.WEBSITE_SYNC_DEBUG.getBooleanValue())
            {
                MMM.LOGGER.warn("[MMM] Could not prepare milestone relay request: {}", exception.getMessage());
            }
        }
    }

    public static synchronized void disconnect()
    {
        if (activeRoomId.isBlank() && connecting == false && connected == false)
        {
            return;
        }
        generation += 1L;
        activeRoomId = "";
        connecting = false;
        connected = false;
        InputStream stream = activeStream;
        activeStream = null;
        if (stream != null)
        {
            try
            {
                stream.close();
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private static synchronized void connect(MinecraftClient client, String roomId)
    {
        disconnect();
        if (client.player == null)
        {
            return;
        }

        activeRoomId = roomId;
        connecting = true;
        long connectionGeneration = generation;
        String uuid = client.player.getUuidAsString();
        String clientId = Configs.cloudClientId;
        String token = Configs.websiteSyncToken;

        try
        {
            String endpoint = BASE_ENDPOINT + "/events?room=" + encode(roomId)
                    + "&minecraftUuid=" + encode(uuid)
                    + "&clientId=" + encode(clientId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("x-mmm-client-sync-token", token)
                    .GET()
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .whenComplete((response, error) -> handleConnectionResponse(connectionGeneration, response, error));
        }
        catch (Exception exception)
        {
            connectionEnded(connectionGeneration, AUTH_RETRY_DELAY_MS);
        }
    }

    private static void handleConnectionResponse(long connectionGeneration, HttpResponse<InputStream> response, Throwable error)
    {
        if (error != null || response == null)
        {
            connectionEnded(connectionGeneration, RECONNECT_DELAY_MS);
            return;
        }
        if (response.statusCode() != 200)
        {
            try
            {
                response.body().close();
            }
            catch (Exception ignored)
            {
            }
            connectionEnded(connectionGeneration, response.statusCode() == 401 ? AUTH_RETRY_DELAY_MS : RECONNECT_DELAY_MS);
            return;
        }

        synchronized (MilestoneSocialRelay.class)
        {
            if (connectionGeneration != generation)
            {
                try
                {
                    response.body().close();
                }
                catch (Exception ignored)
                {
                }
                return;
            }
            connecting = false;
            connected = true;
            activeStream = response.body();
        }
        IO_EXECUTOR.execute(() -> consumeEventStream(connectionGeneration, response.body()));
    }

    private static void consumeEventStream(long connectionGeneration, InputStream stream)
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String eventName = "";
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && connectionGeneration == generation)
            {
                if (line.isEmpty())
                {
                    if ("milestone".equals(eventName) && data.isEmpty() == false)
                    {
                        handleMilestoneEvent(data.toString());
                    }
                    eventName = "";
                    data.setLength(0);
                }
                else if (line.startsWith("event:"))
                {
                    eventName = line.substring("event:".length()).trim();
                }
                else if (line.startsWith("data:"))
                {
                    if (data.isEmpty() == false)
                    {
                        data.append('\n');
                    }
                    data.append(line.substring("data:".length()).trim());
                }
            }
        }
        catch (Exception ignored)
        {
        }
        finally
        {
            connectionEnded(connectionGeneration, RECONNECT_DELAY_MS);
        }
    }

    private static void handleMilestoneEvent(String rawJson)
    {
        try
        {
            JsonObject event = JsonParser.parseString(rawJson).getAsJsonObject();
            String eventId = stringValue(event, "eventId");
            String username = stringValue(event, "username");
            int threshold = event.get("threshold").getAsInt();
            long current = event.get("current").getAsLong();
            long target = event.get("target").getAsLong();
            if (eventId.isBlank()
                    || username.matches("[A-Za-z0-9_]{1,16}") == false
                    || (threshold != 25 && threshold != 50 && threshold != 75 && threshold != 100)
                    || current < 0L
                    || target <= 0L
                    || markSeen(eventId) == false)
            {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.player == null || Configs.Generic.RECEIVE_GOAL_MILESTONES.getBooleanValue() == false)
                {
                    return;
                }
                MiningStats.GoalProgress progress = new MiningStats.GoalProgress("Daily Goal", true, current, target);
                int color = UiFormat.getGoalProgressColor(progress) & 0x00FFFFFF;
                String milestoneMessage = switch (threshold)
                {
                    case 25 -> " is off to a good start";
                    case 50 -> " is halfway through today's goal";
                    case 75 -> " is in the final stretch";
                    default -> " finished today's goal";
                };
                MutableText message = Text.literal("[MMM] ").formatted(Formatting.DARK_GRAY)
                        .append(Text.literal(username).formatted(Formatting.WHITE))
                        .append(Text.literal(milestoneMessage + " (").formatted(Formatting.GRAY))
                        .append(Text.literal(threshold + "% - ").styled(style -> style.withColor(color)))
                        .append(Text.literal(String.format(Locale.US, "%,d / %,d", current, target)).styled(style -> style.withColor(color)))
                        .append(Text.literal(" blocks).").formatted(Formatting.GRAY));
                client.player.sendMessage(message, false);
            });
        }
        catch (Exception ignored)
        {
        }
    }

    private static synchronized boolean markSeen(String eventId)
    {
        if (SEEN_EVENT_IDS.add(eventId) == false)
        {
            return false;
        }
        SEEN_EVENT_ORDER.addLast(eventId);
        while (SEEN_EVENT_ORDER.size() > MAX_SEEN_EVENTS)
        {
            SEEN_EVENT_IDS.remove(SEEN_EVENT_ORDER.removeFirst());
        }
        return true;
    }

    private static synchronized void connectionEnded(long connectionGeneration, long retryDelayMs)
    {
        if (connectionGeneration != generation)
        {
            return;
        }
        connecting = false;
        connected = false;
        activeStream = null;
        nextConnectionAttemptMs = System.currentTimeMillis() + retryDelayMs;
    }

    private static String desiredRoomId(MinecraftClient client)
    {
        if (client == null || client.player == null || client.world == null || client.isInSingleplayer())
        {
            return "";
        }
        ServerInfo server = client.getCurrentServerEntry();
        return server == null ? "" : ServerRoomHasher.hash(server.address);
    }

    private static String stringValue(JsonObject object, String key)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString().trim() : "";
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
