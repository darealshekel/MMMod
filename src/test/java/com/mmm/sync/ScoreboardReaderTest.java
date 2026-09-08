package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import com.mmm.compat.ScoreboardCompat.Entry;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

class ScoreboardReaderTest
{
    @Test
    void cleanupMatchesOriginalIncludingFormattingAndWhitespace()
    {
        for (String text : List.of("Player_1", "\u00a7aPlayer", "  Total\t Digs ", "a\u00a0b", "\u00a7x", "a\nb", "", "\u00a7\nb"))
        {
            String expected = text.replaceAll("\u00a7.", "").replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
            assertEquals(expected, ScoreboardReader.cleanup(text));
        }
        assertEquals("", ScoreboardReader.cleanup(null));
        String name = new String("Player_1");
        assertSame(name, ScoreboardReader.cleanup(name));
    }

    @Test
    void reusesUnchangedRowsButShowsScoreAndDisplayChangesImmediately()
    {
        var cache = new ScoreboardReader.ObjectiveCache();
        var first = cache.read(List.of(entry("One", 20, "One"), entry("Two", 10, "Two")));
        assertSame(first, cache.read(List.of(entry("One", 20, "One"), entry("Two", 10, "Two"))));
        var changed = cache.read(List.of(entry("One", 20, "One"), entry("Two", 30, "New label")));
        assertEquals("Two", changed.get(0).owner());
        assertEquals(30, changed.get(0).scoreValue());
        assertEquals("New label", changed.get(0).cleaned());
        assertSame(first.get(0), changed.get(1));
        assertEquals(1, cache.read(List.of(entry("One", 20, "One"))).size());
        assertTrue(cache.read(List.of()).isEmpty());
        assertEquals(1, cache.read(List.of(entry("New", 5, "New"))).size());
    }

    private static Entry entry(String name, int value, String display)
    {
        return new Entry(name, Text.literal(display), value);
    }
}
