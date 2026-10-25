package com.mmm.update;

import com.mmm.Reference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class UpdateNotifier
{
    public static final String DOWNLOADS_URL = "https://www.mmmaniacs.com/downloads";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private CompletableFuture<String> request;
    private String cachedVersion;
    private String pendingVersion;
    private long nextCheck;
    private long connection;

    public void onJoin(MinecraftClient client)
    {
        long joined = ++connection;
        pendingVersion = null;
        if (System.nanoTime() < nextCheck)
        {
            pendingVersion = cachedVersion;
            return;
        }
        // Share an outstanding request across quick disconnects, but never deliver to an old connection.
        if (request == null || request.isDone())
        {
            String minecraft = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
                    .getMetadata().getVersion().getFriendlyString();
            var httpRequest = HttpRequest.newBuilder(URI.create("https://www.mmmaniacs.com/api/mod-releases"))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build();
            request = HTTP.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                if (response.statusCode() != 200) throw new IllegalStateException("Release check unavailable");
                return ReleaseCheck.newerVersion(response.body(), minecraft, Reference.MOD_VERSION).orElse(null);
            });
        }
        request.whenComplete((version, error) -> client.execute(() -> {
            cachedVersion = error == null ? version : null;
            nextCheck = System.nanoTime() + Duration.ofMinutes(error == null ? 60 : 5).toNanos();
            if (connection == joined) pendingVersion = cachedVersion;
        }));
    }

    public void onDisconnect()
    {
        connection++;
        pendingVersion = null;
    }

    public void tick(MinecraftClient client)
    {
        if (pendingVersion == null || client.player == null || client.world == null) return;
        String version = pendingVersion;
        pendingVersion = null;
        client.inGameHud.getChatHud().addMessage(Text.literal("[MMM] MMMod " + version
                + " is available for your Minecraft version. Click here to download.")
                .styled(style -> style.withColor(Formatting.YELLOW).withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DOWNLOADS_URL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Text.literal("Open the MMM downloads page")))));
    }
}
