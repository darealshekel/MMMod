package com.mmm.config;

import com.google.gson.JsonObject;
import com.mmm.util.PeriodKeys;
import com.mmm.util.WeeklyProgressPolicy;

/** Merges detached JSON only; the persistence worker never changes game counters. */
final class CrossVersionSnapshot {
    private CrossVersionSnapshot() {}

    static JsonObject merge(JsonObject snapshot, JsonObject disk, long now) {
        JsonObject result = new com.google.gson.JsonParser().parse(snapshot.toString()).getAsJsonObject();
        JsonObject incoming = disk == null ? new JsonObject() : disk;
        long localDaily = Math.max(number(snapshot, "dailyBlocksMined"), number(snapshot, "dailyProgress"));
        long diskDaily = Math.max(number(incoming, "dailyBlocksMined"), number(incoming, "dailyProgress"));
        long daily = PeriodKeys.dailyRelation(string(snapshot, "dailyBlocksDate"), now) == PeriodKeys.Relation.OLDER
                ? 0 : localDaily;
        var relation = PeriodKeys.dailyRelation(string(incoming, "dailyBlocksDate"), now);
        if (relation == PeriodKeys.Relation.CURRENT || (daily == 0 &&
                (relation == PeriodKeys.Relation.MISSING || relation == PeriodKeys.Relation.INVALID)))
            daily = Math.max(daily, diskDaily);

        var weekly = WeeklyProgressPolicy.evaluate(number(snapshot, "weeklyBlocksMined"),
                string(snapshot, "weeklyBlocksWeek"), number(snapshot, "weeklyLastResetMs"), now);
        long weeklyBlocks = weekly.blocks();
        relation = PeriodKeys.weeklyRelation(string(incoming, "weeklyBlocksWeek"), now);
        if (relation == PeriodKeys.Relation.CURRENT || (weeklyBlocks == 0 &&
                (relation == PeriodKeys.Relation.MISSING || relation == PeriodKeys.Relation.INVALID)))
            weeklyBlocks = Math.max(weeklyBlocks, number(incoming, "weeklyBlocksMined"));
        result.addProperty("dailyBlocksDate", PeriodKeys.currentDailyKey(now));
        result.addProperty("dailyBlocksMined", daily);
        result.addProperty("dailyProgress", daily);
        result.addProperty("dailyGoalLastResetMs", Math.max(validReset(snapshot, "dailyGoalLastResetMs", now),
                validReset(incoming, "dailyGoalLastResetMs", now)));
        result.addProperty("weeklyBlocksWeek", weekly.periodKey());
        result.addProperty("weeklyBlocksMined", weeklyBlocks);
        result.addProperty("weeklyLastResetMs", Math.max(weekly.lastResetAtMs(), validReset(incoming, "weeklyLastResetMs", now)));
        result.addProperty("personalRecordDailyBlocks", Math.max(Math.max(localDaily, diskDaily),
                Math.max(number(snapshot, "personalRecordDailyBlocks"), number(incoming, "personalRecordDailyBlocks"))));
        result.addProperty("personalRecordWeeklyBlocks", Math.max(Math.max(number(snapshot, "weeklyBlocksMined"),
                number(incoming, "weeklyBlocksMined")), Math.max(number(snapshot, "personalRecordWeeklyBlocks"),
                number(incoming, "personalRecordWeeklyBlocks"))));
        return result;
    }

    private static long validReset(JsonObject value, String key, long now) {
        long number = number(value, key);
        return number <= now ? number : 0;
    }

    private static long number(JsonObject value, String key) {
        try { return Math.max(0L, value.get(key).getAsLong()); }
        catch (RuntimeException ignored) { return 0L; }
    }

    private static String string(JsonObject value, String key) {
        try { return value.get(key).getAsString(); }
        catch (RuntimeException ignored) { return ""; }
    }
}
