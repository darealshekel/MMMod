package com.mmm.tracker;

import com.google.gson.JsonObject;
import com.mmm.sync.RankLocalProgress;
import java.util.*;

/** One player's published baseline, unsynced mining, and lifetime notification watermark. */
public final class CollectionLiveProgress {
    private final Map<String, RankLocalProgress> blocks = new HashMap<>();
    private final Map<String, Long> announced = new HashMap<>();
    private boolean websiteReady;

    public void seed(Map<String, Long> local) {
        CollectionProgress.groupCounts(local).forEach((id, count) -> {
            if (!blocks.containsKey(id)) {
                blocks.put(id, new RankLocalProgress(Math.max(0, count), 0));
                markExisting(id, count);
            }
        });
    }
    public void reconcile(Map<String, Long> published) {
        published = CollectionProgress.groupCounts(published);
        var ids = new HashSet<>(blocks.keySet());
        ids.addAll(published.keySet());
        for (String id : ids) {
            if (id.equals("mmm:non_insta_deepslate")) continue;
            var value = blocks.computeIfAbsent(id, ignored -> new RankLocalProgress(0, 0));
            long incoming = Math.max(0, published.getOrDefault(id, 0L));
            if (!websiteReady) {
                long missing = Math.max(0, value.website() - incoming);
                long pending = Math.min(Long.MAX_VALUE - incoming, missing);
                pending += Math.min(Long.MAX_VALUE - incoming - pending, value.pending());
                value = new RankLocalProgress(incoming, pending);
                blocks.put(id, value);
            } else value.reconcile(incoming);
            // Imported history and website refreshes are not new local achievements.
            markExisting(id, value.total());
        }
        websiteReady = true;
    }
    public List<Long> mined(String id) {
        id = CollectionProgress.collectionId(id);
        var value = blocks.computeIfAbsent(id, ignored -> new RankLocalProgress(0, 0));
        value.mined();
        long previous = announced.getOrDefault(id, 0L);
        var reached = CollectionProgress.milestones(id).stream()
                .filter(goal -> goal > previous && value.total() >= goal).toList();
        markExisting(id, value.total());
        return reached;
    }
    private void markExisting(String id, long count) {
        long reached = CollectionProgress.milestones(id).stream().filter(goal -> count >= goal).mapToLong(Long::longValue).max().orElse(0);
        announced.merge(id, reached, Math::max);
    }
    public Map<String, Long> counts() {
        var result = new HashMap<String, Long>();
        blocks.forEach((id, value) -> result.put(id, value.total()));
        return Map.copyOf(result);
    }
    public JsonObject save() {
        var result = new JsonObject();
        result.addProperty("_websiteReady", websiteReady);
        blocks.forEach((id, value) -> {
            var item = new JsonObject();
            item.addProperty("website", value.website());
            item.addProperty("pending", value.pending());
            item.addProperty("announced", announced.getOrDefault(id, 0L));
            result.add(id, item);
        });
        return result;
    }
    public static CollectionLiveProgress load(JsonObject saved) {
        var result = new CollectionLiveProgress();
        if (saved == null) return result;
        if (saved.has("_websiteReady")) result.websiteReady = saved.get("_websiteReady").getAsBoolean();
        for (var entry : saved.entrySet()) {
            if (entry.getKey().startsWith("_")) continue;
            if (result.blocks.size() >= 512) break;
            try {
                var item = entry.getValue().getAsJsonObject();
                String id = CollectionProgress.collectionId(entry.getKey());
                var existing = result.blocks.getOrDefault(id, new RankLocalProgress(0, 0));
                result.blocks.put(id, new RankLocalProgress(CollectionProgress.add(existing.website(), Math.max(0, item.get("website").getAsLong())),
                        CollectionProgress.add(existing.pending(), Math.max(0, item.get("pending").getAsLong()))));
                result.announced.merge(id, Math.max(0, item.get("announced").getAsLong()), Math::max);
            } catch (RuntimeException ignored) { }
        }
        // A new cap or combined collection can make existing history complete a new tier.
        result.blocks.forEach((id, value) -> result.markExisting(id, value.total()));
        return result;
    }
}
