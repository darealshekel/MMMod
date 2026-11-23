package com.mmm.smoke;

import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Real integrated-server acknowledgements, not simulated air polling. */
final class ReliabilitySmoke {
    private int tick;
    private BlockPos target;
    private boolean done;
    private boolean prepared = true;
    private final long started = System.nanoTime();

    void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done) return;
            try {
                if (System.nanoTime() - started > 120_000_000_000L) throw new AssertionError("Reliability smoke timed out");
                if (!prepared) return;
                tick++;
                if (tick == 210 || tick == 380 || tick == 490) {
                    var interaction = (com.mmm.mixin.ClientPlayerInteractionManagerAccessor) client.interactionManager;
                    System.out.println("MMM_SLOW_CHECK tick=" + tick + " target=" + target + " breaking=" + interaction.mmm$getCurrentBreakingPos()
                            + " progress=" + interaction.mmm$getCurrentBreakingProgress() + " held=" + client.options.attackKey.isPressed()
                            + " hit=" + (client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit ? hit.getBlockPos() : client.crosshairTarget));
                }
                if (tick >= 180 && tick < 500) {
                    var delta = net.minecraft.util.math.Vec3d.ofCenter(target).subtract(client.player.getEyePos());
                    client.player.setYaw((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
                    client.player.setPitch((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
                    client.options.attackKey.setPressed(true);
                }
                if (tick == 1) {
                    client.setScreen(null);
                    client.onWindowFocusChanged(true);
                    client.mouse.lockCursor();
                    client.options.pauseOnLostFocus = false;
                    FeatureToggle.MMM_MINING_TRACKER.setBooleanValue(true);
                    MiningStats.finaliseSession();
                    MiningStats.startNewSession();
                    BlockPos anchor = client.player.getBlockPos();
                    prepareArena(client, anchor);
                    target = anchor.east(2).up();
                    prepare(client, Blocks.NETHERRACK.getDefaultState(), true);
                } else if (tick == 30) {
                    if (!client.world.getBlockState(target).isOf(Blocks.NETHERRACK)) throw new AssertionError("Test block not delivered by server");
                    client.interactionManager.attackBlock(target, Direction.NORTH);
                } else if (tick == 60) {
                    count(1, "instamine acknowledgement");
                    target = target.east();
                    prepare(client, Blocks.DEEPSLATE.getDefaultState(), false);
                } else if (tick == 80) {
                    client.interactionManager.attackBlock(target, Direction.NORTH);
                    client.interactionManager.cancelBlockBreaking();
                    BlockPos external = target;
                    client.getServer().execute(() -> client.getServer().getOverworld().setBlockState(external, Blocks.AIR.getDefaultState()));
                } else if (tick == 110) {
                    count(1, "cancelled target removed externally");
                    target = target.west(2);
                    prepare(client, Blocks.BEDROCK.getDefaultState(), true);
                } else if (tick == 130) {
                    client.world.setBlockState(target, Blocks.NETHERRACK.getDefaultState());
                    client.interactionManager.attackBlock(target, Direction.NORTH);
                } else if (tick == 160) {
                    count(1, "server rollback");
                    client.onWindowFocusChanged(true);
                    client.mouse.lockCursor();
                    target = target.up();
                    prepare(client, Blocks.DEEPSLATE.getDefaultState(), false);
                } else if (tick == 180) {
                    var delta = net.minecraft.util.math.Vec3d.ofCenter(target).subtract(client.player.getEyePos());
                    client.player.setYaw((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
                    client.player.setPitch((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
                    client.options.attackKey.setPressed(true);
                    client.interactionManager.attackBlock(target, Direction.NORTH);
                } else if (tick == 500) {
                    client.options.attackKey.setPressed(false);
                } else if (tick == 510) {
                    if (!client.world.getBlockState(target).isAir()) throw new AssertionError("Slow mining did not finish: " + client.world.getBlockState(target) + ", screen=" + client.currentScreen + ", position=" + client.player.getBlockPos());
                    count(2, "slow break exceeding ten seconds");
                    RuntimeChecks.run(client);
                    client.setScreen(null);
                    done = true;
                    Files.writeString(Path.of("mmm-reliability-smoke.txt"),
                            "PASS: instamine, cancelled attack/external removal, server rollback, >10s break, mixin audit, live scoreboard, chat typing, advancements");
                    System.out.println("MMM_RELIABILITY_SMOKE_PASS");
                    client.scheduleStop();
                }
            } catch (Throwable failure) {
                done = true;
                failure.printStackTrace();
                try { Files.writeString(Path.of("mmm-reliability-smoke.txt"), "FAIL: " + failure); }
                catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }

    private static void count(long expected, String scenario) {
        long actual = MiningStats.getSessionBlocksMined();
        if (actual != expected) throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
    }

    private static void prepareArena(MinecraftClient client, BlockPos anchor) {
        client.getServer().execute(() -> {
            var server = client.getServer();
            server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
            var world = server.getOverworld();
            for (int x = -3; x <= 4; x++) for (int z = -3; z <= 4; z++) {
                world.setBlockState(anchor.add(x, -1, z), Blocks.STONE.getDefaultState());
                for (int y = 0; y <= 5; y++) world.setBlockState(anchor.add(x, y, z), Blocks.AIR.getDefaultState());
            }
            server.getPlayerManager().getPlayer(client.player.getUuid()).requestTeleport(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
        });
        client.player.setPosition(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
    }

    private void prepare(MinecraftClient client, net.minecraft.block.BlockState state, boolean fast) {
        prepared = false;
        BlockPos pos = target;
        client.getServer().execute(() -> {
            var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setStack(0, fast ? new ItemStack(Items.NETHERITE_PICKAXE) : ItemStack.EMPTY);
            player.removeStatusEffect(StatusEffects.HASTE);
            if (fast) player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 2000, 1));
            player.currentScreenHandler.sendContentUpdates();
            client.getServer().getOverworld().setBlockState(pos, state);
            client.execute(() -> prepared = true);
        });
        client.player.getInventory().setSelectedSlot(0);
        client.player.getInventory().setStack(0, fast ? new ItemStack(Items.NETHERITE_PICKAXE) : ItemStack.EMPTY);
    }
}
