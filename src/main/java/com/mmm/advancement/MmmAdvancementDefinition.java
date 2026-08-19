package com.mmm.advancement;

import java.util.List;
import java.util.Locale;

public enum MmmAdvancementDefinition
{
    FIRST_TIME("first_time", "First Time!", "Use the mod for the first time.", Kind.FIRST_USE, 1L, 1F, 0F),
    ENTRY_LEVEL("entry_level", "Entry Level", "Reach 100 total session hours.", Kind.SESSION_HOURS, 100L, 2F, 0F),
    VETERAN("veteran", "Veteran", "Reach 500 total session hours.", Kind.SESSION_HOURS, 500L, 3F, 0F),
    NO_LIFE("no_life", "No Life", "Reach 1,000 total session hours.", Kind.SESSION_HOURS, 1_000L, 4F, 0F),
    PART_OF_THE_MOD("part_of_the_mod", "Part of the Mod", "Reach 5,000 total session hours.", Kind.SESSION_HOURS, 5_000L, 5F, 0F),

    CONSISTENT("consistent", "Consistent", "Mine 10,000 blocks for 7 days in a row.", Kind.STREAK_DAYS, 7L, 1F, 2F),
    UNSTOPPABLE("unstoppable", "Unstoppable", "Mine 10,000 blocks for 30 days in a row.", Kind.STREAK_DAYS, 30L, 2F, 2F),
    ETERNAL_MINER("eternal_miner", "Eternal Miner", "Mine 10,000 blocks for 60 days in a row.", Kind.STREAK_DAYS, 60L, 3F, 2F),

    DIG_AWARD("dig_award", "Dig Award", "Mine 40,000 blocks in a session's Best Hour.", Kind.BEST_HOUR_BLOCKS, 40_000L, 1F, 4F),
    MINER_AWARD("miner_award", "Miner Award", "Mine 50,000 blocks in a session's Best Hour.", Kind.BEST_HOUR_BLOCKS, 50_000L, 2F, 4F),
    DIG_MASTER("dig_master", "Dig Master", "Mine 60,000 blocks in a session's Best Hour.", Kind.BEST_HOUR_BLOCKS, 60_000L, 3F, 4F),
    HUMAN_QUARRY("human_quarry", "Human Quarry", "Mine 70,000 blocks in a session's Best Hour.", Kind.BEST_HOUR_BLOCKS, 70_000L, 4F, 4F),

    ENDURANCE_I("endurance_i", "Endurance I", "Mine for 5 hours in one session.", Kind.SESSION_ENDURANCE_HOURS, 5L, 1F, 6F),
    ENDURANCE_II("endurance_ii", "Endurance II", "Mine for 10 hours in one session.", Kind.SESSION_ENDURANCE_HOURS, 10L, 2F, 6F),
    ENDURANCE_III("endurance_iii", "Endurance III", "Mine for 16 hours in one session.", Kind.SESSION_ENDURANCE_HOURS, 16L, 3F, 6F),
    ENDURANCE_IV("endurance_iv", "Endurance IV", "Mine for 24 hours in one session.", Kind.SESSION_ENDURANCE_HOURS, 24L, 4F, 6F),
    ENDURANCE_V("endurance_v", "Endurance V", "Mine for 48 hours in one session.", Kind.SESSION_ENDURANCE_HOURS, 48L, 5F, 6F),

    PRECISION("precision", "Precision", "Average at least 40,000 blocks/hour in 10 sessions.", Kind.PRECISION_40K_SESSIONS, 10L, 1F, 8F),
    OPTIMIZATION("optimization", "Optimization", "Average at least 50,000 blocks/hour in 10 sessions.", Kind.PRECISION_50K_SESSIONS, 10L, 2F, 8F);

    private static final long HOUR_MS = 3_600_000L;
    public static final List<MmmAdvancementDefinition> ORDERED = List.of(values());

    private final String id;
    private final String title;
    private final String description;
    private final Kind kind;
    private final long threshold;
    private final float x;
    private final float y;

    MmmAdvancementDefinition(String id, String title, String description, Kind kind, long threshold, float x, float y)
    {
        this.id = id;
        this.title = title;
        this.description = description;
        this.kind = kind;
        this.threshold = threshold;
        this.x = x;
        this.y = y;
    }

    public boolean isReached(MmmAdvancementSnapshot snapshot)
    {
        if (snapshot == null)
        {
            return false;
        }
        return switch (this.kind)
        {
            case FIRST_USE -> true;
            case SESSION_HOURS -> snapshot.totalSessionMs() >= multiplyHours(this.threshold);
            case STREAK_DAYS -> snapshot.longestStreakDays() >= this.threshold;
            case BEST_HOUR_BLOCKS -> snapshot.bestHourBlocks() >= this.threshold;
            case SESSION_ENDURANCE_HOURS -> snapshot.longestSessionMs() >= multiplyHours(this.threshold);
            case PRECISION_40K_SESSIONS -> snapshot.sessionsAt40kBph() >= this.threshold;
            case PRECISION_50K_SESSIONS -> snapshot.sessionsAt50kBph() >= this.threshold;
        };
    }

    public String id() { return this.id; }
    public String title() { return this.title; }
    public String description() { return this.description; }
    public Kind kind() { return this.kind; }
    public long threshold() { return this.threshold; }
    public float x() { return this.x; }
    public float y() { return this.y; }

    public long currentValue(MmmAdvancementSnapshot snapshot)
    {
        if (snapshot == null)
        {
            return 0L;
        }
        return switch (this.kind)
        {
            case FIRST_USE -> 1L;
            case SESSION_HOURS -> snapshot.totalSessionMs() / HOUR_MS;
            case STREAK_DAYS -> snapshot.longestStreakDays();
            case BEST_HOUR_BLOCKS -> snapshot.bestHourBlocks();
            case SESSION_ENDURANCE_HOURS -> snapshot.longestSessionMs() / HOUR_MS;
            case PRECISION_40K_SESSIONS -> snapshot.sessionsAt40kBph();
            case PRECISION_50K_SESSIONS -> snapshot.sessionsAt50kBph();
        };
    }

    public float progressFraction(MmmAdvancementSnapshot snapshot)
    {
        if (this.threshold <= 0L)
        {
            return 1F;
        }
        return Math.min(1F, Math.max(0F, (float) currentValue(snapshot) / (float) this.threshold));
    }

    public String progressLabel(MmmAdvancementSnapshot snapshot)
    {
        long current = currentValue(snapshot);
        return switch (this.kind)
        {
            case FIRST_USE -> "Unlocked";
            case SESSION_HOURS -> formatHoursAndMinutes(snapshot == null ? 0L : snapshot.totalSessionMs())
                    + String.format(Locale.ROOT, " / %,dh", this.threshold);
            case SESSION_ENDURANCE_HOURS -> formatHoursAndMinutes(snapshot == null ? 0L : snapshot.longestSessionMs())
                    + String.format(Locale.ROOT, " / %,dh", this.threshold);
            case STREAK_DAYS -> String.format(Locale.ROOT, "%,d / %,d days", current, this.threshold);
            case BEST_HOUR_BLOCKS -> String.format(Locale.ROOT, "%,d / %,d blocks", current, this.threshold);
            case PRECISION_40K_SESSIONS, PRECISION_50K_SESSIONS -> String.format(
                    Locale.ROOT,
                    "%,d / %,d sessions",
                    current,
                    this.threshold);
        };
    }

    private static String formatHoursAndMinutes(long milliseconds)
    {
        long safeMilliseconds = Math.max(0L, milliseconds);
        long hours = safeMilliseconds / HOUR_MS;
        long minutes = (safeMilliseconds % HOUR_MS) / 60_000L;
        return String.format(Locale.ROOT, "%,dh %02dm", hours, minutes);
    }

    private static long multiplyHours(long hours)
    {
        return hours > Long.MAX_VALUE / HOUR_MS ? Long.MAX_VALUE : hours * HOUR_MS;
    }

    public enum Kind
    {
        FIRST_USE,
        SESSION_HOURS,
        STREAK_DAYS,
        BEST_HOUR_BLOCKS,
        SESSION_ENDURANCE_HOURS,
        PRECISION_40K_SESSIONS,
        PRECISION_50K_SESSIONS
    }
}
