package com.mmm.advancement;

import java.util.Map;

/** Portable badge identities; rendering and resource loading belong to the client adapter. */
public final class AchievementBadgeCatalog
{
    public record Badge(String texturePath, int sourceSize) {}

    private static final Map<String, Badge> BADGES = Map.of(
            "first_time", badge("session_hours_i", 1254),
            "entry_level", badge("session_hours_ii", 1254),
            "veteran", badge("session_hours_iii", 1254),
            "no_life", badge("session_hours_iv", 1254),
            "part_of_the_mod", badge("session_hours_v", 1254),
            "endurance_i", badge("endurance_i", 128),
            "endurance_ii", badge("endurance_ii", 128),
            "endurance_iii", badge("endurance_iii", 128),
            "endurance_iv", badge("endurance_iv", 128),
            "endurance_v", badge("endurance_v", 128));

    private AchievementBadgeCatalog() {}

    private static Badge badge(String name, int sourceSize)
    {
        return new Badge("textures/gui/advancements/" + name + ".png", sourceSize);
    }

    public static Badge forDefinition(MmmAdvancementDefinition definition)
    {
        return definition == null ? null : BADGES.get(definition.id());
    }

    public static Badge forAdvancement(String namespace, String path)
    {
        return "mmm".equals(namespace) && path != null ? BADGES.get(path) : null;
    }
}
