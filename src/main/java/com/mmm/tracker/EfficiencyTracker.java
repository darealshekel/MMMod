package com.mmm.tracker;

import com.mmm.storage.SessionData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public final class EfficiencyTracker
{
    private static final SessionEfficiency.Sampler SAMPLER = new SessionEfficiency.Sampler();
    private static SessionData observed;
    private static long previousBlocks, lastHarvestIgt = -1000;
    private static Vec3d previousPosition;

    private EfficiencyTracker() {}

    public static void tick(MinecraftClient client) {
        SessionData session = MiningStats.getCurrentSession();
        long igt = session.getActiveDurationMs();
        if (session != observed) {
            observed = session;
            previousBlocks = session.totalBlocks;
            lastHarvestIgt = -1000;
            previousPosition = null;
        }
        if (session.totalBlocks > previousBlocks) lastHarvestIgt = igt;
        previousBlocks = session.totalBlocks;
        Vec3d position = client.player == null ? null : client.player.getEntityPos();
        boolean moving = position != null && previousPosition != null && position.squaredDistanceTo(previousPosition) > 0.0001;
        SessionEfficiency.Activity activity = null;
        if (MiningStats.isSessionActive() && !MiningStats.isSessionPaused() && !client.isPaused()
                && client.player != null && client.world != null) {
            activity = classify(client, moving, igt - lastHarvestIgt <= 150);
        }
        SAMPLER.sample(session.efficiency, igt, activity);
        previousPosition = position;
    }

    private static SessionEfficiency.Activity classify(MinecraftClient client, boolean moving, boolean harvested) {
        if (client.currentScreen instanceof HandledScreen<?>) return SessionEfficiency.Activity.INVENTORY;
        if (client.currentScreen != null) return SessionEfficiency.Activity.MENU;
        boolean breaking = client.interactionManager != null
                && ((com.mmm.mixin.ClientPlayerInteractionManagerAccessor) client.interactionManager).mmm$isBreakingBlock();
        if ((breaking || harvested) && client.options.attackKey.isPressed() && client.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK && !client.player.isSpectator()) {
            var state = client.world.getBlockState(hit.getBlockPos());
            if (!state.isAir()) {
                float speed = state.calcBlockBreakingDelta(client.player, client.world, hit.getBlockPos());
                if (speed > 0 && speed < 1 && !client.player.isCreative()) return SessionEfficiency.Activity.NON_INSTANT;
                if (speed >= 1 || client.player.isCreative()) return SessionEfficiency.Activity.MINING;
            }
        }
        if (harvested) return SessionEfficiency.Activity.MINING;
        return moving ? SessionEfficiency.Activity.MOVING : SessionEfficiency.Activity.OTHER;
    }
}
