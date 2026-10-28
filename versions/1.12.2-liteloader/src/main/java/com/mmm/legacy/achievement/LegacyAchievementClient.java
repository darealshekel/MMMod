package com.mmm.legacy.achievement;

import com.mmm.advancement.MmmAdvancementManager;
import com.mmm.tracker.GoalNotificationManager;
import com.mmm.update.UpdateNotifier;
import net.minecraft.client.Minecraft;

/** Called by LegacyClient after world/session identity is established. */
public final class LegacyAchievementClient {
    private static final UpdateNotifier UPDATES = new UpdateNotifier();
    private LegacyAchievementClient() {}

    public static void init(Minecraft client) { GoalProgressTexture.registerReloadListener(client); }

    public static void onJoin(Minecraft client) { UPDATES.onJoin(client); }

    public static void tick(Minecraft client) {
        MmmAdvancementManager.onClientTick(client);
        UPDATES.tick(client);
    }

    public static void onDisconnect() {
        MmmAdvancementManager.onDisconnect();
        GoalNotificationManager.clear();
        UPDATES.onDisconnect();
    }
}
