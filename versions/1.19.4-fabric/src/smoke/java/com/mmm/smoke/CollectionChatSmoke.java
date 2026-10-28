package com.mmm.smoke;

import com.google.gson.JsonParser;
import com.mmm.config.Configs;
import com.mmm.social.PublicChatClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;

final class CollectionChatSmoke {
    private static HttpServer server;
    private static volatile String received;
    static void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat", exchange -> {
                received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] response = "{\"ok\":true,\"event\":{\"eventId\":\"collection-smoke\",\"username\":\"SmokeMiner\",\"message\":\"completed Stone Collection 10K\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                try (var body = exchange.getResponseBody()) { body.write(response); }
            });
            System.setProperty("mmm.socialEndpoint", "http://127.0.0.1:" + server.getAddress().getPort());
            server.start();
        } catch (Exception error) { throw new RuntimeException(error); }
    }
    static void verify(MinecraftClient client) throws Exception {
        String uuid = Configs.websiteLinkedMinecraftUuid, token = Configs.websiteSyncToken;
        try {
            var endpoint = PublicChatClient.class.getDeclaredField("BASE_ENDPOINT"); endpoint.setAccessible(true);
            if (!endpoint.get(null).toString().startsWith("http://127.0.0.1:")) throw new AssertionError("Chat smoke must use localhost");
            Configs.websiteLinkedMinecraftUuid = client.player.getUuidAsString();
            Configs.websiteSyncToken = "isolated-smoke-token";
            String message = "completed the Stone Collection 10K milestone!";
            if (!PublicChatClient.sendCollectionMessage(message).get(5, TimeUnit.SECONDS)) throw new AssertionError("Collection notification was not accepted");
            var seen = PublicChatClient.class.getDeclaredField("SEEN_EVENT_IDS"); seen.setAccessible(true);
            if (!((java.util.Set<?>)seen.get(null)).contains("collection-smoke")) throw new AssertionError("Collection chat event was not handled");
            var body = JsonParser.parseString(received).getAsJsonObject();
            if (!body.get("message").getAsString().equals(message)
                    || !body.get("minecraftUuid").getAsString().equals(client.player.getUuidAsString()))
                throw new AssertionError("Collection notification payload incorrect");
        } finally {
            Configs.websiteLinkedMinecraftUuid = uuid; Configs.websiteSyncToken = token;
            server.stop(0);
        }
    }
}
