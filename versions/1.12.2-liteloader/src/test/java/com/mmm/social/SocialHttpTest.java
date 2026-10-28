package com.mmm.social;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SocialHttpTest {
    private HttpServer server;
    private String base;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void postsAuthenticatedUtf8JsonAndReadsErrorBodies() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        server.createContext("/chat", exchange -> {
            captured.set(exchange.getRequestMethod() + ":" + exchange.getRequestHeaders().getFirst("x-mmm-client-sync-token")
                    + ":" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"error\":\"slow down\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        var response = SocialHttp.request(base + "/chat", "test-token", "{\"message\":\"\u05e9\u05dc\u05d5\u05dd\"}").get(5, TimeUnit.SECONDS);
        assertEquals(429, response.status());
        assertTrue(response.body().contains("slow down"));
        assertEquals("POST:test-token:{\"message\":\"\u05e9\u05dc\u05d5\u05dd\"}", captured.get());
    }

    @Test void neverFollowsRedirectWithCredentialHeader() throws Exception {
        AtomicInteger forwarded = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", base + "/secret");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.createContext("/secret", exchange -> { forwarded.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        assertEquals(307, SocialHttp.request(base + "/redirect", "secret", "{}").get(5, TimeUnit.SECONDS).status());
        assertEquals(0, forwarded.get());
    }

    @Test void consumesSseThroughActualLocalSocket() throws Exception {
        CompletableFuture<String> received = new CompletableFuture<>();
        CompletableFuture<Integer> ended = new CompletableFuture<>();
        server.createContext("/events", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("event: presence\ndata: {\"active\":true}\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        try (var stream = SocialHttp.events(base + "/events", "local-token", new SocialHttp.Events() {
            @Override public void opened() {}
            @Override public void event(String name, String data) { received.complete(name + ":" + data); }
            @Override public void ended(int status) { ended.complete(status); }
        })) {
            assertEquals("presence:{\"active\":true}", received.get(5, TimeUnit.SECONDS));
            assertEquals(200, ended.get(5, TimeUnit.SECONDS));
        }
    }
}
