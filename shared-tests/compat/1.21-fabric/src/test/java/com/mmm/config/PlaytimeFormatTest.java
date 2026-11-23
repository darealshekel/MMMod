package com.mmm.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaytimeFormatTest
{
    @Test void formatsRequestedUnitsWithoutWrappingTotalHours()
    {
        long time = (53 * 3600L + 7 * 60L + 9) * 1000L;
        assertEquals("2d 05h", PlaytimeFormat.DAYS_HOURS.format(time));
        assertEquals("53h 07m", PlaytimeFormat.HOURS_MINUTES.format(time));
        assertEquals("53h 07m 09s", PlaytimeFormat.HOURS_MINUTES_SECONDS.format(time));
    }

    @Test void formatsZeroAndBoundaries()
    {
        assertEquals("0h 00m 00s", PlaytimeFormat.HOURS_MINUTES_SECONDS.format(-1));
        assertEquals("1d 00h", PlaytimeFormat.DAYS_HOURS.format(86_400_000));
        assertEquals("1h 00m", PlaytimeFormat.HOURS_MINUTES.format(3_600_000));
    }

    @Test void settingsCycleAndReload()
    {
        for (PlaytimeFormat value : PlaytimeFormat.values())
        {
            assertEquals(value, PlaytimeFormat.HOURS_MINUTES.fromString(value.getStringValue()));
            assertEquals(value, value.cycle(true).cycle(false));
        }
        assertEquals(PlaytimeFormat.HOURS_MINUTES, PlaytimeFormat.DAYS_HOURS.fromString("invalid"));
        assertTrue(Configs.Generic.OPTIONS.contains(Configs.Generic.PLAYTIME_FORMAT));
        assertTrue(Configs.Generic.OPTIONS.contains(Configs.Generic.HUD_WORLD_PLAYTIME_VISIBLE));
        assertTrue(Configs.Generic.OPTIONS.contains(Configs.Generic.HUD_MOD_PLAYTIME_VISIBLE));
        assertTrue(Configs.Generic.OPTIONS.contains(Configs.Generic.HUD_ALL_WORLDS_PLAYTIME_VISIBLE));
        assertTrue(Configs.Generic.OPTIONS.contains(Configs.Generic.WORLD_PLAYTIME_ABOVE_HOTBAR));
    }
}
