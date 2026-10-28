package com.mmm.social;

import com.google.gson.JsonObject;
import com.mmm.config.Configs;
import com.mmm.legacy.social.LegacySocialText;
import com.mmm.sync.WebsiteLinkManager;
import com.mmm.tracker.MiningStats;
import com.mmm.util.MmmLog;
import com.mmm.util.UiFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;

import static com.mmm.legacy.social.LegacySocialText.text;

public final class PublicChatClient {
    public static final String PUBLIC_ROOM_ID = "5e2f7e9aa53b975bfb92affe138a51df82306936f2fb71acda97e51aeaf02e48";
    public static final int MAX_MESSAGE_LENGTH = 200;
    private static final String ENDPOINT = System.getProperty("mmm.socialEndpoint", "https://www.mmmaniacs.com/api/mod-social");
    private static final SocialSession SESSION = new SocialSession(new SocialSession.Transport() {
        @Override public CompletableFuture<SocialHttp.Response> post(String path, String token, JsonObject payload) {
            return SocialHttp.request(ENDPOINT + path, token, payload.toString()).whenComplete((response, error) -> {
                if (Configs.Generic.WEBSITE_SYNC_DEBUG.getBooleanValue()
                        && (error != null || response == null || !response.successful()))
                    MmmLog.LOGGER.warn("[MMM] Social {} failed: {}", path, error != null
                            ? error.getClass().getSimpleName() : response == null ? "no response" : response.status());
            });
        }
        @Override public AutoCloseable events(String query, String token, SocialHttp.Events listener) {
            return SocialHttp.events(ENDPOINT + "/events" + query, token, listener);
        }
    }, new ClientSink(), System::currentTimeMillis);
    private static int ticks;

    private PublicChatClient() {}

    public static void onClientTick(Minecraft client) {
        SESSION.identify(identity(client));
        if (++ticks < 20) return;
        ticks = 0;
        SESSION.tick(MiningStats.isActivelyMining());
    }

    public static void sendMessage(String message) {
        SESSION.identify(identity(Minecraft.getInstance()));
        SESSION.send(message, true);
    }

    public static CompletableFuture<Boolean> sendCollectionMessage(String message) {
        SESSION.identify(identity(Minecraft.getInstance()));
        return SESSION.send(message, false);
    }

    public static void publishMilestone(int threshold, MiningStats.GoalProgress progress) {
        if (progress == null || !Configs.Generic.SHARE_GOAL_MILESTONES.getBooleanValue()) return;
        SESSION.identify(identity(Minecraft.getInstance()));
        SESSION.publishMilestone(threshold, progress.current(), progress.target());
    }

    public static void publishAdvancement(String title, String description) {
        String safeTitle = SocialSession.normalizeMessage(title);
        String safeDescription = SocialSession.normalizeMessage(description);
        String message = "earned the MMM advancement [" + safeTitle + "] - " + safeDescription;
        if (safeTitle.isEmpty() || message.length() > MAX_MESSAGE_LENGTH) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        SocialSession.Identity identity = identity(client);
        SESSION.identify(identity);
        if (identity != null) SESSION.queueAdvancement(message);
        else show(LegacySocialText.prefix().append(text("Advancement Made! ", Formatting.RED))
                .append(text(safeTitle, Formatting.WHITE)).append(text(" - " + safeDescription, Formatting.GRAY)));
    }

    public static void disconnect() { SESSION.disconnect(); ticks = 0; }

    private static SocialSession.Identity identity(Minecraft client) {
        if (client == null || client.player == null || client.world == null || !WebsiteLinkManager.isCurrentPlayerLinked()) return null;
        return new SocialSession.Identity(client.player.getUuidString(), Configs.cloudClientId, Configs.websiteSyncToken, client.getNetworkHandler());
    }

    private static void show(Text message) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) client.player.addMessage(message, false);
    }

    private static final class ClientSink implements SocialSession.Sink {
        @Override public void execute(Runnable action) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) client.executeTask(action);
        }
        @Override public boolean isCurrent(SocialSession.Identity expected) { return expected.equals(identity(Minecraft.getInstance())); }
        @Override public void chat(String username, String message, String rawMessage) {
            if (!Configs.Generic.SHOW_MMM_CHAT_MESSAGES.getBooleanValue() || MmmChatIgnoreList.isIgnored(username)) return;
            String visible = Configs.Generic.CENSOR_MMM_CHAT.getBooleanValue()
                    ? MmmChatCensor.censor(message) : rawMessage.isEmpty() ? message : rawMessage;
            show(LegacySocialText.prefix().append(text(username, Formatting.RED)).append(text(": " + visible, Formatting.WHITE)));
        }
        @Override public void milestone(String username, int threshold, long current, long target) {
            Minecraft client = Minecraft.getInstance();
            if (!Configs.Generic.RECEIVE_GOAL_MILESTONES.getBooleanValue() || MmmChatIgnoreList.isIgnored(username)
                    || client.player == null || username.equalsIgnoreCase(client.player.getName())) return;
            MiningStats.GoalProgress progress = new MiningStats.GoalProgress("Daily Goal", true, current, target);
            int color = UiFormat.getGoalProgressColor(progress);
            String detail = switch (threshold) {
                case 25 -> " reached the first daily milestone";
                case 50 -> " is halfway through today's goal";
                case 75 -> " reached the final stretch";
                case 100 -> " completed today's goal";
                default -> " pushed today's goal further";
            };
            show(LegacySocialText.prefix().append(text(username, Formatting.WHITE))
                    .append(text(detail + " (", Formatting.GRAY))
                    .append(LegacySocialText.rgb(threshold + "% - " + String.format(Locale.US, "%,d / %,d", current, target), color))
                    .append(text(" blocks).", Formatting.GRAY)));
        }
        @Override public void error(String message) { show(LegacySocialText.prefix().append(text(message, Formatting.RED))); }
        @Override public void clearPresence() { ActiveDiggerManager.clear(); }
        @Override public void state(JsonObject event) { ActiveDiggerManager.applySocialState(event); }
        @Override public void presence(JsonObject event) { ActiveDiggerManager.applyPresence(event); }
        @Override public void friends(JsonObject event) { ActiveDiggerManager.applyFriends(event); }
    }
}
