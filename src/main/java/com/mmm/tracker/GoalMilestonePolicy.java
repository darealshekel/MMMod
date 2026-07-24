package com.mmm.tracker;

public final class GoalMilestonePolicy
{
    public static final int STEP = 25;

    private GoalMilestonePolicy()
    {
    }

    public static int percent(long progress, long target)
    {
        if (target <= 0L || progress <= 0L)
        {
            return 0;
        }
        double value = Math.floor(progress * 100.0D / target);
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    public static int highestCrossed(int oldPercent, int newPercent)
    {
        if (newPercent <= oldPercent || newPercent < STEP)
        {
            return 0;
        }
        int highest = newPercent - Math.floorMod(newPercent, STEP);
        return highest > oldPercent ? highest : 0;
    }

    public static boolean isMilestoneThreshold(int threshold)
    {
        return threshold >= STEP && threshold % STEP == 0;
    }
}
