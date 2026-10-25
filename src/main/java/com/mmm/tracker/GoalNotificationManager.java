package com.mmm.tracker;

import java.util.Locale;

import com.mmm.config.Configs;
import com.mmm.legacy.achievement.GoalMilestones;
import com.mmm.legacy.social.LegacySocialText;
import com.mmm.config.FeatureToggle;
import com.mmm.social.PublicChatClient;
import com.mmm.sound.GoalSoundLibrary;
import com.mmm.util.UiFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class GoalNotificationManager
{
    private static final GoalMilestones MILESTONES = new GoalMilestones();

    private GoalNotificationManager() {}

    public static void onGoalProgressChanged(long oldProgress, MiningStats.GoalProgress progress)
    {
        GoalMilestones.Result crossed = MILESTONES.advance(oldProgress, progress.current(), progress.target(),
                FeatureToggle.MMM_SOUND_ALERTS.getBooleanValue());
        triggerPickaxeMilestone(crossed.milestone());
        if (crossed.soundMilestone() > 0) GoalSoundLibrary.play(crossed.soundMilestone());
        if (crossed.milestone() > 0) {
            PublicChatClient.publishMilestone(crossed.milestone(), progress);
            if (FeatureToggle.MMM_NOTIFICATIONS.getBooleanValue()) showThresholdAnnouncement(crossed.milestone(), progress);
        }
    }

    public static void clear() { MILESTONES.clear(); }

    private static void triggerPickaxeMilestone(int milestone)
    {
        if (milestone <= 0
                || Configs.Generic.GOAL_PICKAXE_ANIMATION.getBooleanValue() == false
                || FeatureToggle.MMM_DAILY_GOAL.getBooleanValue() == false)
        {
            return;
        }

        Item pickaxe = switch (milestone)
        {
            case 25 -> Items.STONE_PICKAXE;
            case 50 -> Items.IRON_PICKAXE;
            case 75 -> Items.DIAMOND_PICKAXE;
            default -> Items.DIAMOND_PICKAXE;
        };
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null)
        {
            ItemStack stack = new ItemStack(pickaxe);
            if (milestone > 100)
            {
                stack.addEnchantment(net.minecraft.enchantment.Enchantments.EFFICIENCY, 1);
            }
            client.executeTask(() -> client.gameRenderer.displayItemOverlay(stack));
        }
    }

    private static void showThresholdAnnouncement(int threshold, MiningStats.GoalProgress progress)
    {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null)
        {
            int color = UiFormat.getGoalProgressColor(progress) & 0x00FFFFFF;
            String message = String.format(
                    Locale.US,
                    "[MMM] %s (%,d / %,d blocks).",
                    getMilestoneMessage(threshold),
                    progress.current(),
                    progress.target()
            );
            client.player.addMessage(LegacySocialText.rgb(message, color), false);
        }
    }

    private static String getMilestoneMessage(int threshold)
    {
        return switch (threshold)
        {
            case 25 -> "Daily goal started - 25% reached";
            case 50 -> "Halfway through today's goal - 50% reached";
            case 75 -> "Final stretch - 75% reached";
            case 100 -> "Daily goal complete - 100% reached";
            default -> "Daily goal exceeded - " + threshold + "% reached";
        };
    }
}
