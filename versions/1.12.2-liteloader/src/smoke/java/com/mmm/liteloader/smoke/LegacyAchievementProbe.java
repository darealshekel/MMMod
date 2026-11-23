package com.mmm.liteloader.smoke;

import com.mmm.advancement.MmmAdvancementDefinition;
import com.mmm.advancement.MmmAdvancementManager;
import com.mmm.advancement.MmmAdvancementSnapshot;
import com.mmm.advancement.MmmAdvancementTree;
import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.tracker.GoalNotificationManager;
import com.mmm.tracker.MiningStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.menu.advancements.AdvancementsScreen;

/** Test-only: exercise notifications in the remapped, Java-8 runtime, not just unit tests. */
final class LegacyAchievementProbe {
    private static final int[] GOALS = {25, 50, 75, 100, 125, 425, 450};
    private int ticks;

    boolean tick(Minecraft client) throws Exception {
        ticks++;
        if (ticks == 1) {
            client.openScreen(null);
            FeatureToggle.MMM_DAILY_GOAL.setBooleanValue(true);
            FeatureToggle.MMM_NOTIFICATIONS.setBooleanValue(true);
            FeatureToggle.MMM_SOUND_ALERTS.setBooleanValue(true);
            Configs.Generic.GOAL_PICKAXE_ANIMATION.setBooleanValue(true);
            Configs.Generic.SHARE_GOAL_MILESTONES.setBooleanValue(false);
            GoalNotificationManager.clear();
            verifyUnlockBoundaries();
        }
        if (ticks < 100) return false;
        int elapsed = ticks - 100;
        if (elapsed < GOALS.length * 60) {
            if (elapsed % 60 == 0) {
                int percent = GOALS[elapsed / 60];
                long current = 35000L * percent / 100;
                GoalNotificationManager.onGoalProgressChanged(current - 1,
                        new MiningStats.GoalProgress("Daily Goal", true, current, 35000L));
                System.out.println("MMM_ACHIEVEMENT_PROBE_GOAL " + percent);
            }
            return false;
        }
        elapsed -= GOALS.length * 60;
        var definitions = MmmAdvancementDefinition.ORDERED;
        if (elapsed < definitions.size() * 120) {
            if (elapsed % 120 == 0) {
                var definition = definitions.get(elapsed / 120);
                var announce = MmmAdvancementManager.class.getDeclaredMethod("announce",
                        Minecraft.class, MmmAdvancementDefinition.class);
                announce.setAccessible(true);
                announce.invoke(null, client, definition);
                System.out.println("MMM_ACHIEVEMENT_PROBE_TOAST " + definition.name());
            }
            return false;
        }
        elapsed -= definitions.size() * 120;
        if (elapsed == 0) {
            var screen = new AdvancementsScreen(client.getNetworkHandler().getAdvancements());
            client.openScreen(screen);
            screen.onSelectedTabChanged(MmmAdvancementTree.rootEntry());
            for (var definition : definitions) {
                if (screen.findAdvancementWidget(MmmAdvancementTree.entry(definition)) == null)
                    throw new AssertionError("Missing advancement widget: " + definition.name());
            }
            System.out.println("MMM_ACHIEVEMENT_PROBE_TAB_OPEN");
        }
        if (elapsed < 100) return false;
        System.out.println("MMM_ACHIEVEMENT_PROBE_OK: goal animations, sounds, all advancement toasts and tab");
        return true;
    }

    private static void verifyUnlockBoundaries() throws Exception {
        var reconcile = Class.forName("com.mmm.advancement.AdvancementReconciliation")
                .getDeclaredMethod("apply", java.util.Set.class, MmmAdvancementSnapshot.class);
        reconcile.setAccessible(true);
        for (var definition : MmmAdvancementDefinition.ORDERED) {
            if (definition.kind() == MmmAdvancementDefinition.Kind.FIRST_USE) continue;
            long threshold = definition.threshold();
            if (definition.kind() == MmmAdvancementDefinition.Kind.SESSION_HOURS
                    || definition.kind() == MmmAdvancementDefinition.Kind.SESSION_ENDURANCE_HOURS)
                threshold *= 3_600_000L;
            var unlocked = java.util.EnumSet.noneOf(MmmAdvancementDefinition.class);
            for (int offset = -1; offset <= 1; offset++) {
                long value = threshold + offset;
                MmmAdvancementSnapshot snapshot = switch (definition.kind()) {
                    case SESSION_HOURS -> new MmmAdvancementSnapshot(value, 0, 0, 0);
                    case STREAK_DAYS -> new MmmAdvancementSnapshot(0, (int) value, 0, 0);
                    case BEST_HOUR_BLOCKS -> new MmmAdvancementSnapshot(0, 0, (int) value, 0);
                    case SESSION_ENDURANCE_HOURS -> new MmmAdvancementSnapshot(0, 0, 0, value);
                    case PRECISION_40K_SESSIONS -> new MmmAdvancementSnapshot(0, 0, 0, 0, (int) value, 0);
                    case PRECISION_50K_SESSIONS -> new MmmAdvancementSnapshot(0, 0, 0, 0, 0, (int) value);
                    default -> throw new AssertionError(definition);
                };
                reconcile.invoke(null, unlocked, snapshot);
                if (unlocked.contains(definition) != (offset >= 0))
                    throw new AssertionError("Incorrect unlock boundary: " + definition + " offset=" + offset);
                definition.progressLabel(snapshot);
                definition.progressFraction(snapshot);
            }
        }
        System.out.println("MMM_ACHIEVEMENT_PROBE_BOUNDARIES_OK");
    }
}
