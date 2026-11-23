package com.mmm.ui;

import com.mmm.tracker.CollectionProgress;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.junit.jupiter.api.Assertions.*;

class ProgressShareDataTest
{
    @Test void sessionPreservesIndependentActiveAndWallTimesAndMetricOrder()
    {
        var data = ProgressShareData.session(1234567, 3661999, 90061000, 120000, 987654);
        assertEquals("1,234,567", data.amount());
        assertEquals(List.of(
                new ProgressShareData.Metric("Session IGT", "01:01:01"),
                new ProgressShareData.Metric("Session Time", "25:01:01"),
                new ProgressShareData.Metric("Average / hour", "120,000"),
                new ProgressShareData.Metric("Best Hour", "987,654")), data.metrics());
    }

    @Test void negativeAndSubsecondDurationsNeverProduceNegativeTime()
    {
        var data = ProgressShareData.session(0, -60001, 999, 0, 0);
        assertEquals("0", data.amount());
        assertEquals("00:00:00", data.metrics().get(0).value());
        assertEquals("00:00:00", data.metrics().get(1).value());
    }

    @Test void constructorSnapshotsMetrics()
    {
        var metric = new ProgressShareData.Metric("One", "1");
        var input = new ArrayList<>(List.of(metric));
        var data = new ProgressShareData("10", input);
        input.clear();
        assertEquals(List.of(metric), data.metrics());
        assertThrows(UnsupportedOperationException.class, () -> data.metrics().clear());
    }

    @Test void captionContainsOnlySuppliedPublicFieldsAndOmitsMissingProfile()
    {
        var data = new ProgressShareData("1,234", List.of(new ProgressShareData.Metric("Elapsed", "01:02:03")));
        assertEquals("Miner / Session Summary\nMy world\n1,234 blocks mined\nElapsed: 01:02:03",
                data.caption("Miner", "Session Summary", "My world", ""));
        assertEquals("Miner / Summary\nWorld\n1,234 blocks mined\nElapsed: 01:02:03\nhttps://example.test/player/miner",
                data.caption("Miner", "Summary", "World", "https://example.test/player/miner"));
    }

    @Test void collectionFormattingUsesSelectedRulesWithoutHardCodedThresholds()
    {
        String id = "minecraft:stone";
        var milestones = CollectionProgress.milestones(id);
        long cap = milestones.get(milestones.size() - 1);
        var numbers = NumberFormat.getIntegerInstance(Locale.US);
        var initial = ProgressShareData.collection(id, 0);
        assertEquals("0", initial.amount());
        assertEquals(new ProgressShareData.Metric("Milestones completed", "0 / " + milestones.size()), initial.metrics().get(0));
        assertEquals(new ProgressShareData.Metric("Collection cap", numbers.format(cap)), initial.metrics().get(1));
        assertEquals(new ProgressShareData.Metric("Next milestone", numbers.format(milestones.get(0))), initial.metrics().get(2));
        var complete = ProgressShareData.collection(id, cap + 123);
        assertEquals(numbers.format(cap + 123), complete.amount());
        assertEquals(milestones.size() + " / " + milestones.size(), complete.metrics().get(0).value());
        assertEquals("Completed", complete.metrics().get(2).value());
    }

    @Test void largeCountsRetainLongPrecision()
    {
        var data = ProgressShareData.session(Long.MAX_VALUE, 0, 0, 2147483648L, 4294967296L);
        assertEquals("9,223,372,036,854,775,807", data.amount());
        assertEquals("2,147,483,648", data.metrics().get(2).value());
        assertEquals("4,294,967,296", data.metrics().get(3).value());
    }

    @Test
    @ResourceLock("java.util.Locale.default")
    void formattingIsIndependentOfDefaultLocale()
    {
        Locale previous = Locale.getDefault();
        try
        {
            Locale.setDefault(Locale.GERMANY);
            var data = ProgressShareData.session(1234567, 61000, 61000, 2345, 6789);
            assertEquals("1,234,567", data.amount());
            assertEquals("00:01:01", data.metrics().get(0).value());
            assertEquals("2,345", data.metrics().get(2).value());
        }
        finally { Locale.setDefault(previous); }
    }
}
