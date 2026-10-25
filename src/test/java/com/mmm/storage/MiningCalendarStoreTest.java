package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MiningCalendarStoreTest
{
    @Test
    @SuppressWarnings("unchecked")
    void rolloverKeepsNewestFourHundredDaysAndRemovesSyncedOldDays() throws Exception
    {
        var daysField = MiningCalendarStore.class.getDeclaredField("DAYS");
        var syncedField = MiningCalendarStore.class.getDeclaredField("SYNCED_DAYS");
        daysField.setAccessible(true);
        syncedField.setAccessible(true);
        Map<String, Long> days = (Map<String, Long>) daysField.get(null);
        Map<String, Long> synced = (Map<String, Long>) syncedField.get(null);
        Map<String, Long> originalDays = new LinkedHashMap<>(days);
        Map<String, Long> originalSynced = new LinkedHashMap<>(synced);
        try
        {
            days.clear();
            synced.clear();
            var format = java.time.format.DateTimeFormatter.ofPattern("dd-MM-uuuu");
            LocalDate start = LocalDate.of(2025, 12, 1);
            for (int index = 400; index >= 0; index--)
            {
                String day = start.plusDays(index).format(format);
                days.put(day, 10_000L);
                synced.put(day, 9_000L);
            }
            var trim = MiningCalendarStore.class.getDeclaredMethod("trimOldDays");
            trim.setAccessible(true);
            trim.invoke(null);
            assertEquals(400, days.size());
            assertEquals(400, synced.size());
            org.junit.jupiter.api.Assertions.assertFalse(days.containsKey(start.format(format)));
            org.junit.jupiter.api.Assertions.assertTrue(days.containsKey(start.plusDays(400).format(format)));
            var sorted = MiningCalendarStore.class.getDeclaredMethod("sortedDayKeys");
            sorted.setAccessible(true);
            var keys = (java.util.List<String>) sorted.invoke(null);
            assertEquals(start.plusDays(1).format(format), keys.getFirst());
            assertEquals(start.plusDays(400).format(format), keys.getLast());
        }
        finally
        {
            days.clear();
            days.putAll(originalDays);
            synced.clear();
            synced.putAll(originalSynced);
        }
    }

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
