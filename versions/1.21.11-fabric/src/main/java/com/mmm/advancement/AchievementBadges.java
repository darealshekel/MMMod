package com.mmm.advancement;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Presentation-only adapter; advancement criteria and item icons remain unchanged. */
public final class AchievementBadges
{
    public record Badge(Identifier texture, int sourceSize) {}

    private static final Map<AchievementBadgeCatalog.Badge, Badge> BADGES;

    static
    {
        var badges = new HashMap<AchievementBadgeCatalog.Badge, Badge>();
        for (var definition : MmmAdvancementDefinition.ORDERED)
        {
            var badge = AchievementBadgeCatalog.forDefinition(definition);
            if (badge != null)
                badges.put(badge, new Badge(Identifier.of("mmm", badge.texturePath()), badge.sourceSize()));
        }
        BADGES = Map.copyOf(badges);
    }

    private AchievementBadges() {}

    public static Badge forDefinition(MmmAdvancementDefinition definition)
    {
        return resolve(AchievementBadgeCatalog.forDefinition(definition));
    }

    public static Badge forAdvancement(Identifier id)
    {
        return id == null ? null : resolve(AchievementBadgeCatalog.forAdvancement(id.getNamespace(), id.getPath()));
    }

    private static Badge resolve(AchievementBadgeCatalog.Badge badge)
    {
        return badge == null ? null : BADGES.get(badge);
    }

    public static void draw(DrawContext context, Badge badge, int x, int y, int size)
    {
        // Sample the whole cached texture, not a destination-sized region of the original PNG.
        int source = badge.sourceSize();
        context.drawTexture(RenderPipelines.GUI_TEXTURED, badge.texture(), x, y, 0, 0,
                size, size, source, source, source, source);
    }
}
