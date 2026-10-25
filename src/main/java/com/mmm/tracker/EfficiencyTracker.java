package com.mmm.tracker;

import com.mmm.storage.SessionData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class EfficiencyTracker
{
    private static final SessionEfficiency.Sampler SAMPLER = new SessionEfficiency.Sampler();
    private static SessionData observed;
    private static long previousBlocks, lastHarvestIgt = -1000;
    private static Vec3 previousPosition;

    private EfficiencyTracker() {}

    public static void tick(Minecraft client) {
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
        Vec3 position = client.player == null ? null : client.player.position();
        boolean moving = position != null && previousPosition != null && position.distanceToSqr(previousPosition) > 0.0001;
        SessionEfficiency.Activity activity = null;
        if (MiningStats.isSessionActive() && !MiningStats.isSessionPaused() && !client.isPaused()
                && client.player != null && client.level != null) {
            activity = classify(client, moving, igt - lastHarvestIgt <= 150);
        }
        SAMPLER.sample(session.efficiency, igt, activity);
        previousPosition = position;
    }

    private static SessionEfficiency.Activity classify(Minecraft client, boolean moving, boolean harvested) {
        if (client.gui.screen() instanceof AbstractContainerScreen<?>) return SessionEfficiency.Activity.INVENTORY;
        if (client.gui.screen() != null) return SessionEfficiency.Activity.MENU;
        boolean breaking = client.gameMode != null
                && ((com.mmm.mixin.ClientPlayerInteractionManagerAccessor) client.gameMode).mmm$isBreakingBlock();
        if ((breaking || harvested) && client.options.keyAttack.isDown() && client.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK && !client.player.isSpectator()) {
            var state = client.level.getBlockState(hit.getBlockPos());
            if (!state.isAir()) {
                float speed = state.getDestroyProgress(client.player, client.level, hit.getBlockPos());
                if (speed > 0 && speed < 1 && !client.player.isCreative()) return SessionEfficiency.Activity.NON_INSTANT;
                if (speed >= 1 || client.player.isCreative()) return SessionEfficiency.Activity.MINING;
            }
        }
        if (harvested) return SessionEfficiency.Activity.MINING;
        return moving ? SessionEfficiency.Activity.MOVING : SessionEfficiency.Activity.OTHER;
    }
}
