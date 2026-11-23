package com.mmm.ui;

import com.mmm.tracker.CollectionProgress;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Version-independent content of the private, cropped share panel. */
public record ProgressShareData(String amount, List<Metric> metrics)
{
    public record Metric(String label, String value) {}

    public ProgressShareData
    {
        metrics = List.copyOf(metrics);
    }

    public static ProgressShareData session(long blocks, long activeMs, long wallMs, long average, long bestHour)
    {
        return new ProgressShareData(number(blocks), List.of(new Metric("Session IGT", clock(activeMs)),
                new Metric("Session Time", clock(wallMs)), new Metric("Average / hour", number(average)),
                new Metric("Best Hour", number(bestHour))));
    }

    public static ProgressShareData collection(String id, long count)
    {
        var goals = CollectionProgress.milestones(id);
        long next = CollectionProgress.next(id, count);
        return new ProgressShareData(number(count), List.of(
                new Metric("Milestones completed", CollectionProgress.completed(id, count) + " / " + goals.size()),
                new Metric("Collection cap", number(goals.get(goals.size() - 1))),
                new Metric("Next milestone", next == 0 ? "Completed" : number(next))));
    }

    public String caption(String player, String title, String source, String profileUrl)
    {
        StringBuilder text = new StringBuilder(player + " / " + title + "\n" + source + "\n" + amount + " blocks mined");
        for (Metric metric : metrics) text.append('\n').append(metric.label()).append(": ").append(metric.value());
        if (!profileUrl.isEmpty()) text.append('\n').append(profileUrl);
        return text.toString();
    }

    private static String number(long value)
    {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private static String clock(long ms)
    {
        long seconds = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}
