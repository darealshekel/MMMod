package com.mmm.advancement;

public record MmmAdvancementSnapshot(
        long totalSessionMs,
        int longestStreakDays,
        int bestHourBlocks,
        long longestSessionMs,
        int sessionsAt40kBph,
        int sessionsAt50kBph)
{
    public MmmAdvancementSnapshot(long totalSessionMs, int longestStreakDays, int bestHourBlocks, long longestSessionMs)
    {
        this(totalSessionMs, longestStreakDays, bestHourBlocks, longestSessionMs, 0, 0);
    }

    public MmmAdvancementSnapshot
    {
        totalSessionMs = Math.max(0L, totalSessionMs);
        longestStreakDays = Math.max(0, longestStreakDays);
        bestHourBlocks = Math.max(0, bestHourBlocks);
        longestSessionMs = Math.max(0L, longestSessionMs);
        sessionsAt40kBph = Math.max(0, sessionsAt40kBph);
        sessionsAt50kBph = Math.max(0, sessionsAt50kBph);
    }
}
