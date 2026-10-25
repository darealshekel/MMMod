package com.mmm.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Locale;

public final class PeriodKeys
{
    public enum Relation
    {
        MISSING,
        INVALID,
        OLDER,
        CURRENT,
        FUTURE
    }
    public static final ZoneId UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter DISPLAY_KEY = new DateTimeFormatterBuilder()
            .appendPattern("dd-MM-")
            .appendValue(ChronoField.YEAR, 4)
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter LEGACY_DATE_KEY = DateTimeFormatter.ISO_LOCAL_DATE;

    private PeriodKeys()
    {
    }

    public static String currentDailyKey(long now)
    {
        return format(LocalDate.ofInstant(Instant.ofEpochMilli(now), UTC));
    }

    public static String currentWeeklyKey(long now)
    {
        return weeklyKeyForDate(LocalDate.ofInstant(Instant.ofEpochMilli(now), UTC));
    }

    public static String normalizeDailyKey(String value, long now)
    {
        LocalDate parsed = parseDateKey(value);
        return parsed == null ? currentDailyKey(now) : format(parsed);
    }

    public static String normalizeWeeklyKey(String value, long now)
    {
        String raw = clean(value);
        if (raw.isBlank())
        {
            return currentWeeklyKey(now);
        }
        if (raw.equals(legacyIsoWeekKey(now)))
        {
            return currentWeeklyKey(now);
        }

        LocalDate parsed = parseDateKey(raw);
        if (parsed != null)
        {
            return weeklyKeyForDate(parsed);
        }

        parsed = parseLegacyIsoWeek(raw);
        return parsed == null ? currentWeeklyKey(now) : format(parsed);
    }

    public static boolean isCurrentDailyKey(String value, long now)
    {
        return dailyRelation(value, now) == Relation.CURRENT;
    }

    public static boolean isCurrentWeeklyKey(String value, long now)
    {
        return weeklyRelation(value, now) == Relation.CURRENT;
    }

    public static Relation dailyRelation(String value, long now)
    {
        String raw = clean(value);
        if (raw.isBlank())
        {
            return Relation.MISSING;
        }

        LocalDate parsed = parseDateKey(raw);
        if (parsed == null)
        {
            return Relation.INVALID;
        }

        LocalDate current = LocalDate.ofInstant(Instant.ofEpochMilli(now), UTC);
        return compare(parsed, current);
    }

    public static Relation weeklyRelation(String value, long now)
    {
        String raw = clean(value);
        if (raw.isBlank())
        {
            return Relation.MISSING;
        }

        LocalDate parsed = parseDateKey(raw);
        if (parsed == null)
        {
            parsed = parseLegacyIsoWeek(raw);
        }
        if (parsed == null)
        {
            return Relation.INVALID;
        }

        LocalDate storedStart = weeklyStartForDate(parsed);
        LocalDate currentStart = weeklyStartForDate(LocalDate.ofInstant(Instant.ofEpochMilli(now), UTC));
        return compare(storedStart, currentStart);
    }

    public static long nextWeeklyBoundaryMs(long now)
    {
        LocalDate current = LocalDate.ofInstant(Instant.ofEpochMilli(now), UTC);
        return weeklyStartForDate(current).plusDays(7L).atStartOfDay(UTC).toInstant().toEpochMilli();
    }

    public static long nextDailyBoundaryMs(long now)
    {
        LocalDate current = LocalDate.ofInstant(Instant.ofEpochMilli(now), UTC);
        return current.plusDays(1L).atStartOfDay(UTC).toInstant().toEpochMilli();
    }

    public static long currentDailyStartMs(long now)
    {
        LocalDate current = LocalDate.ofInstant(Instant.ofEpochMilli(now), UTC);
        return current.atStartOfDay(UTC).toInstant().toEpochMilli();
    }
    public static long currentWeeklyStartMs(long now)
    {
        LocalDate current = LocalDate.ofInstant(Instant.ofEpochMilli(now), UTC);
        return weeklyStartForDate(current).atStartOfDay(UTC).toInstant().toEpochMilli();
    }

    public static String legacyIsoWeekKey(long now)
    {
        LocalDate date = LocalDate.ofInstant(Instant.ofEpochMilli(now), UTC);
        WeekFields weekFields = WeekFields.ISO;
        int year = date.get(weekFields.weekBasedYear());
        int week = date.get(weekFields.weekOfWeekBasedYear());
        return String.format(Locale.ROOT, "%04d-W%02d", year, week);
    }

    private static String weeklyKeyForDate(LocalDate date)
    {
        return format(weeklyStartForDate(date));
    }

    private static LocalDate weeklyStartForDate(LocalDate date)
    {
        int diff = (date.getDayOfWeek().getValue() - DayOfWeek.WEDNESDAY.getValue() + 7) % 7;
        return date.minusDays(diff);
    }

    private static String format(LocalDate date)
    {
        return DISPLAY_KEY.format(date);
    }

    private static LocalDate parseDateKey(String value)
    {
        String raw = clean(value);
        if (raw.isBlank())
        {
            return null;
        }

        try
        {
            return LocalDate.parse(raw, DISPLAY_KEY);
        }
        catch (Exception ignored)
        {
        }

        try
        {
            return LocalDate.parse(raw, LEGACY_DATE_KEY);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static LocalDate parseLegacyIsoWeek(String value)
    {
        String raw = clean(value);
        if (raw.matches("\\d{4}-W\\d{2}") == false)
        {
            return null;
        }

        try
        {
            int year = Integer.parseInt(raw.substring(0, 4));
            int week = Integer.parseInt(raw.substring(6, 8));
            if (week < 1 || week > 53)
            {
                return null;
            }
            LocalDate jan4 = LocalDate.of(year, 1, 4);
            LocalDate weekOneMonday = jan4.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return weekOneMonday.plusWeeks(week - 1L).plusDays(2);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.trim();
    }

    private static Relation compare(LocalDate stored, LocalDate current)
    {
        if (stored.isBefore(current))
        {
            return Relation.OLDER;
        }
        if (stored.isAfter(current))
        {
            return Relation.FUTURE;
        }
        return Relation.CURRENT;
    }
}
