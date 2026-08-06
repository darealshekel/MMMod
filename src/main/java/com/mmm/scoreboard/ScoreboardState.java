package com.mmm.scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScoreboardState
{
    private static final List<Snapshot> SNAPSHOTS = new ArrayList<>();
    private static int pageOffset;

    private ScoreboardState()
    {
    }

    public static synchronized int getPageOffset()
    {
        return pageOffset;
    }

    public static synchronized void resetPage()
    {
        pageOffset = 0;
    }

    public static synchronized boolean pageUp(int pageSize)
    {
        int next = Math.max(0, pageOffset - Math.max(1, pageSize));
        boolean changed = next != pageOffset;
        pageOffset = next;
        return changed;
    }

    public static synchronized boolean pageDown(int entryCount, int pageSize)
    {
        int size = Math.max(1, pageSize);
        if (pageOffset + size >= Math.max(0, entryCount))
        {
            return false;
        }
        pageOffset += size;
        return true;
    }

    public static synchronized void clampPage(int entryCount, int pageSize)
    {
        int size = Math.max(1, pageSize);
        int lastPage = entryCount <= 0 ? 0 : ((entryCount - 1) / size) * size;
        pageOffset = Math.max(0, Math.min(pageOffset, lastPage));
    }

    public static synchronized void addSnapshot(Snapshot snapshot)
    {
        SNAPSHOTS.add(snapshot);
    }

    public static synchronized List<Snapshot> getSnapshots()
    {
        return Collections.unmodifiableList(new ArrayList<>(SNAPSHOTS));
    }

    public static synchronized boolean moveSnapshot(int index, int direction)
    {
        int target = index + direction;
        if (index < 0 || index >= SNAPSHOTS.size() || target < 0 || target >= SNAPSHOTS.size())
        {
            return false;
        }
        Collections.swap(SNAPSHOTS, index, target);
        return true;
    }

    public static synchronized boolean removeSnapshot(int index)
    {
        if (index < 0 || index >= SNAPSHOTS.size())
        {
            return false;
        }
        SNAPSHOTS.remove(index);
        return true;
    }

    public record Snapshot(String objectiveName, String displayName, long capturedAtMs, List<SnapshotRow> rows)
    {
        public Snapshot
        {
            rows = List.copyOf(rows);
        }
    }

    public record SnapshotRow(String name, int score)
    {
    }
}
