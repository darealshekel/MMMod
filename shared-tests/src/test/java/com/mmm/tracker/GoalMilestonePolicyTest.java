package com.mmm.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GoalMilestonePolicyTest
{
    @Test
    void detectsBaseAndExceededMilestones()
    {
        assertEquals(25, GoalMilestonePolicy.highestCrossed(24, 25));
        assertEquals(125, GoalMilestonePolicy.highestCrossed(100, 125));
        assertEquals(150, GoalMilestonePolicy.highestCrossed(124, 151));
        assertEquals(0, GoalMilestonePolicy.highestCrossed(125, 149));
    }

    @Test
    void calculatesUncappedProgressSafely()
    {
        assertEquals(125, GoalMilestonePolicy.percent(437_500L, 350_000L));
        assertEquals(0, GoalMilestonePolicy.percent(1L, 0L));
    }

    @Test
    void validatesOnlyTwentyFivePercentSteps()
    {
        assertTrue(GoalMilestonePolicy.isMilestoneThreshold(25));
        assertTrue(GoalMilestonePolicy.isMilestoneThreshold(125));
        assertFalse(GoalMilestonePolicy.isMilestoneThreshold(130));
    }
}
