package com.mmm.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmm.MMM;
import com.mmm.config.Configs;
import com.mmm.sync.WebsiteLinkManager;
import com.mmm.tracker.GoalMilestonePolicy;
import com.mmm.tracker.MiningStats;
import com.mmm.ui.MmmUi;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class PublicChatClient
{
    public static final String PUBLIC_ROOM_ID = "5e2f7e9aa53b975bfb92affe138a51df82306936f2fb71acda97e51aeaf02e48";
    public static final int MAX_MESSAGE_LENGTH = 200;

    private static final String BASE_ENDPOINT = System.getProperty("mmm.socialEndpoint", "https://www.mmmaniacs.com/api/mod-social");
    private static final long RECONNECT_DELAY_MS = 5_000L;
    private static final long AUTH_RETRY_DELAY_MS = 60_000L;
    private static final int MAX_SEEN_EVENTS = 256;
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "MMM social " + THREAD_IDS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    };
    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(3, THREAD_FACTORY);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10L))
            .executor(IO_EXECUTOR)
            .build();
    private static final Set<String> SEEN_EVENT_IDS = new HashSet<>();
    private static final Deque<String> SEEN_EVENT_ORDER = new ArrayDeque<>();

    private static volatile InputStream activeStream;
    private static volatile boolean connecting;
    private static volatile boolean connected;
    private static volatile boolean sending;
    private static volatile long generation;
    private static volatile long nextConnectionAttemptMs;
    private static int tickCounter;

    private PublicChatClient()
    {
    }

    public static void onClientTick(Minecraft client)
    {
        if (++tickCounter < 20)
        {
            return;
        }
        tickCounter = 0;

        if (client == null
                || client.player == null
                || WebsiteLinkManager.isCurrentPlayerLinked() == false
                || (Configs.Generic.SHOW_MMM_CHAT_MESSAGES.getBooleanValue() == false
                    && Configs.Generic.RECEIVE_GOAL_MILESTONES.getBooleanValue() == false))
        {
            disconnect();
            return;
        }
        if (connected || connecting || System.currentTimeMillis() < nextConnectionAttemptMs)
        {
            return;
        }
        connect(client);
    }

    public static void sendMessage(String rawMessage)
    {
        Minecraft client = Minecraft.getInstance();
        String message = normalizeMessage(rawMessage);
        if (client == null
                || client.player == null
                || WebsiteLinkManager.isCurrentPlayerLinked() == false)
        {
            showLocalError("Website link required.");
            return;
        }
        if (message.isBlank() || message.length() > MAX_MESSAGE_LENGTH || sending)
        {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("minecraftUuid", client.player.getStringUUID());
        payload.addProperty("clientId", Configs.cloudClientId);
        payload.addProperty("message", message);
        sending = true;

        try
        {
            HTTP_CLIENT.sendAsync(jsonRequest("/chat", payload), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((response, error) -> {
                        sending = false;
                        if (error != null || response == null)
                        {
                            showLocalError("Could not reach MMM Chat.");
                            return;
                        }
                        if (response.statusCode() >= 400)
                        {
                            showLocalError(responseError(response.body(), response.statusCode()));
                            return;
                        }
                        try
                        {
                            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (body.has("ok") == false
                                    || body.get("ok").isJsonPrimitive() == false
                                    || body.get("ok").getAsBoolean() == false
                                    || body.has("event") == false
                                    || body.get("event").isJsonObject() == false)
                            {
                                showLocalError("MMM Chat returned an invalid response.");
                                return;
                            }
                            handleChatEvent(body.getAsJsonObject("event"));
                        }
                        catch (Exception exception)
                        {
                            showLocalError("MMM Chat is not available yet.");
                            if (Configs.Generic.WEBSITE_SYNC_DEBUG.getBooleanValue())
                            {
                                MMM.LOGGER.warn("[MMM] Chat endpoint returned an invalid response: {}", exception.getMessage());
                            }
                        }
                    });
        }
        catch (Exception exception)
        {
            sending = false;
            showLocalError("Could not send that message.");
        }
    }

    public static void publishMilestone(int threshold, MiningStats.GoalProgress progress)
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null
                || client.player == null
                || progress == null
                || Configs.Generic.SHARE_GOAL_MILESTONES.getBooleanValue() == false
                || WebsiteLinkManager.isCurrentPlayerLinked() == false
                || GoalMilestonePolicy.isMilestoneThreshold(threshold) == false)
        {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("roomId", PUBLIC_ROOM_ID);
        payload.addProperty("minecraftUuid", client.player.getStringUUID());
        payload.addProperty("clientId", Configs.cloudClientId);
        payload.addProperty("threshold", threshold);
        payload.addProperty("current", Math.max(0L, progress.current()));
        payload.addProperty("target", Math.max(1L, progress.target()));

        try
        {
            HTTP_CLIENT.sendAsync(jsonRequest("/milestone", payload), HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400 && Configs.Generic.WEBSITE_SYNC_DEBUG.getBooleanValue())
                        {
                            MMM.LOGGER.warn("[MMM] Milestone publish returned HTTP {}", response.statusCode());
                        }
                    })
                    .exceptionally(error -> {
                        if (Configs.Generic.WEBSITE_SYNC_DEBUG.getBooleanValue())
                        {
                            MMM.LOGGER.warn("[MMM] Milestone publish failed: {}", error.getMessage());
                        }
                        return null;
                    });
        }
        catch (Exception exception)
        {
            if (Configs.Generic.WEBSITE_SYNC_DEBUG.getBooleanValue())
            {
                MMM.LOGGER.warn("[MMM] Could not prepare milestone request: {}", exception.getMessage());
            }
        }
    }

    public static synchronized void disconnect()
    {
        if (connecting == false && connected == false && activeStream == null)
        {
            return;
        }
        generation += 1L;
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

    private static synchronized void connect(Minecraft client)
    {
        disconnect();
        if (client.player == null)
        {
            return;
        }

        connecting = true;
        long connectionGeneration = generation;
        String endpoint = BASE_ENDPOINT + "/events?room=" + encode(PUBLIC_ROOM_ID)
                + "&minecraftUuid=" + encode(client.player.getStringUUID())
                + "&clientId=" + encode(Configs.cloudClientId);
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("x-mmm-client-sync-token", Configs.websiteSyncToken)
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

        synchronized (PublicChatClient.class)
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
                    if (data.isEmpty() == false)
                    {
                        JsonObject event = JsonParser.parseString(data.toString()).getAsJsonObject();
                        if ("chat".equals(eventName))
                        {
                            handleChatEvent(event);
                        }
                        else if ("milestone".equals(eventName))
                        {
                            handleMilestoneEvent(event);
                        }
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

    private static void handleChatEvent(JsonObject event)
    {
        String eventId = stringValue(event, "eventId");
        String username = stringValue(event, "username");
        String message = normalizeMessage(stringValue(event, "message"));
        if (eventId.isBlank()
                || username.matches("[A-Za-z0-9_]{1,16}") == false
                || message.isBlank()
                || message.length() > MAX_MESSAGE_LENGTH
                || markSeen(eventId) == false)
        {
            return;
        }
        showChatMessage(username, message);
    }

    private static void handleMilestoneEvent(JsonObject event)
    {
        try
        {
            String eventId = stringValue(event, "eventId");
            String username = stringValue(event, "username");
            int threshold = event.get("threshold").getAsInt();
            long current = event.get("current").getAsLong();
            long target = event.get("target").getAsLong();
            if (eventId.isBlank()
                    || username.matches("[A-Za-z0-9_]{1,16}") == false
                    || GoalMilestonePolicy.isMilestoneThreshold(threshold) == false
                    || current < 0L
                    || target <= 0L
                    || markSeen(eventId) == false)
            {
                return;
            }
            showMilestone(username, threshold, current, target);
        }
        catch (Exception ignored)
        {
        }
    }

    private static void showChatMessage(String username, String messageText)
    {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player == null || Configs.Generic.SHOW_MMM_CHAT_MESSAGES.getBooleanValue() == false)
            {
                return;
            }
            MutableComponent message = Component.literal("[MMM] ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(username).withStyle(style -> style.withColor(MmmUi.accent() & 0x00FFFFFF)))
                    .append(Component.literal(": " + messageText).withStyle(ChatFormatting.WHITE));
            client.player.sendSystemMessage(message);
        });
    }

    private static void showMilestone(String username, int threshold, long current, long target)
    {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player == null
                    || Configs.Generic.RECEIVE_GOAL_MILESTONES.getBooleanValue() == false
                    || username.equalsIgnoreCase(client.player.getName().getString()))
            {
                return;
            }

            MiningStats.GoalProgress progress = new MiningStats.GoalProgress("Daily Goal", true, current, target);
            int color = UiFormat.getGoalProgressColor(progress) & 0x00FFFFFF;
            String milestoneMessage = switch (threshold)
            {
                case 25 -> " reached the first daily milestone";
                case 50 -> " is halfway through today's goal";
                case 75 -> " reached the final stretch";
                case 100 -> " completed today's goal";
                default -> " pushed today's goal further";
            };
            MutableComponent message = Component.literal("[MMM] ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(username).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(milestoneMessage + " (").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(threshold + "% - ").withStyle(style -> style.withColor(color)))
                    .append(Component.literal(String.format(Locale.US, "%,d / %,d", current, target))
                            .withStyle(style -> style.withColor(color)))
                    .append(Component.literal(" blocks).").withStyle(ChatFormatting.GRAY));
            client.player.sendSystemMessage(message);
        });
    }

    private static void showLocalError(String detail)
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null)
        {
            return;
        }
        client.execute(() -> {
            if (client.player != null)
            {
                client.player.sendSystemMessage(Component.literal("[MMM] ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(detail).withStyle(ChatFormatting.RED)));
            }
        });
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

    private static HttpRequest jsonRequest(String path, JsonObject payload)
    {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_ENDPOINT + path))
                .timeout(Duration.ofSeconds(15L))
                .header("Content-Type", "application/json")
                .header("x-mmm-client-sync-token", Configs.websiteSyncToken)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
    }

    private static String normalizeMessage(String value)
    {
        return value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").replaceAll("\\s+", " ").trim();
    }

    private static String responseError(String rawBody, int statusCode)
    {
        try
        {
            JsonObject body = JsonParser.parseString(rawBody).getAsJsonObject();
            String error = stringValue(body, "error");
            if (error.isBlank() == false)
            {
                return error;
            }
        }
        catch (Exception ignored)
        {
        }
        return statusCode == 429 ? "You are sending messages too quickly." : "Could not send that message.";
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
