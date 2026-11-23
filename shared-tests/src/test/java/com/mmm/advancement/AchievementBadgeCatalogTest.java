package com.mmm.advancement;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AchievementBadgeCatalogTest
{
    @Test void allTenBadgeIdsAreExplicitAndStable()
    {
        var ids = List.of("first_time", "entry_level", "veteran", "no_life", "part_of_the_mod",
                "endurance_i", "endurance_ii", "endurance_iii", "endurance_iv", "endurance_v");
        var textures = List.of("session_hours_i", "session_hours_ii", "session_hours_iii",
                "session_hours_iv", "session_hours_v", "endurance_i", "endurance_ii",
                "endurance_iii", "endurance_iv", "endurance_v");
        for (int index = 0; index < ids.size(); index++)
        {
            var badge = AchievementBadgeCatalog.forAdvancement("mmm", ids.get(index));
            assertNotNull(badge, ids.get(index));
            assertEquals("textures/gui/advancements/" + textures.get(index) + ".png", badge.texturePath());
            assertEquals(index < 5 ? 1254 : 128, badge.sourceSize());
        }
        long mapped = 0;
        for (var definition : MmmAdvancementDefinition.ORDERED)
        {
            var badge = AchievementBadgeCatalog.forDefinition(definition);
            assertSame(badge, AchievementBadgeCatalog.forAdvancement("mmm", definition.id()));
            assertEquals(ids.contains(definition.id()), badge != null, definition.id());
            if (badge != null) mapped++;
        }
        assertEquals(10, mapped);
    }

    @Test void foreignNamespacesCategoriesAndNullsDoNotSelectBadges()
    {
        assertNull(AchievementBadgeCatalog.forDefinition(null));
        assertNull(AchievementBadgeCatalog.forAdvancement(null, "first_time"));
        assertNull(AchievementBadgeCatalog.forAdvancement("mmm", null));
        assertNull(AchievementBadgeCatalog.forAdvancement("mmm", "category/session_hours"));
        assertNull(AchievementBadgeCatalog.forAdvancement("mmm", "category/endurance"));
        assertNull(AchievementBadgeCatalog.forAdvancement("mmm", "root"));
        assertNull(AchievementBadgeCatalog.forAdvancement("mmm", "session_hours_i"));
        for (var definition : MmmAdvancementDefinition.ORDERED)
        {
            assertNull(AchievementBadgeCatalog.forAdvancement("minecraft", definition.id()));
            assertNull(AchievementBadgeCatalog.forAdvancement("other_mod", definition.id()));
        }
    }
}
