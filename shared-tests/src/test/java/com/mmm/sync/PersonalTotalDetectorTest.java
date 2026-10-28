package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PersonalTotalDetectorTest
{
    @Test
    void combinesAllFiveToolUseCountersIntoOneWorldTotal()
    {
        assertEquals(
                5_000_500L,
                PersonalTotalDetector.combineToolUsageTotals(4_966_429L, 20_000L, 13_571L, 400L, 100L));
    }

    @Test
    void ignoresInvalidNegativeToolValues()
    {
        assertEquals(42L, PersonalTotalDetector.combineToolUsageTotals(42L, -1L, -20L, -30L, -40L));
    }

    @Test
    void describesOnlyTheToolObjectivesThatWereFound()
    {
        assertEquals(
                "Combined Tool Uses: Pickaxe Uses + Shovel Uses + Axe Uses + Hoe Uses + Shears Uses",
                PersonalTotalDetector.toolUsageObjectiveTitle(4_966_429L, 20_000L, 13_571L, 400L, 100L));
        assertEquals(
                "Combined Tool Uses: Pickaxe Uses",
                PersonalTotalDetector.toolUsageObjectiveTitle(4_966_429L, 0L, 0L, 0L, 0L));
    }

    @Test
    void ignoresWebsiteTierTagNumbersInRenderedPlayerNames()
    {
        assertEquals(0L, PersonalTotalDetector.renderedMiningTotal("18.5M | 5hekel"));
        assertEquals(1_234_567L, PersonalTotalDetector.renderedMiningTotal("Your digs: 1,234,567"));
    }
}
