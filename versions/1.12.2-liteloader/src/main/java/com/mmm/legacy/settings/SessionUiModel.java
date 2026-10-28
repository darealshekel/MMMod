package com.mmm.legacy.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mmm.storage.SessionData;
import com.mmm.tracker.SessionEfficiency;
import java.util.*;
import java.util.function.Function;

public final class SessionUiModel {
    public enum Filter { ALL("All blocks"), ORES("Ores"), OTHER("Other blocks");
        public final String label; Filter(String label) { this.label = label; }
    }
    private SessionUiModel() {}
    public static List<Map.Entry<String, Long>> blocks(SessionData session, String query, Filter filter, boolean byName, Function<String, String> names) {
        Comparator<Map.Entry<String, Long>> order = byName
                ? Comparator.comparing(e -> names.apply(e.getKey()).toLowerCase(Locale.ROOT))
                : Map.Entry.<String, Long>comparingByValue().reversed();
        return session.blockBreakdown.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .filter(e -> filter == Filter.ALL || isOre(e.getKey()) == (filter == Filter.ORES))
                .filter(e -> SettingsUi.matches(names.apply(e.getKey()) + " " + e.getKey(), query))
                .sorted(order.thenComparing(Map.Entry::getKey)).toList();
    }
    private static boolean isOre(String id) { return id.endsWith("_ore") || id.equals("minecraft:ancient_debris"); }
    public static double percent(long amount, long duration) { return duration <= 0 ? 0 : Math.max(0, Math.min(100, amount * 100.0 / duration)); }
    public static int graphCeiling(SessionData session) {
        int peak = session.miningRateBuckets.stream().mapToInt(session::getBucketBlocksPerHour).max().orElse(0);
        return Math.max(1000, Math.min(SessionData.MAX_BLOCKS_PER_HOUR, (int) ((peak * 11L + 9999) / 10000) * 1000));
    }
    public static JsonObject snapshot(SessionData session, String world, long active, long wall, long bestHour) {
        JsonObject json = new JsonObject();
        json.addProperty("world", world); json.addProperty("startTimeMs", session.startTimeMs);
        json.addProperty("activeDurationMs", active); json.addProperty("wallDurationMs", wall);
        json.addProperty("totalBlocks", session.totalBlocks); json.addProperty("bestHourBlocks", bestHour);
        json.addProperty("bestStreakSeconds", session.bestStreakSeconds); json.addProperty("peakBlocksPerHour", session.getPeakBlocksPerHour());
        json.addProperty("averageBlocksPerHour", average(session.totalBlocks, active));
        JsonObject blocks = new JsonObject();
        session.blockBreakdown.forEach(blocks::addProperty); json.add("blockBreakdown", blocks);
        JsonArray buckets = new JsonArray(); session.miningRateBuckets.forEach(buckets::add); json.add("miningRateBuckets", buckets);
        json.add("efficiency", session.efficiency.toJson()); json.addProperty("untrackedMs", session.efficiency.untracked(active));
        return json;
    }
    public static long average(long blocks, long active) { return active <= 0 ? 0 : SessionData.clampBlocksPerHour(Math.round(blocks * 3600000.0 / active)); }
    public static String share(SessionData session, String world, long active, long wall, long bestHour) {
        StringBuilder text = new StringBuilder(world).append("\nBlocks: ").append(session.totalBlocks)
                .append("\nSession IGT: ").append(com.mmm.timer.MmmTimerState.formatTime(active))
                .append("\nSession Time: ").append(com.mmm.timer.MmmTimerState.formatTime(wall))
                .append("\nAverage blocks/hr: ").append(average(session.totalBlocks, active))
                .append("\nBest Hour: ").append(bestHour).append("\nPeak blocks/hr: ").append(session.getPeakBlocksPerHour())
                .append("\nBest Streak: ").append(session.bestStreakSeconds).append("s\n");
        for (SessionEfficiency.Activity activity : SessionEfficiency.Activity.values())
            text.append(activity.label).append(": ").append(com.mmm.timer.MmmTimerState.formatTime(session.efficiency.time(activity))).append("\n");
        text.append("Untracked: ").append(com.mmm.timer.MmmTimerState.formatTime(session.efficiency.untracked(active))).append("\n");
        session.blockBreakdown.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> text.append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
        return text.toString();
    }
}
