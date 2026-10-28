package com.mmm.social;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Java 8 transport. Redirects are deliberately disabled for authenticated requests. */
public final class SocialHttp {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ExecutorService IO = Executors.newFixedThreadPool(6, runnable -> {
        Thread thread = new Thread(runnable, "MMM social " + IDS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private static final int MAX_BODY = 2 * 1024 * 1024;

    private SocialHttp() {}

    public record Response(int status, String body) {
        public boolean successful() { return status >= 200 && status < 300; }
    }

    public static CompletableFuture<Response> request(String url, String token, String body) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                connection = connection(url, token, "application/json");
                if (body != null) {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.setFixedLengthStreamingMode(bytes.length);
                    try (java.io.OutputStream out = connection.getOutputStream()) { out.write(bytes); }
                }
                int status = connection.getResponseCode();
                InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                if (input == null) return new Response(status, "");
                try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (out.size() + count > MAX_BODY) throw new IOException("Social response too large");
                        out.write(buffer, 0, count);
                    }
                    return new Response(status, new String(out.toByteArray(), StandardCharsets.UTF_8));
                }
            } catch (IOException error) {
                throw new CompletionException(error);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, IO);
    }

    private static HttpURLConnection connection(String url, String token, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "MMMod/" + com.mmm.Reference.MOD_VERSION);
        if (token != null && !token.isEmpty()) connection.setRequestProperty("x-mmm-client-sync-token", token);
        return connection;
    }

    public interface Events {
        void opened();
        void event(String name, String data);
        void ended(int status);
    }

    public static Stream events(String url, String token, Events events) {
        Stream stream = new Stream();
        IO.execute(() -> {
            int status = 0;
            try {
                if (stream.closed) return;
                HttpURLConnection connection = connection(url, token, "text/event-stream");
                stream.connection = connection;
                connection.setReadTimeout(45_000);
                connection.setRequestProperty("Cache-Control", "no-cache");
                if (stream.closed) return;
                status = connection.getResponseCode();
                if (status != 200 || stream.closed) return;
                try (InputStream in = connection.getInputStream()) {
                    events.opened();
                    SocialSseReader.read(in, () -> !stream.closed, events::event);
                }
            } catch (IOException ignored) {
                // The session applies bounded reconnect/backoff, including read timeouts.
            } finally {
                if (stream.connection != null) stream.connection.disconnect();
                events.ended(status);
            }
        });
        return stream;
    }

    public static final class Stream implements AutoCloseable {
        private volatile boolean closed;
        private volatile HttpURLConnection connection;

        @Override public void close() {
            closed = true;
            HttpURLConnection active = connection;
            // Closing a streaming connection can wait on a socket read; never do that on the game thread.
            if (active != null) IO.execute(active::disconnect);
        }
    }
}
