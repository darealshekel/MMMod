package com.mmm.tracker;

/** Fixed-size primitive ring buffer for per-tick mining counts. */
final class RollingMiningMetrics
{
    private final int[] counts;
    private int nextIndex;
    private int size;
    private long total;

    RollingMiningMetrics(int capacity)
    {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.counts = new int[capacity];
    }

    void addTick(int count)
    {
        int safeCount = Math.max(0, count);
        if (size == counts.length) total -= counts[nextIndex];
        else size++;
        counts[nextIndex] = safeCount;
        total += safeCount;
        nextIndex = (nextIndex + 1) % counts.length;
    }

    int size() { return size; }
    long total() { return total; }

    long sumLatest(int requestedTicks)
    {
        int ticks = Math.max(0, Math.min(requestedTicks, size));
        long sum = 0L;
        int index = nextIndex;
        for (int offset = 0; offset < ticks; offset++)
        {
            index = index == 0 ? counts.length - 1 : index - 1;
            sum += counts[index];
        }
        return sum;
    }

    void clear()
    {
        nextIndex = 0;
        size = 0;
        total = 0L;
    }
}
