package com.mmm.smoke;

import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Real integrated-server acknowledgements, not simulated air polling. */
final class ReliabilitySmoke {
    private int tick;
    private BlockPos target;
    private boolean done;
    private final long started = System.nanoTime();

    void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done) return;
            try {
                if (System.nanoTime() - started > 120_000_000_000L) throw new AssertionError("Reliability smoke timed out");
                tick++;
                if (tick == 1) {
                    client.gui.setScreen(null);
                    client.options.pauseOnLostFocus = false;
                    FeatureToggle.MMM_MINING_TRACKER.setBooleanValue(true);
                    MiningStats.finaliseSession();
                    MiningStats.startNewSession();
                    prepareArena(client);
                    target = new BlockPos(12, 82, 14);
                    prepare(client, Blocks.NETHERRACK.defaultBlockState(), true);
                } else if (tick == 30) {
                    client.gameMode.startDestroyBlock(target, Direction.NORTH);
                } else if (tick == 60) {
                    count(1, "instamine acknowledgement");
                    target = target.east();
                    prepare(client, Blocks.DEEPSLATE.defaultBlockState(), false);
                } else if (tick == 80) {
                    client.gameMode.startDestroyBlock(target, Direction.NORTH);
                    client.gameMode.stopDestroyBlock();
                    BlockPos external = target;
                    client.getSingleplayerServer().execute(() -> client.getSingleplayerServer().overworld().setBlock(external, Blocks.AIR.defaultBlockState(), 3));
                } else if (tick == 110) {
                    count(1, "cancelled target removed externally");
                    target = target.west(2);
                    prepare(client, Blocks.BEDROCK.defaultBlockState(), true);
                } else if (tick == 130) {
                    client.level.setBlock(target, Blocks.NETHERRACK.defaultBlockState(), 3);
                    client.gameMode.startDestroyBlock(target, Direction.NORTH);
                } else if (tick == 160) {
                    count(1, "server rollback");
                    target = target.above();
                    prepare(client, Blocks.DEEPSLATE.defaultBlockState(), false);
                } else if (tick == 180) {
                    var delta = net.minecraft.world.phys.Vec3.atCenterOf(target).subtract(client.player.getEyePosition());
                    client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
                    client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
                    client.options.keyAttack.setDown(true);
                    client.gameMode.startDestroyBlock(target, Direction.NORTH);
                } else if (tick == 500) {
                    client.options.keyAttack.setDown(false);
                } else if (tick == 510) {
                    count(2, "slow break exceeding ten seconds");
                    org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                    client.gui.setScreen(null);
                    done = true;
                    Files.writeString(Path.of("mmm-reliability-smoke.txt"),
                            "PASS: instamine, cancelled attack/external removal, server rollback, >10s break, mixin audit");
                    System.out.println("MMM_RELIABILITY_SMOKE_PASS");
                    client.stop();
                }
            } catch (Throwable failure) {
                done = true;
                failure.printStackTrace();
                try { Files.writeString(Path.of("mmm-reliability-smoke.txt"), "FAIL: " + failure); }
                catch (Exception ignored) {}
                client.stop();
            }
        });
    }

    private static void count(long expected, String scenario) {
        long actual = MiningStats.getSessionBlocksMined();
        if (actual != expected) throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
    }

    private static void prepareArena(Minecraft client) {
        client.getSingleplayerServer().execute(() -> {
            var server = client.getSingleplayerServer();
            server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
            var world = server.overworld();
            for (int x = 9; x <= 16; x++) for (int z = 9; z <= 16; z++) {
                world.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 81; y <= 85; y++) world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
            server.getPlayerList().getPlayer(client.player.getUUID()).teleportTo(12.5, 81, 12.5);
        });
        client.player.setPos(12.5, 81, 12.5);
    }

    private void prepare(Minecraft client, net.minecraft.world.level.block.state.BlockState state, boolean fast) {
        BlockPos pos = target;
        client.getSingleplayerServer().execute(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, fast ? new ItemStack(Items.NETHERITE_PICKAXE) : ItemStack.EMPTY);
            player.removeEffect(MobEffects.HASTE);
            if (fast) player.addEffect(new MobEffectInstance(MobEffects.HASTE, 2000, 1));
            player.containerMenu.broadcastChanges();
            client.getSingleplayerServer().overworld().setBlock(pos, state, 3);
        });
        client.player.getInventory().setSelectedSlot(0);
        client.player.getInventory().setItem(0, fast ? new ItemStack(Items.NETHERITE_PICKAXE) : ItemStack.EMPTY);
    }
}
