package com.mmm.smoke;

import com.mmm.config.Configs;
import com.mmm.hud.MinedBlockOverlayRenderer;
import com.mmm.hud.MinedBlockOverlayState;
import com.mmm.hud.HudMoveScreen;
import com.mmm.hud.HudModuleId;
import com.mmm.timer.TimerHudRenderer;
import com.mmm.tracker.CollectionWebsiteClient;
import com.mmm.tracker.LiveCollections;
import com.mmm.tracker.MiningStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.util.math.BlockPos;

/** Isolated client fixtures only; this source set is never included in the shipped mod. */
final class BlockOverlaySmoke
{
    private int phase, ticks, blockSequence;
    private long started, switched;
    private boolean fullShot, fadeShot;
    private HudMoveScreen editor;
    private int pendingScreenshots;

    void install()
    {
        started = System.nanoTime();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (phase == 99) return;
            try
            {
                if (System.nanoTime() - started > 90_000_000_000L) throw new AssertionError("Overlay smoke timed out");
                if (client.player == null || client.world == null || ++ticks < 40) return;
                if (phase == 0)
                {
                    client.options.pauseOnLostFocus = false;
                    Configs.Generic.MINED_BLOCK_OVERLAY.setBooleanValue(true);
                    Configs.Generic.MINED_BLOCK_OVERLAY_X.resetToDefault();
                    Configs.Generic.MINED_BLOCK_OVERLAY_Y.resetToDefault();
                    Configs.Generic.MINED_BLOCK_OVERLAY_SCALE.resetToDefault();
                    Configs.Generic.HUD_TEXT_SHADOW.setBooleanValue(true);
                    Configs.Generic.WORLD_PLAYTIME_ABOVE_HOTBAR.setBooleanValue(true);
                    resize(client, 1280, 720, 2);
                    seed("minecraft:stone", 3_785_086);
                    long before = LiveCollections.counts(null).get("minecraft:stone");
                    mine(Blocks.STONE);
                    MinedBlockOverlayRenderer.tick(client);
                    check(progress().count() == before + 1, "Overlay missed an unsynced accepted block");
                    var position = new BlockPos(8000 + blockSequence, 64, 8000);
                    MiningStats.recordBlockMined(Blocks.STONE, position, Blocks.STONE.getDefaultState(), false);
                    MiningStats.recordBlockMined(Blocks.STONE, position, Blocks.STONE.getDefaultState(), false);
                    // The existing sanity guard permits three breaks per coordinate (replaced blocks).
                    MiningStats.recordBlockMined(Blocks.DIRT, position, Blocks.DIRT.getDefaultState(), false);
                    check(LiveCollections.counts(null).get("minecraft:stone") == before + 3, "Wrong accepted-break count");
                    check(state().blockId().equals("minecraft:stone"), "Rejected break switched overlay");
                    seed("minecraft:netherrack", 50000);
                    seedRank(client, "minecraft:netherrack", 28);
                    mine(Blocks.NETHERRACK);
                    switched = System.nanoTime();
                    phase = 1;
                }
                else if (phase == 1)
                {
                    if (ticks % 5 == 0) mine(Blocks.NETHERRACK);
                    long elapsed = System.nanoTime() - switched;
                    if (elapsed > 2_000_000_000L && !fullShot) { shot(client, "01-live-progress.png"); fullShot = true; }
                    check(state().opacity(System.nanoTime()) > 0.99F, "Continued netherrack mining faded the overlay");
                    if (elapsed > 12_000_000_000L)
                    {
                        var view = field("view");
                        var heading = view.getClass().getDeclaredMethod("heading"); heading.setAccessible(true);
                        check(heading.invoke(view).toString().endsWith(" #28"), "Actual collection rank is missing");
                        check(progress().count() > 50000, "Continued mining did not update the count");
                        shot(client, "09-continuous-netherrack.png");
                        switched = System.nanoTime();
                        phase = 10;
                    }
                }
                else if (phase == 10)
                {
                    long elapsed = System.nanoTime() - switched;
                    if (state().opacity(System.nanoTime()) < 0.6F && !fadeShot)
                    {
                        shot(client, "02-fading.png");
                        fadeShot = true;
                    }
                    if (elapsed > 12_000_000_000L)
                    {
                        check(state().opacity(System.nanoTime()) == 0, "Overlay did not hide after mining stopped");
                        shot(client, "03-hidden.png");
                        mine(Blocks.NETHERRACK);
                        MinedBlockOverlayRenderer.tick(client);
                        check(state().opacity(System.nanoTime()) > 0.99F, "Resuming same block did not restore overlay");
                        phase = 11;
                        ticks = 0;
                    }
                }
                else if (phase == 11)
                {
                    shot(client, "10-resumed-netherrack.png");
                    mine(Blocks.DIRT);
                    MinedBlockOverlayRenderer.tick(client);
                    check(state().opacity(System.nanoTime()) > 0.99F, "Different block did not reappear");
                    check(progress().collectionId().equals("minecraft:dirt"), "Wrong switched collection");
                    phase = 2;
                    ticks = 0;
                }
                else if (phase == 2)
                {
                    shot(client, "04-switched.png");
                    Configs.Generic.MINED_BLOCK_OVERLAY.setBooleanValue(false);
                    mine(Blocks.GRAVEL);
                    check(state().blockId() == null, "Disabled overlay accepted a block");
                    Configs.Generic.MINED_BLOCK_OVERLAY.setBooleanValue(true);
                    seed("minecraft:diamond_ore", 999);
                    mine(Blocks.DIAMOND_ORE);
                    MinedBlockOverlayRenderer.tick(client);
                    check(progress().complete(), "Final milestone did not complete");
                    check(progress().fraction() == 1, "Completed percentage was not 100%");
                    phase = 3;
                    ticks = 0;
                }
                else if (phase == 3)
                {
                    shot(client, "05-complete.png");
                    resize(client, 640, 480, 2);
                    seed("minecraft:deepslate_diamond_ore", 123_456_788);
                    mine(Blocks.DEEPSLATE_DIAMOND_ORE);
                    MinedBlockOverlayRenderer.tick(client);
                    phase = 4;
                    ticks = 0;
                }
                else if (phase == 4)
                {
                    shot(client, "06-compact-long-number.png");
                    MinedBlockOverlayRenderer.clear();
                    check(state().blockId() == null && progress() == null, "Disconnect cleanup retained state");
                    mine(Blocks.DEEPSLATE_DIAMOND_ORE);
                    MinedBlockOverlayRenderer.tick(client);
                    check(state().opacity(System.nanoTime()) > 0.99F, "Fresh world did not show same block");
                    MinedBlockOverlayRenderer.clear();
                    resize(client, 1280, 720, 2);
                    phase = 5;
                    ticks = 0;
                }
                else if (phase == 5)
                {
                    editor = new HudMoveScreen(null);
                    client.setScreen(editor);
                    check(state().blockId() == null, "Editor preview restarted live mining timer");
                    var bounds = TimerHudRenderer.getBounds(client, HudModuleId.MINED_BLOCKS);
                    check(editor.mouseClicked(click(bounds[0] + 10, bounds[1] + 10), false), "Overlay cannot be selected in Move HUD");
                    check(editor.mouseDragged(click(30, 140), 0, 0), "Overlay cannot be dragged");
                    editor.mouseReleased(click(30, 140));
                    bounds = TimerHudRenderer.getBounds(client, HudModuleId.MINED_BLOCKS);
                    check(Math.abs(bounds[0] - 20) <= 1 && Math.abs(bounds[1] - 130) <= 1, "Dragged position wrong");
                    editor.mouseScrolled(30, 140, 0, 1);
                    check(Math.abs(Configs.Generic.MINED_BLOCK_OVERLAY_SCALE.getDoubleValue() - 1.05) < 0.001, "Scroll did not resize overlay");
                    press("+");
                    check(Math.abs(Configs.Generic.MINED_BLOCK_OVERLAY_SCALE.getDoubleValue() - 1.10) < 0.001, "+ did not resize overlay");
                    press("-");
                    check(Math.abs(Configs.Generic.MINED_BLOCK_OVERLAY_SCALE.getDoubleValue() - 1.05) < 0.001, "- did not resize overlay");
                    phase = 6;
                    ticks = 0;
                }
                else if (phase == 6)
                {
                    shot(client, "07-move-hud.png");
                    int x = Configs.Generic.MINED_BLOCK_OVERLAY_X.getIntegerValue();
                    int y = Configs.Generic.MINED_BLOCK_OVERLAY_Y.getIntegerValue();
                    double scale = Configs.Generic.MINED_BLOCK_OVERLAY_SCALE.getDoubleValue();
                    editor.close();
                    Configs.Generic.MINED_BLOCK_OVERLAY_X.resetToDefault();
                    Configs.Generic.MINED_BLOCK_OVERLAY_Y.resetToDefault();
                    Configs.Generic.MINED_BLOCK_OVERLAY_SCALE.resetToDefault();
                    Configs.loadFromFile();
                    check(Configs.Generic.MINED_BLOCK_OVERLAY_X.getIntegerValue() == x, "X was not persisted");
                    check(Configs.Generic.MINED_BLOCK_OVERLAY_Y.getIntegerValue() == y, "Y was not persisted");
                    check(Configs.Generic.MINED_BLOCK_OVERLAY_SCALE.getDoubleValue() == scale, "Scale was not persisted");
                    mine(Blocks.STONE);
                    MinedBlockOverlayRenderer.tick(client);
                    phase = 7;
                    ticks = 0;
                }
                else if (phase == 7)
                {
                    shot(client, "08-moved-overlay.png");
                    var bounds = MinedBlockOverlayRenderer.getBounds(client);
                    phase = 8;
                    ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> {
                        try (image)
                        {
                            int scale = client.getWindow().getScaleFactor();
                            int color = image.getColorArgb((bounds[0] + 1) * scale, (bounds[1] + 1) * scale);
                            check((color & 0xFFFFFF) == 0x111111, "Overlay background is not #111111");
                            double overlayScale = Configs.Generic.MINED_BLOCK_OVERLAY_SCALE.getDoubleValue();
                            int barX = (int) Math.round((bounds[0] + 40 * overlayScale) * scale);
                            int barY = (int) Math.round((bounds[1] + 30 * overlayScale) * scale);
                            check((image.getColorArgb(barX, barY) & 0xFFFFFF) == 0x111111, "Removed progress bar is still rendered");
                            boolean whiteTitle = false;
                            for (int py = (int) ((bounds[1] + 5 * overlayScale) * scale); py < (bounds[1] + 14 * overlayScale) * scale; py++)
                                for (int px = (int) ((bounds[0] + 36 * overlayScale) * scale); px < (bounds[0] + 170 * overlayScale) * scale; px++)
                                {
                                    int pixel = image.getColorArgb(px, py) & 0xFFFFFF;
                                    int red = pixel >> 16 & 255, green = pixel >> 8 & 255, blue = pixel & 255;
                                    // The vanilla font atlas shades a white tint to #fcfcfc.
                                    if (red >= 245 && Math.abs(red - green) <= 2 && Math.abs(red - blue) <= 2) whiteTitle = true;
                                    check(!(red > 200 && green > 120 && green < 240 && blue < 180), "Overlay title is still gold");
                                }
                            check(whiteTitle, "Overlay title is not white");
                            phase = 9;
                        }
                        catch (Throwable failure) { fail(client, failure); }
                    });
                }
                else if (phase == 9 && pendingScreenshots == 0)
                {
                    client.setScreen(new HudMoveScreen(null));
                    editor = (HudMoveScreen) client.currentScreen;
                    press("Reset");
                    check(Configs.Generic.MINED_BLOCK_OVERLAY_X.getIntegerValue() == -1, "Reset did not restore auto X");
                    check(Configs.Generic.MINED_BLOCK_OVERLAY_Y.getIntegerValue() == -1, "Reset did not restore auto Y");
                    check(Configs.Generic.MINED_BLOCK_OVERLAY_SCALE.getDoubleValue() == 1, "Reset did not restore scale");
                    editor.close();
                    Files.writeString(Path.of("mmm-smoke-result.txt"), "PASS: continuous netherrack counts, real rank, idle fade, same-block revival, switching, toggle, completion, compact layout, cleanup, Move HUD preview, drag/resize, persistence, #111111 background, white title, no progress bar, reset");
                    System.out.println("MMM_BLOCK_OVERLAY_SMOKE_PASS");
                    phase = 99;
                    client.scheduleStop();
                }
            }
            catch (Throwable failure)
            {
                fail(client, failure);
            }
        });
    }

    private void mine(Block block)
    {
        MiningStats.recordBlockMined(block, new BlockPos(8000 + ++blockSequence, 64, 8000), block.getDefaultState(), false);
    }

    private void press(String label)
    {
        for (var child : editor.children())
            if (child instanceof ButtonWidget button && button.getMessage().getString().equals(label))
            {
                button.onPress(new net.minecraft.client.input.KeyInput(257, 0, 0));
                return;
            }
        throw new AssertionError("Missing Move HUD control: " + label);
    }

    private static void seed(String block, long count)
    {
        var counts = new HashMap<>(LiveCollections.counts(null));
        counts.put(block, count);
        LiveCollections.counts(new CollectionWebsiteClient.Snapshot(counts, 2, System.currentTimeMillis()));
    }

    private static Object field(String name) throws Exception
    {
        var field = MinedBlockOverlayRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void seedRank(MinecraftClient client, String block, long rank) throws Exception
    {
        var type = com.mmm.tracker.CollectionRankClient.class;
        var cache = type.getDeclaredField("CACHE"); cache.setAccessible(true);
        var cachedType = Class.forName("com.mmm.tracker.CollectionRankClient$Cached");
        var constructor = cachedType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var value = new com.mmm.tracker.CollectionRankClient.Rank(com.mmm.tracker.CollectionRankClient.Status.RANKED, rank, 1000);
        var cached = constructor.newInstance(java.util.concurrent.CompletableFuture.completedFuture(value), System.currentTimeMillis() + 120000);
        synchronized (type)
        {
            ((java.util.Map) cache.get(null)).put(block + ":" + client.player.getName().getString().toLowerCase(java.util.Locale.ROOT), cached);
        }
    }

    private static MinedBlockOverlayState state() throws Exception { return (MinedBlockOverlayState) field("STATE"); }
    private static MinedBlockOverlayState.Progress progress() throws Exception { return (MinedBlockOverlayState.Progress) field("progress"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private void fail(MinecraftClient client, Throwable failure)
    {
        phase = 99;
        failure.printStackTrace();
        try { Files.writeString(Path.of("mmm-smoke-result.txt"), "FAIL: " + failure); }
        catch (Exception ignored) {}
        client.scheduleStop();
    }

    private static net.minecraft.client.gui.Click click(double x, double y)
    {
        return new net.minecraft.client.gui.Click(x, y, new net.minecraft.client.input.MouseInput(0, 0));
    }

    private void shot(MinecraftClient client, String name) throws Exception
    {
        Path directory = Path.of("../block-overlay-screenshots");
        Files.createDirectories(directory);
        pendingScreenshots++;
        ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> {
            try (image) { image.writeTo(directory.resolve(name)); }
            catch (Throwable failure) { fail(client, failure); }
            finally { pendingScreenshots--; }
        });
    }

    private static void resize(MinecraftClient client, int width, int height, int scale)
    {
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), width, height);
        client.options.getGuiScale().setValue(scale);
        client.onResolutionChanged();
    }
}
