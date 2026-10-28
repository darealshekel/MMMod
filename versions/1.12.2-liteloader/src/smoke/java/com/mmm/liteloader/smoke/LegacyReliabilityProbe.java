package com.mmm.liteloader.smoke;

import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Exercises actual server block updates through the packaged LiteLoader mixins. */
final class LegacyReliabilityProbe {
    private int tick;
    private BlockPos target;

    boolean tick(Minecraft client) {
        tick++;
        if (tick == 1) {
            client.openScreen(null);
            FeatureToggle.MMM_MINING_TRACKER.setBooleanValue(true);
            MiningStats.finaliseSession();
            MiningStats.startNewSession();
            target = new BlockPos(client.player.x + 2, client.player.y + 1, client.player.z);
            prepare(client, Blocks.NETHERRACK, true);
        } else if (tick == 30) {
            client.interactionManager.startMiningBlock(target, Direction.WEST);
        } else if (tick == 60) {
            count(1, "instamine");
            target = target.south();
            prepare(client, Blocks.END_STONE, false);
        } else if (tick == 80) {
            client.interactionManager.startMiningBlock(target, Direction.WEST);
            client.interactionManager.stopMiningBlock();
            BlockPos external = target;
            client.getServer().executeTask(() -> client.getServer().getWorld(0).setBlockState(external, Blocks.AIR.defaultState()));
        } else if (tick == 110) {
            count(1, "cancelled attack removed externally");
            target = target.north(2);
            prepare(client, Blocks.BEDROCK, true);
        } else if (tick == 130) {
            client.world.setBlockState(target, Blocks.NETHERRACK.defaultState());
            client.interactionManager.startMiningBlock(target, Direction.WEST);
        } else if (tick == 160) {
            count(1, "server rollback");
            target = target.up();
            prepare(client, Blocks.END_STONE, false);
        } else if (tick == 180) {
            double dx = target.getX() + .5 - client.player.x;
            double dy = target.getY() + .5 - client.player.y - client.player.getEyeHeight();
            double dz = target.getZ() + .5 - client.player.z;
            client.player.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            client.player.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            KeyBinding.set(client.options.attackKey.getKeyCode(), true);
            client.interactionManager.startMiningBlock(target, Direction.WEST);
        } else if (tick == 510) {
            KeyBinding.set(client.options.attackKey.getKeyCode(), false);
        } else if (tick == 520) {
            count(2, "slow break exceeding ten seconds");
            org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
            System.out.println("MMM_RUNTIME_MINING_OK: instamine, cancelled attack, rollback, slow break");
            return true;
        }
        return false;
    }

    private void prepare(Minecraft client, Block block, boolean fast) {
        BlockPos pos = target;
        client.getServer().executeTask(() -> {
            var player = client.getServer().getPlayerManager().get(client.player.getUuid());
            player.inventory.selectedSlot = 0;
            player.inventory.setItem(0, fast ? new ItemStack(Items.GOLDEN_PICKAXE) : ItemStack.EMPTY);
            client.getServer().getWorld(0).setBlockState(pos, block.defaultState());
        });
        client.player.inventory.selectedSlot = 0;
        client.player.inventory.setItem(0, fast ? new ItemStack(Items.GOLDEN_PICKAXE) : ItemStack.EMPTY);
    }

    private static void count(long expected, String scenario) {
        if (MiningStats.getSessionTotal() != expected)
            throw new AssertionError(scenario + ": expected " + expected + ", got " + MiningStats.getSessionTotal());
    }
}
