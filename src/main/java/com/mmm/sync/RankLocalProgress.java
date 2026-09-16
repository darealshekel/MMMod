package com.mmm.sync;

/** Keeps newly mined blocks separate from the published total. */
public final class RankLocalProgress
{
    private long website = -1;
    private long pending;

    public RankLocalProgress() {}
    public RankLocalProgress(long website, long pending)
    {
        this.website = Math.max(-1, website);
        this.pending = this.website < 0 ? 0 : Math.min(Math.max(0, pending), Long.MAX_VALUE - this.website);
    }
    public void mined()
    {
        if (website >= 0 && pending < Long.MAX_VALUE - website) pending++;
    }
    public void reconcile(long published)
    {
        if (published < 0) throw new IllegalArgumentException("Negative website total");
        // An unchanged cache response must not erase mining. Only newly published
        // increases absorb local progress; corrections can lower the baseline.
        if (website >= 0 && published > website) pending -= Math.min(pending, published - website);
        website = published;
        pending = Math.min(pending, Long.MAX_VALUE - website);
    }
    public long website() { return website; }
    public long pending() { return pending; }
    public long total() { return Math.max(0, website) + pending; }
}
