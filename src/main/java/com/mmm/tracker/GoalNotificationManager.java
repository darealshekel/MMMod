package com.mmm.tracker;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.sound.GoalSoundLibrary;
import com.mmm.social.MilestoneSocialRelay;
import com.mmm.util.UiFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public final class GoalNotificationManager
{
    private static final Set<Integer> TRIGGERED_THRESHOLDS = new HashSet<>();
    private static final List<Integer> PICKAXE_MILESTONES = List.of(25, 50, 75, 100);
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

        int oldPercent = (int) Math.min(100, (oldProgress * 100) / progress.target());
        int newPercent = progress.getPercent();

        triggerPickaxeMilestone(oldPercent, newPercent);
        triggerMilestoneSound(oldPercent, newPercent);

        lastObservedProgress = progress.current();
        lastObservedTarget = progress.target();

        for (Integer threshold : Configs.getNotificationThresholds())
        {
            if (!TRIGGERED_THRESHOLDS.contains(threshold) && oldPercent < threshold && newPercent >= threshold)
            {
                TRIGGERED_THRESHOLDS.add(threshold);
                MilestoneSocialRelay.publishMilestone(threshold, progress);
                if (FeatureToggle.TWEAK_NOTIFICATIONS.getBooleanValue())
                {
                    showThresholdAnnouncement(threshold, progress);
                }
                return;
            }
        }
    }

    public static void render(DrawContext context, MinecraftClient client)
    {
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
        if (!FeatureToggle.TWEAK_SOUND_ALERTS.getBooleanValue())
        {
            return;
        }

        int highestCrossed = 0;
        for (int milestone : PICKAXE_MILESTONES)
        {
            if (oldPercent < milestone && newPercent >= milestone && TRIGGERED_SOUND_MILESTONES.add(milestone))
            {
                highestCrossed = milestone;
            }
        }

        if (highestCrossed <= 0)
        {
            return;
        }
        GoalSoundLibrary.play(highestCrossed);
    }

    private static void triggerPickaxeMilestone(int oldPercent, int newPercent)
    {
        int highestCrossed = 0;
        for (int milestone : PICKAXE_MILESTONES)
        {
            if (newPercent >= milestone)
            {
                boolean firstCrossing = TRIGGERED_PICKAXE_MILESTONES.add(milestone);
                if (firstCrossing && oldPercent < milestone)
                {
                    highestCrossed = milestone;
                }
            }
        }

        if (highestCrossed <= 0
                || Configs.Generic.GOAL_PICKAXE_ANIMATION.getBooleanValue() == false
                || FeatureToggle.TWEAK_DAILY_GOAL.getBooleanValue() == false)
        {
            return;
        }

        Item pickaxe = switch (highestCrossed)
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
            case 25 -> "Nice start - 25% done today";
            case 50 -> "Halfway there - 50% done today";
            case 75 -> "Almost there - 75% done today";
            default -> "Daily goal complete - 100% done";
        };
    }

}
