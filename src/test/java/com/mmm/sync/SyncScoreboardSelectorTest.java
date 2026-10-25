package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SyncScoreboardSelectorTest
{
    @Test
    void acceptsMiningTotals()
    {
        assertTrue(SyncScoreboardSelector.isEligibleContext("total_blocks Total Blocks Mined"));
        assertTrue(SyncScoreboardSelector.isEligibleContext("digs Blocks Dug"));
    }

    @Test
    void rejectsProjectAndUnrelatedObjectives()
    {
        assertFalse(SyncScoreboardSelector.isEligibleContext("project_blocks Project Blocks Mined"));
        assertFalse(SyncScoreboardSelector.isEligibleContext("daily_digs Daily Digs"));
        assertFalse(SyncScoreboardSelector.isEligibleContext("sprint_distance Sprint Distance"));
    }
}
