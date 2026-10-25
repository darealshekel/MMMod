package com.mmm.tracker;

import com.mmm.storage.SessionData;
import com.mmm.mixin.ClientPlayerInteractionManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;

public final class EfficiencyTracker {
    private static final SessionEfficiency.Sampler SAMPLER = new SessionEfficiency.Sampler();
    private static SessionData observed;
    private static long previousBlocks, lastHarvestIgt = -1000;
    private static Vec3d previousPosition;

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
        Vec3d position = client.player == null ? null : new Vec3d(client.player.x, client.player.y, client.player.z);
        boolean moving = position != null && previousPosition != null && position.squaredDistanceTo(previousPosition) > 0.0001;
        SessionEfficiency.Activity activity = null;
        if (MiningStats.isSessionActive() && !MiningStats.isSessionPaused() && !client.isPaused()
                && client.player != null && client.world != null) {
            activity = classify(client, moving, igt - lastHarvestIgt <= 150);
        }
        SAMPLER.sample(session.efficiency, igt, activity);
        previousPosition = position;
    }

    private static SessionEfficiency.Activity classify(Minecraft client, boolean moving, boolean harvested) {
        if (client.screen instanceof InventoryMenuScreen) return SessionEfficiency.Activity.INVENTORY;
        if (client.screen != null) return SessionEfficiency.Activity.MENU;
        boolean breaking = client.interactionManager != null
                && ((ClientPlayerInteractionManagerAccessor) client.interactionManager).mmm$isBreakingBlock();
        HitResult hit = client.crosshairTarget;
        if ((breaking || harvested) && client.options.attackKey.isPressed() && hit != null
                && hit.type == HitResult.Type.BLOCK && !client.player.isSpectator()) {
            var state = client.world.getBlockState(hit.getPos());
            if (state.getMaterial() != Material.AIR) {
                float speed = state.getMiningSpeed(client.player, client.world, hit.getPos());
                if (speed > 0 && speed < 1 && !client.player.isCreative()) return SessionEfficiency.Activity.NON_INSTANT;
                if (speed >= 1 || client.player.isCreative()) return SessionEfficiency.Activity.MINING;
            }
        }
        if (harvested) return SessionEfficiency.Activity.MINING;
        return moving ? SessionEfficiency.Activity.MOVING : SessionEfficiency.Activity.OTHER;
    }
}
