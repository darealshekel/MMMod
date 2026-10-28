package com.mmm.tracker;

import com.mmm.config.Configs;
import com.mmm.storage.WorldIdentity;
import com.mmm.storage.WorldSessionContext;
import com.mmm.sync.WebsiteLinkManager;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class PinnedCollection
{
    public record View(String blockId, String name, String scope, long count, long target, CollectionRankClient.Rank rank) {
        public View(String blockId, String name, String scope, long count, long target) {
            this(blockId, name, scope, count, target, new CollectionRankClient.Rank(CollectionRankClient.Status.LOADING, 0, 0));
        }
        public boolean completed() { return count >= CollectionProgress.milestones(blockId).getLast(); }
        public double fraction() { return target <= 0 ? 0 : Math.min(1, Math.max(0, count) / (double)target); }
    }
    private static View view;
    private static CollectionWebsiteClient.Snapshot website;
    private static String account = "";
    private static boolean loading, failed;
    private static long nextRefresh, nextSample;
    private PinnedCollection() {}

    public static View view() { return view; }
    public static boolean hasPin() {
        var player = Minecraft.getInstance().player;
        return player != null && player.getStringUUID().equals(Configs.pinnedCollectionPlayer)
                && !Configs.pinnedCollectionBlock.isBlank();
    }
    public static boolean matches(String block, boolean global) {
        return hasPin() && CollectionProgress.collectionId(Configs.pinnedCollectionBlock).equals(CollectionProgress.collectionId(block))
                && Configs.pinnedCollectionWorld.equals(global ? "" : WorldSessionContext.getCurrentWorldId());
    }
    public static void pin(String block, boolean global, CollectionWebsiteClient.Snapshot snapshot) {
        var client = Minecraft.getInstance();
        if (client.player == null) return;
        Configs.pinnedCollectionBlock = block;
        Configs.pinnedCollectionWorld = global ? "" : WorldSessionContext.getCurrentWorldId();
        Configs.pinnedCollectionWorldName = global ? "" : WorldSessionContext.getCurrentWorldName();
        Configs.pinnedCollectionPlayer = client.player.getStringUUID();
        account = WebsiteLinkManager.isCurrentPlayerLinked() ? Configs.pinnedCollectionPlayer : "";
        website = snapshot;
        loading = false; failed = false; nextRefresh = 0; nextSample = 0;
        Configs.requestSave();
        tick(client);
    }
    public static void unpin() {
        Configs.pinnedCollectionBlock = "";
        Configs.pinnedCollectionWorld = "";
        Configs.pinnedCollectionWorldName = "";
        Configs.pinnedCollectionPlayer = "";
        view = null;
        Configs.requestSave();
    }
    public static void tick(Minecraft client) {
        if (!hasPin() || client.level == null) { view = null; return; }
        long now = System.currentTimeMillis();
        if (now < nextSample) return;
        nextSample = now + 200;
        String linked = WebsiteLinkManager.isCurrentPlayerLinked() ? client.player.getStringUUID() : "";
        if (!linked.equals(account)) {
            account = linked; website = null; loading = false; failed = false; nextRefresh = 0;
        }
        boolean global = Configs.pinnedCollectionWorld.isBlank();
        if (global && !linked.isBlank() && !loading && now >= nextRefresh) {
            loading = true;
            String username = WebsiteLinkManager.getPersistedUsername();
            if (username.isBlank()) username = client.player.getGameProfile().name();
            CollectionWebsiteClient.load(linked, username).whenComplete((data, failure) -> client.execute(() -> {
                if (!linked.equals(account)) return;
                loading = false; failed = failure != null;
                if (failure == null) website = data;
                nextRefresh = System.currentTimeMillis() + (failure == null ? 60_000 : 30_000);
                nextSample = 0;
            }));
        }
        String block = CollectionProgress.collectionId(Configs.pinnedCollectionBlock);
        Identifier id = Identifier.tryParse(CollectionProgress.iconId(block));
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) { view = null; return; }
        var sources = new ArrayList<CollectionProgress.Source>();
        for (var world : Configs.WORLD_STATS) {
            String worldId = WorldIdentity.canonicalWorldId(world.worldId, world.kind, world.host);
            if ((global || Configs.pinnedCollectionWorld.equals(worldId)) && world.blockBreakdown != null)
                sources.add(new CollectionProgress.Source(worldId, world.blockBreakdown));
        }
        long count = global && website != null ? LiveCollections.counts(website).getOrDefault(block, 0L)
                : LiveCollections.localCounts(CollectionProgress.aggregate(sources), global ? null : Configs.pinnedCollectionWorld).getOrDefault(block, 0L);
        String scope = global ? website != null ? failed ? "Global (cached + local)" : "Global (live)"
                : loading ? "Global (loading)" : "Global (local only)" : Configs.pinnedCollectionWorldName;
        long next = CollectionProgress.next(block, count);
        String name = CollectionProgress.customName(block);
        String username = WebsiteLinkManager.isCurrentPlayerLinked() ? WebsiteLinkManager.getPersistedUsername() : "";
        if (username.isBlank()) username = client.player.getGameProfile().name();
        view = new View(block, name == null ? BuiltInRegistries.BLOCK.getValue(id).getName().getString() : name, scope, count,
                next == 0 ? CollectionProgress.milestones(block).getLast() : next, CollectionRankClient.get(block, username));
    }
}
