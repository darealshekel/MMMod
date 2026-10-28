package com.mmm.liteloader.probe;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/** Runs against the packaged/remapped Java 8 artifact, not the compiler output. */
public final class Java8TransportProbe {
    private static final String BODY = "{\"blocks\":12345}";
    private static final String RESPONSE = "{\"ok\":true}";

    public static void main(String[] args) throws Exception {
        if (!System.getProperty("java.specification.version").equals("1.8")) {
            throw new AssertionError("The transport probe must run under Java 8");
        }
        Class.forName("com.mmm.liteloader.LiteLoaderEnvironment")
                .getMethod("initialize", Path.class).invoke(null, Paths.get(args[0]));
        final AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sync", exchange -> {
            try {
                String body;
                try (InputStream input = exchange.getRequestBody(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) out.write(buffer, 0, read);
                    body = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
                boolean valid = "POST".equals(exchange.getRequestMethod()) && BODY.equals(body)
                        && "local-test-only".equals(exchange.getRequestHeaders().getFirst("x-sync-secret"))
                        && "application/json".equals(exchange.getRequestHeaders().getFirst("Accept"))
                        && "MMMod/1.0.20 (Minecraft 1.12.2; LiteLoader)".equals(exchange.getRequestHeaders().getFirst("User-Agent"));
                byte[] response = RESPONSE.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(valid ? 200 : 400, response.length);
                exchange.getResponseBody().write(response);
                if (valid) requests.incrementAndGet();
            } finally { exchange.close(); }
        });
        server.start();
        try {
            Class<?> api = Class.forName("com.mmm.sync.ApiClient");
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sync";
            Method blocking = api.getDeclaredMethod("postJsonBlocking", String.class, String.class, String.class, Map.class);
            blocking.setAccessible(true);
            checkResponse(blocking.invoke(null, endpoint, "local-test-only", BODY, Collections.emptyMap()));
            CompletableFuture<Object> result = new CompletableFuture<>();
            BiConsumer<Object, Throwable> callback = (response, failure) -> {
                if (failure == null) result.complete(response);
                else result.completeExceptionally(failure);
            };
            Method asynchronous = api.getDeclaredMethod("postJson", String.class, String.class, String.class, BiConsumer.class);
            asynchronous.setAccessible(true);
            asynchronous.invoke(null, endpoint, "local-test-only", BODY, callback);
            checkResponse(result.get(25, TimeUnit.SECONDS));
            if (requests.get() != 2) throw new AssertionError("Not all test requests reached the local server");
            System.out.println("JAVA8_TRANSPORT_OK: blocking and async POST preserve JSON, headers, status and body");
        } finally { server.stop(0); }
    }

    private static void checkResponse(Object response) throws Exception {
        Method status = response.getClass().getMethod("statusCode");
        Method body = response.getClass().getMethod("body");
        status.setAccessible(true);
        body.setAccessible(true);
        if (!Integer.valueOf(200).equals(status.invoke(response)) || !RESPONSE.equals(body.invoke(response))) {
            throw new AssertionError("Packaged HTTP transport changed the response");
        }
    }
}
