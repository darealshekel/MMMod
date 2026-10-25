package com.mmm.legacy.social;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.social.ActiveDiggerManager;
import com.mmm.social.PublicChatClient;
import com.mmm.tags.TierTagManager;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public final class LegacySocialHooks {
    private LegacySocialHooks() {}

    public static void tick(Minecraft client) {
        PublicChatClient.onClientTick(client);
        TierTagManager.onClientTick(client);
    }

    public static void disconnect() {
        PublicChatClient.disconnect();
        TierTagManager.clear();
    }

    public static Text tabFooter(Text original) {
        List<String> local = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getNetworkHandler() != null)
            client.getNetworkHandler().getOnlinePlayers().forEach(info -> local.add(info.getProfile().getName()));
        List<String> remote = ActiveDiggerManager.visibleRemoteDiggers(local);
        Text extra = new LiteralText("");
        boolean hasExtra = false;
        if (!remote.isEmpty()) {
            extra.append(LegacySocialText.text("Active Diggers", Formatting.GRAY));
            for (int index = 0; index < remote.size(); index++) {
                extra.append(index % 4 == 0 ? "\n" : "   ");
                extra.append(LegacySocialText.text("\u26CF ", Formatting.RED))
                        .append(LegacySocialText.text(remote.get(index), Formatting.WHITE));
            }
            hasExtra = true;
        }
        if (FeatureToggle.MMM_MINING_TRACKER.getBooleanValue() && FeatureToggle.MMM_DAILY_GOAL.getBooleanValue()) {
            MiningStats.GoalProgress goal = MiningStats.getDailyGoalProgress();
            if (goal.enabled()) {
                if (hasExtra) extra.append("\n");
                extra.append(LegacySocialText.text("Daily Goal: ", Formatting.GRAY))
                        .append(LegacySocialText.text(String.format(Locale.US, "%,d/%,d", Math.max(0, goal.current()), Math.max(0, goal.target())), Formatting.WHITE))
                        .append(LegacySocialText.rgb("  " + UiFormat.formatGoalPercent(goal), UiFormat.getGoalProgressColor(goal)));
                hasExtra = true;
            }
        }
        if (!hasExtra) return original;
        return original == null ? extra : new LiteralText("").append(original.copy()).append("\n").append(extra);
    }

    /** Decorate only the sender argument, never free-form message text or a scoreboard's numeric value. */
    public static Text decorateChat(Text message) {
        if (!(message instanceof TranslatableText) || !Configs.Generic.TIER_NAME_TAGS.getBooleanValue()) return message;
        TranslatableText translated = (TranslatableText) message;
        String key = translated.getKey();
        if (!"chat.type.text".equals(key) && !"chat.type.announcement".equals(key) && !"chat.type.emote".equals(key)) return message;
        Object[] args = translated.getArgs().clone();
        if (args.length == 0) return message;
        Text sender = args[0] instanceof Text ? (Text) args[0] : new LiteralText(String.valueOf(args[0]));
        Text decorated = TierTagManager.decorateDisplayedName(sender);
        if (decorated == null) return message;
        args[0] = decorated;
        Text result = new TranslatableText(key, args).setStyle(message.getStyle().copy());
        for (Text sibling : message.getSiblings()) result.append(sibling.copy());
        return result;
    }
}
