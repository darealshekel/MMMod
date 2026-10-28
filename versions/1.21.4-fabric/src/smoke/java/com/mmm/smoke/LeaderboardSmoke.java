package com.mmm.smoke;

import com.mmm.ui.LeaderboardScreen;
import com.mmm.leaderboard.LeaderboardData;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotRecorder;

final class LeaderboardSmoke
{
    private int phase, ticks;
    private long start = System.currentTimeMillis();
    private LeaderboardScreen screen;
    private Object field(String name) throws Exception {
        var field = LeaderboardScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }
    private void click(String label) {
        var button = screen.children().stream().filter(child -> child instanceof ButtonWidget)
                .map(child -> (ButtonWidget) child).filter(b -> b.getMessage().getString().equals(label)
                        || (label.equals("Blocks") && b.getMessage().getString().equals("Blk"))).findFirst().orElseThrow();
        button.onPress();
    }
    void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (phase == 9) return;
            try {
                if (System.currentTimeMillis() - start > 120_000) throw new AssertionError("Leaderboard smoke timeout at phase " + phase);
                if (phase == 0 && client.currentScreen instanceof TitleScreen && client.getOverlay() == null) {
                    client.options.getGuiScale().setValue(2);
                    client.onResolutionChanged();
                    screen = new LeaderboardScreen(client.currentScreen);
                    client.setScreen(screen); phase = 1;
                } else if (phase == 1 && !(boolean) field("loading")) {
                    if (field("error") != null) throw new AssertionError(field("error"));
                    if (((LeaderboardData.Page) field("data")).rows().isEmpty()) throw new AssertionError("Missing live players");
                    ticks = 0; phase = 2;
                } else if (phase == 2 && ++ticks > 25) {
                    screenshot(client, "leaderboard-players.png");
                    click("Blocks");
                    var search = (TextFieldWidget) field("searchField");
                    screen.setFocused(search);
                    for (char c : "stone".toCharArray()) screen.charTyped(c, 0);
                    if (!search.getText().equals("stone")) throw new AssertionError("Block search typing failed");
                    screen.keyPressed(259, 0, 0);
                    if (!search.getText().equals("ston")) throw new AssertionError("Block search backspace failed");
                    search.setText("stone");
                    ticks = 0; phase = 3;
                } else if (phase == 3 && ++ticks > 25) {
                    screenshot(client, "leaderboard-block-picker.png");
                    var blocks = (java.util.List<?>) field("visibleBlocks");
                    int index = blocks.indexOf("minecraft:stone");
                    if (index < 0) throw new AssertionError("Stone not found");
                    var offset = LeaderboardScreen.class.getDeclaredField("offset");
                    offset.setAccessible(true); offset.set(screen, index);
                    screen.mouseClicked((int) field("x") + 30, (int) field("listTop") + 10, 0);
                    phase = 4;
                } else if (phase == 4 && !(boolean) field("loading")) {
                    if (field("error") != null) throw new AssertionError(field("error"));
                    if (!"minecraft:stone".equals(field("selectedBlock"))) throw new AssertionError("Wrong block selected");
                    if (((LeaderboardData.Page) field("data")).rows().isEmpty()) throw new AssertionError("Missing block data");
                    screen.mouseScrolled((int) field("x") + 30, (int) field("listTop") + 10, 0, -1);
                    ticks = 0; phase = 5;
                } else if (phase == 5 && ++ticks > 25) {
                    screenshot(client, "leaderboard-stone.png");
                    screen.init(client, 320, 240);
                    if ((int) field("listBottom") - (int) field("listTop") < 28) throw new AssertionError("No rows fit at minimum GUI size");
                    for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.widget.ClickableWidget widget)
                        if (widget.getX() < 0 || widget.getX() + widget.getWidth() > screen.width) throw new AssertionError("Widget overflows");
                    screen.close();
                    if (!(client.currentScreen instanceof TitleScreen)) throw new AssertionError("Back navigation failed");
                    phase = 9;
                    Files.writeString(Path.of("mmm-leaderboard-smoke-result.txt"), "PASS: live player/block data, typing, backspace, scrolling, selection, minimum layout, close");
                    System.out.println("MMM_LEADERBOARD_SMOKE_PASS");
                    client.scheduleStop();
                }
            } catch (Throwable failure) {
                phase = 9; failure.printStackTrace();
                try { Files.writeString(Path.of("mmm-leaderboard-smoke-result.txt"), "FAIL: " + failure); } catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }
    private void screenshot(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {});
    }
}
