package com.mmm.tracker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmm.config.Configs;
import com.mmm.storage.WorldIdentity;
import com.mmm.util.BlockBreakdownCatalog;
import java.util.*;
import net.minecraft.client.MinecraftClient;

/** Client-thread coordinator; rendering only reads the prepared counters. */
public final class LiveCollections {
    private static String account = "";
    private static JsonObject accounts = new JsonObject();
    private static CollectionLiveProgress progress = new CollectionLiveProgress();
    private static final Map<String, Long> nonInstantWorlds = new HashMap<>();
    private static CollectionWebsiteClient.Snapshot website;
    private static boolean dirty;
    private static long saveAt;
    private LiveCollections() {}

    private static boolean activate(MinecraftClient client) {
        if (client == null || client.player == null) return false;
        String uuid = client.player.getUuidAsString();
        if (uuid.equals(account)) return true;
        if (!account.isEmpty()) persist();
        try { accounts = new JsonParser().parse(Configs.collectionLiveState).getAsJsonObject(); }
        catch (RuntimeException ignored) { accounts = new JsonObject(); }
        account = uuid;
        progress = new CollectionLiveProgress(); nonInstantWorlds.clear();
        try {
            var saved = accounts.getAsJsonObject(uuid);
            if (saved != null) {
                progress = CollectionLiveProgress.load(saved.getAsJsonObject("progress"));
                if (saved.has("nonInstantWorlds")) saved.getAsJsonObject("nonInstantWorlds").entrySet().forEach(entry ->
                        nonInstantWorlds.put(entry.getKey(), Math.max(0, entry.getValue().getAsLong())));
            }
        } catch (RuntimeException ignored) { }
        var sources = new ArrayList<CollectionProgress.Source>();
        for (var world : Configs.WORLD_STATS)
            sources.add(new CollectionProgress.Source(WorldIdentity.canonicalWorldId(world.worldId, world.kind, world.host), world.blockBreakdown));
        progress.seed(CollectionProgress.aggregate(sources));
        website = null; dirty = true; saveAt = 0;
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
    }
    private static void accept(CollectionWebsiteClient.Snapshot snapshot) {
        if (snapshot != null && (website == null || snapshot.receivedAt() > website.receivedAt())) {
            progress.reconcile(snapshot.counts()); website = snapshot; dirty = true;
        }
    }
    public static Map<String, Long> counts(CollectionWebsiteClient.Snapshot snapshot) {
        activate(MinecraftClient.getInstance());
        accept(snapshot);
        return progress.counts();
    }
    public static void mined(String block) {
        var client = MinecraftClient.getInstance();
        if (!activate(client) || (!BlockBreakdownCatalog.isValid(block) && !block.equals("mmm:non_insta_deepslate"))) return;
        progress.mined(block);
        dirty = true;
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
        var state = accounts.has(account) && accounts.get(account).isJsonObject() ? accounts.getAsJsonObject(account) : new JsonObject();
        state.add("progress", progress.save());
        var special = new JsonObject(); nonInstantWorlds.forEach(special::addProperty); state.add("nonInstantWorlds", special);
        accounts.add(account, state);
        while (accounts.size() > 8) {
            String oldest = accounts.entrySet().stream().map(Map.Entry::getKey).filter(key -> !key.equals(account)).findFirst().orElseThrow();
            accounts.remove(oldest);
        }
        Configs.collectionLiveState = accounts.toString(); dirty = false; saveAt = System.currentTimeMillis() + 5_000;
        Configs.requestSave();
    }
}
