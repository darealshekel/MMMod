package com.mmm.tweak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslucentLavaRendererTest
{
    @Test
    void convertsConfiguredPercentToAlpha()
    {
        assertEquals(115, TranslucentLavaRenderer.alphaFromPercent(45));
    }

    @Test
    void clampsOpacityToSupportedRange()
    {
        assertEquals(26, TranslucentLavaRenderer.alphaFromPercent(-1));
        assertEquals(255, TranslucentLavaRenderer.alphaFromPercent(150));
    }

    @Test
    void generatesManagedPackAtConfiguredOpacity(@TempDir Path temporaryDirectory) throws Exception
    {
        Path packDirectory = temporaryDirectory.resolve("MMM-Translucent-Lava");

        assertTrue(TranslucentLavaRenderer.ensureGeneratedPack(packDirectory, 45));
        assertFalse(TranslucentLavaRenderer.ensureGeneratedPack(packDirectory, 45));
        assertTrue(Files.readString(packDirectory.resolve("pack.mcmeta")).contains("\"pack_format\": 34"));

        BufferedImage image = ImageIO.read(packDirectory
                .resolve("assets/minecraft/textures/block/lava_still.png")
                .toFile());
        assertEquals(115, firstVisibleAlpha(image));
    }

    @Test
    void regeneratesManagedTexturesWhenOpacityChanges(@TempDir Path temporaryDirectory) throws Exception
    {
        Path packDirectory = temporaryDirectory.resolve("MMM-Translucent-Lava");

        assertTrue(TranslucentLavaRenderer.ensureGeneratedPack(packDirectory, 45));
        assertTrue(TranslucentLavaRenderer.ensureGeneratedPack(packDirectory, 20));

        BufferedImage image = ImageIO.read(packDirectory
                .resolve("assets/minecraft/textures/block/lava_still.png")
                .toFile());
        assertEquals(51, firstVisibleAlpha(image));
    }

    private static int firstVisibleAlpha(BufferedImage image)
    {
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha != 0)
                {
                    return alpha;
                }
            }
        }
        return 0;
    }
}
