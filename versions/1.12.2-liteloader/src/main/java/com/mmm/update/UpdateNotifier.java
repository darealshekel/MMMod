package com.mmm.update;

import com.mmm.Reference;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Formatting;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;

public final class UpdateNotifier {
    public static final String DOWNLOADS_URL = "https://www.mmmaniacs.com/downloads";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MMM release check");
        thread.setDaemon(true);
        return thread;
    });
    private final Supplier<CompletableFuture<String>> fetch;
    private final LongSupplier clock;
    private CompletableFuture<String> request;
    private String cachedVersion;
    private String pendingVersion;
    private long nextCheck;
    private long connection;
    private boolean checked;

    public UpdateNotifier() {
        this(() -> CompletableFuture.supplyAsync(UpdateNotifier::fetchVersion, IO), System::nanoTime);
    }

    UpdateNotifier(Supplier<CompletableFuture<String>> fetch, LongSupplier clock) {
        this.fetch = fetch;
        this.clock = clock;
    }

    public void onJoin(Minecraft client) { join(task -> client.executeTask(task)); }

    void join(Consumer<Runnable> clientThread) {
        long joined = ++connection;
        pendingVersion = null;
        if (checked && clock.getAsLong() - nextCheck < 0) {
            pendingVersion = cachedVersion;
            return;
        }
        if (request == null || request.isDone()) request = fetch.get();
        request.whenComplete((version, error) -> clientThread.accept(() -> {
            cachedVersion = error == null ? version : null;
            checked = true;
            nextCheck = clock.getAsLong() + (error == null ? 60L : 5L) * 60L * 1_000_000_000L;
            if (connection == joined) pendingVersion = cachedVersion;
        }));
    }

    public void onDisconnect() {
        connection++;
        pendingVersion = null;
    }

    String poll(boolean inWorld) {
        if (!inWorld) return null;
        String result = pendingVersion;
        pendingVersion = null;
        return result;
    }

    public void tick(Minecraft client) {
        if (client == null) return;
        String version = poll(client.player != null && client.world != null);
        if (version == null) return;
        LiteralText message = new LiteralText("[MMM] MMMod " + version
                + " is available for your Minecraft version. Click here to download.");
        message.getStyle().setColor(Formatting.YELLOW).setUnderlined(true)
                .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DOWNLOADS_URL))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText("Open the MMM downloads page")));
        client.gui.getChat().addMessage(message);
    }

    private static String fetchVersion() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://www.mmmaniacs.com/api/mod-releases").openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MMM/" + Reference.MOD_VERSION + " (Minecraft 1.12.2; LiteLoader)");
            if (connection.getResponseCode() != 200) throw new java.io.IOException("Release check unavailable");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (output.size() + length > 1_048_576) throw new java.io.IOException("Release response too large");
                    output.write(buffer, 0, length);
                }
                return ReleaseCheck.newerVersion(new String(output.toByteArray(), StandardCharsets.UTF_8),
                        "1.12.2", Reference.MOD_VERSION).orElse(null);
            }
        } catch (Exception failure) {
            throw new CompletionException(failure);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
