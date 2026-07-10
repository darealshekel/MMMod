package com.mmm.tracker;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.sound.GoalSoundLibrary;
import com.mmm.util.UiFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public final class GoalNotificationManager
{
    private static final List<String> MESSAGES_25 = List.of(
            "Great start - you are building solid momentum.",
            "Good pace - keep mining and stay consistent.",
            "Your goal is moving - keep the rhythm going.",
            "Strong opening - settle in and keep digging.",
            "Nice progress - keep this pace moving forward."
    );
    private static final List<String> MESSAGES_50 = List.of(
            "Halfway there - keep your pace steady.",
            "Half complete - stay focused and keep digging.",
            "You are halfway in - keep the momentum going.",
            "Good work - the second half starts now.",
            "Halfway done - keep moving toward the finish."
    );
    private static final List<String> MESSAGES_75 = List.of(
            "Three quarters done - finish the last stretch.",
            "Almost there - keep your pace through the finish.",
            "Final stretch - stay steady and bring it home.",
            "Your goal is close - keep mining to the end.",
            "Great progress - one last push will finish it."
    );
    private static final List<String> MESSAGES_100 = List.of(
            "Daily goal complete - excellent work today.",
            "Goal finished - your steady effort paid off.",
            "Daily target reached - well done.",
            "You completed the goal - strong work.",
            "Goal complete - another solid mining day."
    );

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
            String message = "[MMM] " + threshold + "% reached. " + getRandomMessage(threshold);
            client.player.sendMessage(Text.literal(message).styled(style -> style.withColor(color)), false);
        }
    }

    private static String getRandomMessage(int threshold)
    {
        List<String> pool = threshold >= 100 ? MESSAGES_100
                : threshold >= 75 ? MESSAGES_75
                : threshold >= 50 ? MESSAGES_50
                : MESSAGES_25;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

}
