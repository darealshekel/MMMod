package com.mmm.tracker;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.social.PublicChatClient;
import com.mmm.sound.GoalSoundLibrary;
import com.mmm.util.UiFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public final class GoalNotificationManager
{
    private static final Set<Integer> TRIGGERED_THRESHOLDS = new HashSet<>();
    private static final List<Integer> BASE_MILESTONES = List.of(25, 50, 75, 100);
    private static final Set<Integer> TRIGGERED_PICKAXE_MILESTONES = new HashSet<>();
    private static final Set<Integer> TRIGGERED_SOUND_MILESTONES = new HashSet<>();
    private static long lastObservedProgress;
    private static long lastObservedTarget;

    private GoalNotificationManager()
    {
    }

    public static void onGoalProgressChanged(long oldProgress, MiningStats.GoalProgress progress)
    {
        if (progress.target() <= 0)
        {
            return;
        }

        if (progress.target() != lastObservedTarget || progress.current() < lastObservedProgress)
        {
            clear();
        }

        int oldPercent = GoalMilestonePolicy.percent(oldProgress, progress.target());
        int newPercent = GoalMilestonePolicy.percent(progress.current(), progress.target());
        int crossedMilestone = GoalMilestonePolicy.highestCrossed(oldPercent, newPercent);

        triggerPickaxeMilestone(crossedMilestone);
        triggerMilestoneSound(oldPercent, newPercent);

        lastObservedProgress = progress.current();
        lastObservedTarget = progress.target();

        if (crossedMilestone > 0 && TRIGGERED_THRESHOLDS.add(crossedMilestone))
        {
            PublicChatClient.publishMilestone(crossedMilestone, progress);
            if (FeatureToggle.MMM_NOTIFICATIONS.getBooleanValue())
            {
                showThresholdAnnouncement(crossedMilestone, progress);
            }
        }
    }

    public static void clear()
    {
        TRIGGERED_THRESHOLDS.clear();
        TRIGGERED_PICKAXE_MILESTONES.clear();
        TRIGGERED_SOUND_MILESTONES.clear();
        lastObservedProgress = 0L;
        lastObservedTarget = 0L;
    }

    private static void triggerMilestoneSound(int oldPercent, int newPercent)
    {
        if (!FeatureToggle.MMM_SOUND_ALERTS.getBooleanValue())
        {
            return;
        }

        int highestCrossed = 0;
        for (int milestone : BASE_MILESTONES)
        {
            if (oldPercent < milestone && newPercent >= milestone && TRIGGERED_SOUND_MILESTONES.add(milestone))
            {
                highestCrossed = milestone;
            }
        }

        if (highestCrossed > 0)
        {
            GoalSoundLibrary.play(highestCrossed);
        }
    }

    private static void triggerPickaxeMilestone(int milestone)
    {
        if (milestone <= 0
                || TRIGGERED_PICKAXE_MILESTONES.add(milestone) == false
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
            default -> Items.NETHERITE_PICKAXE;
        };
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null)
        {
            ItemStack stack = new ItemStack(pickaxe);
            if (milestone > 100)
            {
                stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            client.execute(() -> client.gameRenderer.showFloatingItem(stack));
        }
    }

    private static void showThresholdAnnouncement(int threshold, MiningStats.GoalProgress progress)
    {
        MinecraftClient client = MinecraftClient.getInstance();
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
            client.player.sendMessage(Text.literal(message).styled(style -> style.withColor(color)), false);
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
