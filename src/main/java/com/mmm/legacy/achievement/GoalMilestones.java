package com.mmm.legacy.achievement;

import com.mmm.tracker.GoalMilestonePolicy;
import java.util.HashSet;
import java.util.Set;

/** Per-goal delivery state, independent from GUI and network side effects. */
public final class GoalMilestones {
    private final Set<Integer> announced = new HashSet<>();
    private final Set<Integer> sounds = new HashSet<>();
    private long lastTarget;
    private long lastProgress;

    public Result advance(long oldProgress, long current, long target, boolean soundEnabled) {
        if (target <= 0) return new Result(0, 0);
        if (lastTarget != target || current < lastProgress) clear();
        int oldPercent = GoalMilestonePolicy.percent(oldProgress, target);
        int newPercent = GoalMilestonePolicy.percent(current, target);
        int highest = GoalMilestonePolicy.highestCrossed(oldPercent, newPercent);
        int sound = 0;
        if (soundEnabled) for (int milestone : new int[]{25, 50, 75, 100})
            if (oldPercent < milestone && newPercent >= milestone && sounds.add(milestone)) sound = milestone;
        lastTarget = target;
        lastProgress = current;
        return new Result(highest > 0 && announced.add(highest) ? highest : 0, sound);
    }

    public void clear() {
        announced.clear();
        sounds.clear();
        lastTarget = 0;
        lastProgress = 0;
    }

    public record Result(int milestone, int soundMilestone) {}
}
