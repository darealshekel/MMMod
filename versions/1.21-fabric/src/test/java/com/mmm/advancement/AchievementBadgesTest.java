package com.mmm.advancement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AchievementBadgesTest
{
    private static final List<MmmAdvancementDefinition> DEFINITIONS = List.of(
            MmmAdvancementDefinition.FIRST_TIME, MmmAdvancementDefinition.ENTRY_LEVEL,
            MmmAdvancementDefinition.VETERAN, MmmAdvancementDefinition.NO_LIFE,
            MmmAdvancementDefinition.PART_OF_THE_MOD);
    private static final List<String> TIERS = List.of("i", "ii", "iii", "iv", "v");
    private static final List<String> HASHES = List.of(
            "5a271dbf4a8aa6a4c7e4bfe6dd366dc0f4e5b0778a16b1388d9852da3a628a1f",
            "b3b6dd892865425c3f27589f3754e2f17e8c49b96a90f56b4ecd7d1e6f73445d",
            "e09449182ec084b7ebd9ce77ebb1329fe208101a25d1f2e044147636cfef6c32",
            "d1a3972c80e94c8b75c1867546c8457740b6fc302dbd8065133320d7c2dd2eab",
            "d1ed98489863bc6e452a3a5510aeb49325f8d155fa80daeed54080082c05aae0");

    @Test void suppliedTiersMapToTheSameCardAndAdvancementTexture()
    {
        for (int index = 0; index < DEFINITIONS.size(); index++)
        {
            var definition = DEFINITIONS.get(index);
            var expected = Identifier.of("mmm", "textures/gui/advancements/session_hours_" + TIERS.get(index) + ".png");
            var badge = AchievementBadges.forDefinition(definition);
            assertEquals(expected, badge.texture());
            assertSame(badge, AchievementBadges.forAdvancement(Identifier.of("mmm", definition.id())));
        }
        assertEquals(100, MmmAdvancementDefinition.ENTRY_LEVEL.threshold());
        assertEquals(500, MmmAdvancementDefinition.VETERAN.threshold());
        assertEquals(1000, MmmAdvancementDefinition.NO_LIFE.threshold());
        assertEquals(5000, MmmAdvancementDefinition.PART_OF_THE_MOD.threshold());
    }

    @Test void unrelatedAndVanillaAdvancementsKeepTheirExistingIcons()
    {
        assertNull(AchievementBadges.forDefinition(null));
        assertNull(AchievementBadges.forAdvancement(null));
        for (var definition : MmmAdvancementDefinition.ORDERED)
        {
            assertNull(AchievementBadges.forAdvancement(Identifier.of("minecraft", definition.id())));
            if (!DEFINITIONS.contains(definition)
                    && definition.kind() != MmmAdvancementDefinition.Kind.SESSION_ENDURANCE_HOURS)
                assertNull(AchievementBadges.forDefinition(definition));
        }
        assertNull(AchievementBadges.forAdvancement(Identifier.of("mmm", "category/session_hours")));
    }

    @Test void packagedPngsAreUnmodifiedOriginalsWithAlphaAndExpectedDimensions() throws Exception
    {
        for (int index = 0; index < DEFINITIONS.size(); index++)
        {
            var badge = AchievementBadges.forDefinition(DEFINITIONS.get(index));
            String path = "/assets/mmm/" + badge.texture().getPath();
            try (var stream = getClass().getResourceAsStream(path))
            {
                assertNotNull(stream, path);
                byte[] bytes = stream.readAllBytes();
                assertEquals(HASHES.get(index), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), path);
                var image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                assertEquals(badge.sourceSize(), image.getWidth());
                assertEquals(badge.sourceSize(), image.getHeight());
                assertTrue(image.getColorModel().hasAlpha());
                assertEquals(0, image.getRGB(0, 0) >>> 24);
                assertTrue((image.getRGB(image.getWidth() / 2, image.getHeight() / 2) >>> 24) > 0);
            }
        }
    }

    @Test void enduranceTiersUsePackagedTransparent128PixelBadges() throws Exception
    {
        var definitions = List.of(MmmAdvancementDefinition.ENDURANCE_I, MmmAdvancementDefinition.ENDURANCE_II,
                MmmAdvancementDefinition.ENDURANCE_III, MmmAdvancementDefinition.ENDURANCE_IV,
                MmmAdvancementDefinition.ENDURANCE_V);
        for (int index = 0; index < definitions.size(); index++)
        {
            var definition = definitions.get(index);
            var badge = AchievementBadges.forDefinition(definition);
            assertEquals("textures/gui/advancements/endurance_" + TIERS.get(index) + ".png", badge.texture().getPath());
            assertSame(badge, AchievementBadges.forAdvancement(Identifier.of("mmm", definition.id())));
            assertEquals(128, badge.sourceSize());
            try (var stream = getClass().getResourceAsStream("/assets/mmm/" + badge.texture().getPath()))
            {
                assertNotNull(stream, badge.texture().toString());
                var image = ImageIO.read(stream);
                assertEquals(128, image.getWidth());
                assertEquals(128, image.getHeight());
                assertEquals(0, image.getRGB(0, 0) >>> 24);
                assertEquals(255, image.getRGB(64, 64) >>> 24);
            }
        }
    }

    @Test void cardHasNoRedAccentAndWidgetReplacesBothTreeAndTooltipIcons() throws Exception
    {
        Path source = Path.of(System.getProperty("mmm.sources"));
        String renderer = Files.readString(source.resolve("com/mmm/advancement/AchievementCardRenderer.java"));
        assertFalse(renderer.contains("0xFFCE2626"));
        String widget = Files.readString(source.resolve("com/mmm/mixin/AdvancementWidgetMixin.java"));
        assertTrue(widget.contains("{\"renderWidgets\", \"drawTooltip\"}"));
        assertTrue(widget.contains("original.call(context, stack, x, y)"));
    }
}
