package com.mmm.tracker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmm.config.Configs;
import com.mmm.social.PublicChatClient;
import com.mmm.storage.WorldIdentity;
import com.mmm.sync.WebsiteLinkManager;
import com.mmm.util.BlockBreakdownCatalog;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Client-thread coordinator; rendering only reads the prepared counters. */
public final class LiveCollections {
    private static String account = "";
    private static JsonObject accounts = new JsonObject();
    private static CollectionLiveProgress progress = new CollectionLiveProgress();
    private static final Deque<String> outbox = new ArrayDeque<>();
    private static final Map<String, Long> nonInstantWorlds = new HashMap<>();
    private static CollectionWebsiteClient.Snapshot website;
    private static boolean loading, sending, dirty;
    private static long refreshAt, saveAt, sendAt;
    private LiveCollections() {}
    public static JsonObject syncReceipt(String world) {
        if (dirty) persist();
        return progress.receipt(world);
    }
    public static void acknowledge(JsonObject receipt) {
        progress.acknowledge(receipt);
        CollectionWebsiteClient.invalidate(account);
        dirty = true;
        refreshAt = 0;
    }

    private static boolean activate(MinecraftClient client) {
        if (client == null || client.player == null) return false;
        String uuid = client.player.getUuidAsString();
        if (uuid.equals(account)) return true;
        if (!account.isEmpty()) persist();
        try { accounts = JsonParser.parseString(Configs.collectionLiveState).getAsJsonObject(); }
        catch (RuntimeException ignored) { accounts = new JsonObject(); }
        account = uuid;
        progress = new CollectionLiveProgress(); outbox.clear(); nonInstantWorlds.clear();
        try {
            var saved = accounts.getAsJsonObject(uuid);
            if (saved != null) {
                progress = CollectionLiveProgress.load(saved.getAsJsonObject("progress"));
                if (saved.has("nonInstantWorlds")) saved.getAsJsonObject("nonInstantWorlds").entrySet().forEach(entry ->
                        nonInstantWorlds.put(entry.getKey(), Math.max(0, entry.getValue().getAsLong())));
                for (var item : saved.getAsJsonArray("outbox")) {
                    String message = item.getAsString();
                    if (message.length() <= PublicChatClient.MAX_MESSAGE_LENGTH && outbox.size() < 512) outbox.addLast(message);
                }
            }
        } catch (RuntimeException ignored) { }
        var sources = new ArrayList<CollectionProgress.Source>();
        for (var world : Configs.WORLD_STATS)
            sources.add(new CollectionProgress.Source(WorldIdentity.canonicalWorldId(world.worldId, world.kind, world.host), world.blockBreakdown));
        progress.seed(CollectionProgress.aggregate(sources));
        website = null; loading = false; sending = false; dirty = true; refreshAt = 0; saveAt = 0; sendAt = 0;
        return true;
    }
    public static void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            if (!account.isEmpty() && dirty) persist();
            return;
        }
        if (!activate(client)) return;
        long now = System.currentTimeMillis();
        if (dirty && now >= saveAt) persist();
        if (!WebsiteLinkManager.isCurrentPlayerLinked()) { finishCatchUp(client); return; }
        String uuid = account;
        if (!loading && now >= refreshAt) {
            loading = true;
            String name = WebsiteLinkManager.getPersistedUsername();
            if (name.isBlank()) name = client.player.getGameProfile().getName();
            CollectionWebsiteClient.load(uuid, name).whenComplete((result, failure) -> client.execute(() -> {
                if (!uuid.equals(account)) return;
                loading = false; refreshAt = System.currentTimeMillis() + (failure == null ? 60_000 : 30_000);
                if (failure == null) accept(result);
                finishCatchUp(client);
            }));
        }
        if (!sending && !outbox.isEmpty() && now >= sendAt && Configs.Generic.SHARE_COLLECTION_MILESTONES.getBooleanValue()) {
            sending = true;
            String message = outbox.peekFirst();
            PublicChatClient.sendCollectionMessage(message).whenComplete((ok, error) -> client.execute(() -> {
                if (!uuid.equals(account)) return;
                sending = false; sendAt = System.currentTimeMillis() + (Boolean.TRUE.equals(ok) ? 3_000 : 30_000);
                if (Boolean.TRUE.equals(ok) && message.equals(outbox.peekFirst())) { outbox.removeFirst(); persist(); }
            }));
        }
    }
    private static void accept(CollectionWebsiteClient.Snapshot snapshot) {
        if (snapshot != null && (website == null || snapshot.receivedAt() > website.receivedAt())) {
            progress.reconcile(snapshot.counts()); website = snapshot; dirty = true;
        }
    }
    private static void finishCatchUp(MinecraftClient client) {
        if (!progress.needsCatchUp() || client.player == null || client.world == null) return;
        var summary = progress.finishCatchUp();
        // This is an account migration, so it stays local and never fills the shared chat outbox.
        if (summary.milestones() > 0) client.player.sendMessage(Text.literal("[MMM] Collections updated: "
                + summary.milestones() + " new milestone" + (summary.milestones() == 1 ? "" : "s")
                + " unlocked across " + summary.collections() + " collection" + (summary.collections() == 1 ? "" : "s")
                + " from your existing mining totals.").formatted(Formatting.GREEN), false);
        persist();
    }
    public static Map<String, Long> counts(CollectionWebsiteClient.Snapshot snapshot) {
        activate(MinecraftClient.getInstance());
        accept(snapshot);
        return progress.counts();
    }
    public static void mined(String block) {
        var client = MinecraftClient.getInstance();
        if (!activate(client) || (!BlockBreakdownCatalog.isValid(block) && !block.equals("mmm:non_insta_deepslate"))) return;
        if (!WebsiteLinkManager.isCurrentPlayerLinked()) finishCatchUp(client);
        var milestones = progress.mined(block, com.mmm.storage.WorldSessionContext.getCurrentWorldId());
        dirty = true;
        for (long goal : milestones) {
            String collection = CollectionProgress.collectionId(block);
            String name = CollectionProgress.customName(collection);
            if (name == null) name = Registries.BLOCK.get(Identifier.of(CollectionProgress.iconId(collection))).getName().getString();
            String amount = CollectionProgress.targetLabel(goal);
            String message = "completed the " + name + " Collection " + amount + " milestone!";
            if (WebsiteLinkManager.isCurrentPlayerLinked() && Configs.Generic.SHARE_COLLECTION_MILESTONES.getBooleanValue()) {
                if (outbox.size() < 512) outbox.addLast(message);
            } else {
                client.player.sendMessage(Text.literal("[MMM] " + message).formatted(Formatting.GREEN), false);
            }
        }
        if (!milestones.isEmpty()) persist();
    }
    public static void nonInstantDeepslate(String world) {
        if (!activate(MinecraftClient.getInstance())) return;
        nonInstantWorlds.merge(world, 1L, CollectionProgress::add);
        mined("mmm:non_insta_deepslate");
    }
    public static Map<String, Long> localCounts(Map<String, Long> raw, String world) {
        activate(MinecraftClient.getInstance());
        var counts = new HashMap<>(CollectionProgress.groupCounts(raw));
        long special = world == null ? nonInstantWorlds.values().stream().reduce(0L, CollectionProgress::add)
                : nonInstantWorlds.getOrDefault(world, 0L);
        counts.put("mmm:non_insta_deepslate", special);
        return Map.copyOf(counts);
    }
    private static void persist() {
        if (account.isEmpty()) return;
        var state = new JsonObject(); state.add("progress", progress.save());
        var special = new JsonObject(); nonInstantWorlds.forEach(special::addProperty); state.add("nonInstantWorlds", special);
        var messages = new JsonArray(); outbox.forEach(messages::add); state.add("outbox", messages);
        accounts.add(account, state);
        while (accounts.size() > 8) {
            String oldest = accounts.keySet().stream().filter(key -> !key.equals(account)).findFirst().orElseThrow();
            accounts.remove(oldest);
        }
        Configs.collectionLiveState = accounts.toString(); dirty = false; saveAt = System.currentTimeMillis() + 5_000;
        Configs.requestSave();
    }
}
