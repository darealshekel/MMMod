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
}