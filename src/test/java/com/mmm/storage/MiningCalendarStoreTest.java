package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MiningCalendarStoreTest
{
    @Test
    void sumsOnlyDaysInsideCurrentWednesdayWeek()
    {
        Map<String, Long> days = new LinkedHashMap<>();
        days.put("21-07-2026", 95_023L);
        days.put("22-07-2026", 351_716L);
        days.put("23-07-2026", 5_011L);
        days.put("29-07-2026", 99_999L);

        long total = MiningCalendarStore.sumDaysWithin(
                days,
                LocalDate.of(2026, 7, 22),
                LocalDate.of(2026, 7, 23));

        assertEquals(356_727L, total);
    }

    @Test
    void ignoresMalformedNegativeAndOutOfRangeValues()
    {
        Map<String, Long> days = new LinkedHashMap<>();
        days.put("22-07-2026", 12L);
        days.put("23-07-2026", -20L);
        days.put("not-a-date", 500L);
        days.put("24-07-2026", 40L);

        long total = MiningCalendarStore.sumDaysWithin(
                days,
                LocalDate.of(2026, 7, 22),
                LocalDate.of(2026, 7, 23));

        assertEquals(12L, total);
    }

    @Test
    void findsLongestConsecutiveMiningStreak()
    {
        Map<String, Long> days = new LinkedHashMap<>();
        days.put("01-08-2026", 20L);
        days.put("02-08-2026", 30L);
        days.put("03-08-2026", 0L);
        days.put("04-08-2026", 10L);
        days.put("05-08-2026", 10L);
        days.put("06-08-2026", 10L);
        days.put("bad-date", 999L);

        assertEquals(3, MiningCalendarStore.longestMiningStreakDays(days));
    }

    @Test
    void qualifiedStreakIgnoresDaysBelowTenThousandBlocks()
    {
        Map<String, Long> days = new LinkedHashMap<>();
        days.put("01-08-2026", 20_000L);
        days.put("02-08-2026", 11_000L);
        days.put("03-08-2026", 167L);
        days.put("04-08-2026", 10_000L);

        assertEquals(2, MiningCalendarStore.longestMiningStreakDays(days, 10_000L));
    }
}
