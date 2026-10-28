package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreboardTextRulesTest
{
    private static final List<String> PERSONAL_MARKERS = List.of("your", "you", "player", "my", "me", "self");

    @Test
    void doesNotTreatMarkersInsideUsernamesAsPersonalRows()
    {
        assertFalse(ScoreboardTextRules.containsAnyStandaloneMarker("Timer_16K 100000", PERSONAL_MARKERS));
        assertFalse(ScoreboardTextRules.containsAnyStandaloneMarker("Young_Royal_ 90000", PERSONAL_MARKERS));
        assertFalse(ScoreboardTextRules.containsAnyStandaloneMarker("myrxxvy 80000", PERSONAL_MARKERS));
        assertTrue(ScoreboardTextRules.containsAnyStandaloneMarker("Your digs: 70000", PERSONAL_MARKERS));
    }

    @Test
    void scoreboardOwnerWinsOverDecorativeDisplayPrefixes()
    {
        assertEquals("WkeyAki", ScoreboardTextRules.extractUsername("WkeyAki", "[VIP] WkeyAki 123456"));
        assertEquals("WkeyAki", ScoreboardTextRules.extractUsername("#hidden", "WkeyAki 123456"));
    }

    @Test
    void recognizesToolUseObjectivesUsedForWorldTotals()
    {
        assertTrue(ScoreboardParser.isPickUsesObjective("minecraft.used:diamond_pickaxe Pick Uses"));
        assertTrue(ScoreboardParser.isShovelUsesObjective("shovel_uses Shovel Uses"));
        assertTrue(ScoreboardParser.isAxeUsesObjective("axe_uses Axe Uses"));
        assertTrue(ScoreboardParser.isHoeUsesObjective("minecraft.used:minecraft.diamond_hoe"));
        assertTrue(ScoreboardParser.isShearsUsesObjective("minecraft.used:minecraft.shears"));
        assertEquals(50, ScoreboardParser.objectivePriority("minecraft.used:minecraft.diamond_hoe"));
        assertEquals(50, ScoreboardParser.objectivePriority("minecraft.used:minecraft.shears"));
        assertFalse(ScoreboardParser.isAxeUsesObjective("pickaxe_uses Pickaxe Uses"));
    }
}
