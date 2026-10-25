package com.mmm.sync;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/** Keeps newly mined blocks separate from the published total. */
public final class RankLocalProgress
{
    private long website = -1;
    private long pending;
    private long confirmed;
    private long legacyPending;
    private final Map<String, Long> minedByWorld = new HashMap<>();
    private final Map<String, Long> acknowledgedByWorld = new HashMap<>();

    public RankLocalProgress() {}
    public RankLocalProgress(long website, long pending)
    {
        this.website = Math.max(-1, website);
        this.pending = this.website < 0 ? 0 : Math.min(Math.max(0, pending), Long.MAX_VALUE - this.website);
    }
    public void mined()
    {
        mined("");
    }
    public void mined(String world)
    {
        if (website >= 0 && pending < Long.MAX_VALUE - website) {
            pending++;
            minedByWorld.merge(world, 1L, (a, b) -> a == Long.MAX_VALUE ? a : a + b);
        }
    }
    public JsonObject receipt(String world)
    {
        var receipt = new JsonObject();
        receipt.addProperty("world", world);
        receipt.addProperty("mined", minedByWorld.getOrDefault(world, 0L));
        receipt.addProperty("website", website);
        return receipt;
    }
    public void acknowledge(JsonObject receipt)
    {
        if (receipt == null) return;
        String world = receipt.get("world").getAsString();
        long count = Math.min(minedByWorld.getOrDefault(world, 0L), Math.max(0, receipt.get("mined").getAsLong()));
        long previous = acknowledgedByWorld.getOrDefault(world, 0L);
        if (count <= previous) return;
        long accepted = Math.min(pending - confirmed - legacyPending, count - previous);
        long alreadyPublished = receipt.has("website") ? Math.max(0, website - receipt.get("website").getAsLong()) : 0;
        long absorbed = Math.min(accepted, alreadyPublished);
        pending -= absorbed;
        confirmed += accepted - absorbed;
        acknowledgedByWorld.put(world, count);
    }
    public void initializeWebsite(long published)
    {
        long missing = Math.max(0, website - published);
        pending = Math.min(pending, Long.MAX_VALUE - published);
        missing = Math.min(missing, Long.MAX_VALUE - published - pending);
        pending += missing;
        legacyPending = Math.min(pending, legacyPending + missing);
        website = published;
    }
    public JsonObject save()
    {
        var state = new JsonObject();
        state.addProperty("website", website);
        state.addProperty("pending", pending);
        state.addProperty("confirmed", confirmed);
        state.addProperty("legacyPending", legacyPending);
        var mined = new JsonObject(); minedByWorld.forEach(mined::addProperty); state.add("minedByWorld", mined);
        var acknowledged = new JsonObject(); acknowledgedByWorld.forEach(acknowledged::addProperty); state.add("acknowledgedByWorld", acknowledged);
        return state;
    }
    public static RankLocalProgress load(JsonObject state)
    {
        var value = new RankLocalProgress(state.get("website").getAsLong(), state.get("pending").getAsLong());
        if (state.has("confirmed")) value.confirmed = Math.min(value.pending, Math.max(0, state.get("confirmed").getAsLong()));
        // Old estimates have no world receipts. Keep their migration behavior separate
        // from new mining, which must always have an explicit acknowledgement.
        value.legacyPending = state.has("legacyPending") ? Math.min(value.pending - value.confirmed,
                Math.max(0, state.get("legacyPending").getAsLong())) : state.has("minedByWorld") ? 0 : value.pending - value.confirmed;
        if (state.has("minedByWorld")) state.getAsJsonObject("minedByWorld").entrySet().stream().limit(4096)
                .forEach(e -> value.minedByWorld.put(e.getKey(), Math.max(0, e.getValue().getAsLong())));
        if (state.has("acknowledgedByWorld")) state.getAsJsonObject("acknowledgedByWorld").entrySet().stream().limit(4096)
                .forEach(e -> value.acknowledgedByWorld.put(e.getKey(), Math.max(0, e.getValue().getAsLong())));
        return value;
    }
    public void reconcile(long published)
    {
        if (published < 0) throw new IllegalArgumentException("Negative website total");
        // A manual edit or another world's sync is not a receipt for this client's mining.
        if (website >= 0 && published > website) {
            long legacyAbsorbed = Math.min(legacyPending, published - website);
            legacyPending -= legacyAbsorbed;
            pending -= legacyAbsorbed;
            long absorbed = Math.min(confirmed, published - website - legacyAbsorbed);
            pending -= absorbed;
            confirmed -= absorbed;
        }
        website = published;
        pending = Math.min(pending, Long.MAX_VALUE - website);
        confirmed = Math.min(confirmed, pending);
        legacyPending = Math.min(legacyPending, pending - confirmed);
    }
    public long website() { return website; }
    public long pending() { return pending; }
    public long total() { return Math.max(0, website) + pending; }
}
