package com.mmm.advancement;

import java.util.EnumSet;
import java.util.Set;

final class AdvancementReconciliation {
    private AdvancementReconciliation() {}

    static boolean hasHistory(MmmAdvancementSnapshot history) {
        return history.totalSessionMs() > 0 || history.longestSessionMs() > 0 || history.bestHourBlocks() > 0
                || history.longestStreakDays() > 0 || history.sessionsAt40kBph() > 0 || history.sessionsAt50kBph() > 0;
    }

    static Result apply(Set<MmmAdvancementDefinition> unlocked, MmmAdvancementSnapshot snapshot) {
        Set<MmmAdvancementDefinition> added = EnumSet.noneOf(MmmAdvancementDefinition.class);
        boolean changed = false;
        for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED) {
            if (definition.isReached(snapshot)) {
                if (unlocked.add(definition)) { added.add(definition); changed = true; }
            } else if (unlocked.remove(definition)) changed = true;
        }
        return new Result(java.util.Collections.unmodifiableSet(added), changed);
    }

    record Result(Set<MmmAdvancementDefinition> newlyUnlocked, boolean changed) {}
}
