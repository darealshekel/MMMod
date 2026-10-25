package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.tracker.GoalMilestonePolicy;
import com.mmm.tracker.MiningStats;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;

public final class LegacyGoalNotifications {
    private static final Set<Integer> milestones = new HashSet<>();
    private static long lastTarget;
    private static long lastProgress;

    private LegacyGoalNotifications() {}

    public static void clear() { milestones.clear(); lastTarget = 0; lastProgress = 0; }

    public static void onGoalProgressChanged(long oldProgress, MiningStats.GoalProgress progress) {
        if (progress.target() <= 0) return;
        if (lastTarget != progress.target() || progress.current() < lastProgress) clear();
        lastTarget = progress.target();
        lastProgress = progress.current();
        int threshold = GoalMilestonePolicy.highestCrossed(
                GoalMilestonePolicy.percent(oldProgress, progress.target()),
                GoalMilestonePolicy.percent(progress.current(), progress.target()));
        Minecraft client = Minecraft.getInstance();
        if (threshold <= 0 || !milestones.add(threshold) || client.player == null) return;
        if (FeatureToggle.MMM_NOTIFICATIONS.getBooleanValue()) {
            client.player.addMessage(new LiteralText("[MMM] Daily goal " + threshold + "% ("
                    + String.format(java.util.Locale.US, "%,d / %,d blocks).", progress.current(), progress.target())), false);
        }
        if (FeatureToggle.MMM_SOUND_ALERTS.getBooleanValue() && threshold <= 100) {
            client.getSoundManager().play(new net.minecraft.client.sound.instance.SimpleSoundInstance(
                    new net.minecraft.resource.Identifier("mmm", threshold == 100 ? "goal_complete" : "goal_success"),
                    net.minecraft.sound.SoundCategory.MASTER, 1.0F, 1.0F, false, 0,
                    net.minecraft.client.sound.instance.SoundInstance.Attenuation.NONE, 0, 0, 0));
        }
        if (Configs.Generic.GOAL_PICKAXE_ANIMATION.getBooleanValue() && FeatureToggle.MMM_DAILY_GOAL.getBooleanValue()) {
            // Netherite does not exist in 1.12.2; use the highest vanilla tool tier.
            ItemStack stack = new ItemStack(threshold == 25 ? Items.STONE_PICKAXE
                    : threshold == 50 ? Items.IRON_PICKAXE : Items.DIAMOND_PICKAXE);
            if (threshold >= 100) stack.addEnchantment(net.minecraft.enchantment.Enchantments.EFFICIENCY, 1);
            client.gameRenderer.displayItemOverlay(stack);
        }
    }
}
