package com.mmm.legacy.achievement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoalMilestonesTest {
    @Test void baseMilestonesEachHaveTheirOwnSound() {
        GoalMilestones state = new GoalMilestones();
        for (int milestone : new int[]{25, 50, 75, 100}) {
            var result = state.advance(milestone - 1, milestone, 100, true);
            assertEquals(milestone, result.milestone());
            assertEquals(milestone, result.soundMilestone());
            assertEquals(0, state.advance(milestone - 1, milestone, 100, true).milestone());
        }
    }

    @Test void largeJumpSelectsHighestNoticeAndHighestBaseSound() {
        GoalMilestones state = new GoalMilestones();
        assertEquals(new GoalMilestones.Result(150, 100), state.advance(0, 151, 100, true));
        assertEquals(new GoalMilestones.Result(175, 0), state.advance(151, 175, 100, true));
        assertEquals(new GoalMilestones.Result(200, 0), state.advance(175, 200, 100, true));
    }

    @Test void soundToggleDoesNotSuppressMilestonesOrReplayPastSound() {
        GoalMilestones state = new GoalMilestones();
        assertEquals(new GoalMilestones.Result(25, 0), state.advance(0, 25, 100, false));
        assertEquals(new GoalMilestones.Result(0, 0), state.advance(25, 26, 100, true));
        assertEquals(new GoalMilestones.Result(50, 50), state.advance(26, 50, 100, true));
    }

    @Test void targetChangesAndDailyResetsStartFreshDeliveryState() {
        GoalMilestones state = new GoalMilestones();
        state.advance(0, 100, 100, true);
        assertEquals(new GoalMilestones.Result(25, 25), state.advance(0, 25, 100, true));
        assertEquals(new GoalMilestones.Result(25, 25), state.advance(0, 50, 200, true));
        state.clear();
        assertEquals(new GoalMilestones.Result(25, 25), state.advance(0, 50, 200, true));
    }

    @Test void invalidAndUnchangedProgressDoesNotNotify() {
        GoalMilestones state = new GoalMilestones();
        assertEquals(new GoalMilestones.Result(0, 0), state.advance(0, 100, 0, true));
        assertEquals(new GoalMilestones.Result(0, 0), state.advance(100, 100, 100, true));
    }

    @Test void xpBarUsesVanillaCropAndClampsOverGoal() {
        assertEquals(0, LegacyGoalHud.filledWidth(0, 100));
        assertEquals(1, LegacyGoalHud.filledWidth(1, 1_000_000));
        assertEquals(91, LegacyGoalHud.filledWidth(50, 100));
        assertEquals(182, LegacyGoalHud.filledWidth(100, 100));
        assertEquals(182, LegacyGoalHud.filledWidth(Long.MAX_VALUE, 1));
        assertEquals(0, LegacyGoalHud.filledWidth(100, 0));
    }

    @Test void texturePreservesAlphaAndBrightnessWithoutGreenTint() {
        int[] source = {0x00000000, 0xFF00FF00, 0x80808080, 0xFF404040};
        int[] destination = new int[source.length];
        GoalProgressTexture.recolor(source, destination, 0xFFCC2244);
        assertArrayEquals(new int[]{0, 0xFFCC2244, 0x80661122, 0xFF330911}, destination);
    }
}
