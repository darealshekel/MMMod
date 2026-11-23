package com.mmm.advancement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AchievementCardStateTest
{
    @Test void collectionsShareQueueButKeepDistinctTiersAndIcons()
    {
        var state = new AchievementCardState();
        state.offer(MmmAdvancementDefinition.DIG_MASTER);
        state.offerCollection("minecraft:stone", "Stone", 2500);
        state.offerCollection("minecraft:stone", "Localized stone", 2500);
        state.offerCollection("minecraft:stone", "Stone", 5000);
        state.offerCollection("minecraft:oak_log", "Oak Log", 2500);
        state.advance(10);
        assertEquals("Collection Milestone!", state.currentCard().heading());
        assertEquals("Stone - 2.5K", state.currentCard().title());
        assertEquals("minecraft:stone", state.currentCard().collectionId());
        assertEquals(2500, state.currentCard().milestone());
        assertEquals(0, state.visibility());
        state.advance(10);
        assertEquals(5000, state.currentCard().milestone());
        state.advance(10);
        assertEquals("minecraft:oak_log", state.currentCard().collectionId());
        state.clear();
        assertNull(state.currentCard());
    }

    @Test void groupedCollectionsCoalesceAndInvalidCardsAreIgnored()
    {
        var state = new AchievementCardState();
        state.offerCollection(null, "Stone", 2500);
        state.offerCollection("minecraft:stone", "", 2500);
        state.offerCollection("minecraft:stone", "Stone", 0);
        assertNull(state.currentCard());
        state.offerCollection("minecraft:red_terracotta", "Terracotta", 2500);
        state.offerCollection("minecraft:blue_terracotta", "Terracotta", 2500);
        assertEquals("minecraft:terracotta", state.currentCard().collectionId());
        state.advance(10);
        assertNull(state.currentCard());
    }

    @Test void slidesHoldsAndLeaves()
    {
        var state = new AchievementCardState();
        state.offer(MmmAdvancementDefinition.DIG_MASTER);
        assertEquals(0, state.visibility());
        state.advance(0.225);
        assertTrue(state.visibility() > 0 && state.visibility() < 1);
        state.advance(0.225);
        assertEquals(1, state.visibility());
        state.advance(4.0);
        assertEquals(1, state.visibility(), 0.00001);
        state.advance(0.225);
        assertTrue(state.visibility() > 0 && state.visibility() < 1);
        state.advance(0.226);
        assertNull(state.current());
    }

    @Test void queuesDistinctUnlocksAndStartsEachAtTheTop()
    {
        var state = new AchievementCardState();
        state.offer(MmmAdvancementDefinition.DIG_MASTER);
        state.offer(MmmAdvancementDefinition.DIG_MASTER);
        state.offer(MmmAdvancementDefinition.VETERAN);
        state.advance(10);
        assertEquals(MmmAdvancementDefinition.VETERAN, state.current());
        assertEquals(0, state.visibility());
        state.advance(10);
        assertNull(state.current());
    }

    @Test void clearingDropsPendingCardsAndRestartsAnimation()
    {
        var state = new AchievementCardState();
        state.offer(MmmAdvancementDefinition.VETERAN);
        state.advance(1);
        state.clear();
        assertNull(state.current());
        state.offer(MmmAdvancementDefinition.FIRST_TIME);
        assertEquals(0, state.visibility());
    }

    @Test void invalidInputsCannotCorruptAnimation()
    {
        var state = new AchievementCardState();
        state.offer(null);
        state.advance(10);
        state.offer(MmmAdvancementDefinition.FIRST_TIME);
        state.advance(Double.NaN);
        state.advance(Double.POSITIVE_INFINITY);
        state.advance(-10);
        assertEquals(0, state.visibility());
        assertEquals(MmmAdvancementDefinition.FIRST_TIME, state.current());
    }
}
