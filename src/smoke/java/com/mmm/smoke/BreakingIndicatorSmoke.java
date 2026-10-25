package com.mmm.smoke;

import com.mmm.config.Configs;
import com.mmm.feature.BreakIndicatorState;
import com.mmm.feature.BreakingIndicatorRenderer;
import com.mmm.mixin.WorldRendererAccessor;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.MixinEnvironment;

final class BreakingIndicatorSmoke {
    private int ticks;
    private boolean done;
    private BlockPos pos;
    private final long started = System.nanoTime();

    void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done) return;
            try {
                if (System.nanoTime() - started > 60_000_000_000L) throw new AssertionError("Indicator smoke timeout");
                if (++ticks == 1) {
                    MixinEnvironment.getCurrentEnvironment().audit();
                    client.options.pauseOnLostFocus = false;
                    Configs.Generic.BREAKING_INDICATORS.setBooleanValue(true);
                    client.player.setYaw(0);
                    client.player.setPitch(0);
                    pos = BlockPos.ofFloored(client.player.getX(), client.player.getEyeY(), client.player.getZ() + 3);
                    client.world.setBlockState(pos, Blocks.STONE.getDefaultState());
                    var info = new BlockBreakingInfo(-100, pos);
                    info.setStage(5);
                    ((WorldRendererAccessor)client.worldRenderer).mmm$getBlockBreakingInfos().put(-100, info);
                } else if (ticks == 20) {
                    shot(client, "breaking-indicator-growing.png");
                    ((WorldRendererAccessor)client.worldRenderer).mmm$getBlockBreakingInfos().clear();
                    client.world.setBlockState(pos, Blocks.STONE_SLAB.getDefaultState());
                    if (!client.interactionManager.breakBlock(pos)) throw new AssertionError("Client break failed");
                    var pulse = pulses().active(System.nanoTime()).get(pos);
                    if (pulse == null) throw new AssertionError("Successful instant break did not trigger a completion pulse");
                    if (Math.abs(pulse.bounds().getLengthY() - 0.5) > 0.001) throw new AssertionError("Slab lost its shape");
                } else if (ticks == 30) {
                    if (!pulses().active(System.nanoTime()).isEmpty()) throw new AssertionError("Completion pulse did not expire");
                    client.world.setBlockState(pos, Blocks.STONE.getDefaultState());
                    Configs.Generic.BREAKING_INDICATORS.setBooleanValue(false);
                    client.interactionManager.breakBlock(pos);
                    if (!pulses().active(System.nanoTime()).isEmpty()) throw new AssertionError("Disabled feature created a pulse");
                    Configs.Generic.BREAKING_INDICATORS.setBooleanValue(true);
                    if (client.interactionManager.breakBlock(pos)) throw new AssertionError("Air break unexpectedly succeeded");
                    if (!pulses().active(System.nanoTime()).isEmpty()) throw new AssertionError("Failed break created a pulse");
                    client.world.setBlockState(pos, Blocks.STONE_SLAB.getDefaultState());
                    var info = new BlockBreakingInfo(-101, pos);
                    info.setStage(9);
                    ((WorldRendererAccessor)client.worldRenderer).mmm$getBlockBreakingInfos().put(-101, info);
                } else if (ticks == 40) {
                    shot(client, "breaking-indicator-slab.png");
                    ((WorldRendererAccessor)client.worldRenderer).mmm$getBlockBreakingInfos().clear();
                    client.interactionManager.breakBlock(pos);
                    Configs.Generic.BREAKING_INDICATORS.setBooleanValue(false);
                } else if (ticks == 41) {
                    if (!pulses().active(System.nanoTime()).isEmpty()) throw new AssertionError("Disabling did not clear existing pulses");
                    done = true;
                    Files.writeString(Path.of("mmm-breaking-indicator-smoke.txt"),
                            "PASS: mixin audit, actual successful/failed/disabled break hooks, slab bounds, pulse expiry and disable cleanup, live rendering screenshots");
                    System.out.println("MMM_BREAKING_INDICATOR_SMOKE_PASS");
                    client.scheduleStop();
                }
            } catch (Throwable failure) {
                done = true;
                failure.printStackTrace();
                try { Files.writeString(Path.of("mmm-breaking-indicator-smoke.txt"), "FAIL: " + failure); }
                catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }

    private static BreakIndicatorState pulses() throws Exception {
        var field = BreakingIndicatorRenderer.class.getDeclaredField("COMPLETED");
        field.setAccessible(true);
        return (BreakIndicatorState)field.get(null);
    }

    private static void shot(MinecraftClient client, String name) throws Exception {
        Path directory = Path.of("../build/breaking-indicator-smoke");
        Files.createDirectories(directory);
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(directory.resolve(name));
        }
    }
}
