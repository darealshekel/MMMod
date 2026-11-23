package com.mmm.advancement;

import java.util.ArrayDeque;
import com.mmm.tracker.CollectionProgress;

/** Presentation-only state; never grants or publishes an achievement. */
public final class AchievementCardState
{
    public static final double SLIDE_SECONDS = 0.45;
    public static final double HOLD_SECONDS = 4.0;
    public static final double DURATION_SECONDS = SLIDE_SECONDS * 2 + HOLD_SECONDS;
    public record Card(MmmAdvancementDefinition definition, String collectionId, long milestone,
                       String heading, String title) {}

    private final ArrayDeque<Card> queue = new ArrayDeque<>();
    private double elapsed;

    public void offer(MmmAdvancementDefinition definition)
    {
        if (definition != null) offerCard(new Card(definition, "", 0, "Achievement Unlocked!", definition.title()));
    }

    public void offerCollection(String collectionId, String name, long milestone)
    {
        if (collectionId == null || name == null || name.isBlank() || milestone <= 0) return;
        String id = CollectionProgress.collectionId(collectionId);
        if (queue.stream().anyMatch(card -> card.collectionId().equals(id) && card.milestone() == milestone)) return;
        offerCard(new Card(null, id, milestone, "Collection Milestone!",
                name + " - " + CollectionProgress.targetLabel(milestone)));
    }

    private void offerCard(Card card)
    {
        if (!queue.contains(card)) queue.addLast(card);
    }

    public Card currentCard() { return queue.peekFirst(); }

    public MmmAdvancementDefinition current() { return queue.isEmpty() ? null : queue.peekFirst().definition(); }

    public void advance(double seconds)
    {
        if (queue.isEmpty() || !Double.isFinite(seconds) || seconds <= 0) return;
        elapsed += seconds;
        if (elapsed >= DURATION_SECONDS)
        {
            queue.removeFirst();
            elapsed = 0;
        }
    }

    public double visibility()
    {
        if (queue.isEmpty()) return 0;
        double t = elapsed < SLIDE_SECONDS ? elapsed / SLIDE_SECONDS
                : (DURATION_SECONDS - elapsed) / SLIDE_SECONDS;
        t = Math.max(0, Math.min(1, t));
        return 1 - Math.pow(1 - t, 3);
    }

    public void clear()
    {
        queue.clear();
        elapsed = 0;
    }
}
