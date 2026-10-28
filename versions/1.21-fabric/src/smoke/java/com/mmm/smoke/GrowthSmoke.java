package com.mmm.smoke;

import com.mmm.config.Configs;
import com.mmm.config.FirstRunNotice;
import com.mmm.config.HudPresets;
import com.mmm.storage.SessionData;
import com.mmm.ui.ProgressShareScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

final class GrowthSmoke
{
    private int phase, ticks;
    private ProgressShareScreen screen;
    private final long started = System.currentTimeMillis();
    private final Path output = Path.of("mmm-growth-smoke-result.txt");
    private java.util.Set<Path> before;

    void install()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (phase == 99) return;
            try
            {
                if (System.currentTimeMillis() - started > 60_000) throw new AssertionError("Growth smoke timed out");
                Path directory = client.runDirectory.toPath().resolve("screenshots/mmm");
                if (phase == 0)
                {
                    checkWelcome(client);
                    HudPresets.compact();
                    if (Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue()) throw new AssertionError("Preset enabled sync");
                    before = images(directory);
                    var session = new SessionData(0);
                    session.endTimeMs = 4_174_000;
                    session.wallDurationMs = 5_535_000;
                    session.totalBlocks = 64_810;
                    session.bestHourBlocks = 57_209;
                    screen = ProgressShareScreen.session(null, session, "Offline smoke world");
                    client.setScreen(screen);
                    screen.init(client, 320, 240);
                    checkBounds(screen);
                    screen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                    phase = 1; ticks = 0;
                }
                else if (phase == 1 && ++ticks >= 8)
                {
                    button(screen, "Copy text").onPress();
                    String caption = client.keyboard.getClipboard();
                    if (!caption.contains("64,810") || !caption.contains("01:09:34")
                            || !caption.contains("01:32:15") || !caption.contains("57,209")
                            || caption.contains("https://")) throw new AssertionError("Wrong offline share values: " + caption);
                    button(screen, "Save PNG").onPress();
                    phase = 2;
                }
                else if (phase == 2)
                {
                    var files = images(directory); files.removeAll(before);
                    if (files.isEmpty() || !button(screen, "Save PNG").active) return;
                    checkImage(files.iterator().next());
                    screen = ProgressShareScreen.collection(null, "Stone", "minecraft:stone", 6_704_163, "Global Total", new ItemStack(Items.STONE));
                    client.setScreen(screen);
                    screen.init(client, 960, 540);
                    checkBounds(screen);
                    screen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                    phase = 3; ticks = 0; before = images(directory);
                }
                else if (phase == 3 && ++ticks >= 8)
                {
                    button(screen, "Copy text").onPress();
                    if (!client.keyboard.getClipboard().contains("6,704,163")) throw new AssertionError("Collection total changed in export");
                    button(screen, "Save PNG").onPress();
                    phase = 4;
                }
                else if (phase == 4)
                {
                    var files = images(directory); files.removeAll(before);
                    if (files.isEmpty() || !button(screen, "Save PNG").active) return;
                    checkImage(files.iterator().next());
                    phase = 99;
                    Files.writeString(output, "PASS: existing runtime checks, one-time persisted welcome with command links, offline session/collection exports, pause-aware IGT and wall time, exact counts, small and large GUI bounds, nonblank cropped PNGs, compact HUD does not enable sync");
                    client.scheduleStop();
                }
            }
            catch (Throwable failure)
            {
                phase = 99;
                failure.printStackTrace();
                try { Files.writeString(output, "FAIL: " + failure); } catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }

    private static void checkWelcome(net.minecraft.client.MinecraftClient client) throws Exception
    {
        client.setScreen(null);
        FirstRunNotice.SEEN.setBooleanValue(false);
        var field = net.minecraft.client.gui.hud.ChatHud.class.getDeclaredField("messages");
        field.setAccessible(true);
        var messages = (java.util.List<?>) field.get(client.inGameHud.getChatHud());
        int before = messages.size();
        FirstRunNotice.tick(client);
        if (!FirstRunNotice.SEEN.getBooleanValue() || messages.size() != before + 1)
            throw new AssertionError("Welcome did not appear exactly once");
        var text = ((net.minecraft.client.gui.hud.ChatHudLine) messages.getFirst()).content();
        var commands = text.getSiblings().stream().map(part -> part.getStyle().getClickEvent())
                .filter(java.util.Objects::nonNull).map(net.minecraft.text.ClickEvent::getValue).toList();
        if (!commands.contains("/mmm") || !commands.contains("/mmm collections"))
            throw new AssertionError("Welcome command links missing");
        for (int i = 0; i < 20; i++) FirstRunNotice.tick(client);
        if (messages.size() != before + 1) throw new AssertionError("Welcome repeated");
        if (!com.mmm.storage.AsyncPersistence.flush(java.time.Duration.ofSeconds(3)))
            throw new AssertionError("Welcome preference save did not finish");
        FirstRunNotice.SEEN.setBooleanValue(false);
        Configs.loadFromFile();
        if (!FirstRunNotice.SEEN.getBooleanValue()) throw new AssertionError("Welcome dismissal was not persisted");
    }

    private static java.util.Set<Path> images(Path directory) throws Exception
    {
        if (!Files.exists(directory)) return new java.util.HashSet<>();
        try (var paths = Files.list(directory)) { return paths.filter(file -> file.toString().endsWith(".png")).collect(java.util.stream.Collectors.toSet()); }
    }

    private static void checkImage(Path file) throws Exception
    {
        try (var image = NativeImage.read(Files.readAllBytes(file)))
        {
            if (image.getWidth() < 100 || image.getHeight() < 100) throw new AssertionError("Export size invalid");
            boolean signal = false, text = false;
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++)
                {
                    int pixel = image.getColor(x, y);
                    if ((pixel & 0xFFFFFF) == 0x0000D9) signal = true;
                    if ((pixel & 0xFF) > 180 && (pixel >> 8 & 0xFF) > 180) text = true;
                }
            if (!signal || !text) throw new AssertionError("PNG missing MMM accent or text");
        }
    }

    private static ButtonWidget button(Screen screen, String name)
    {
        return screen.children().stream().filter(child -> child instanceof ButtonWidget button && button.getMessage().getString().equals(name))
                .map(child -> (ButtonWidget) child).findFirst().orElseThrow();
    }

    private static void checkBounds(Screen screen)
    {
        for (var child : screen.children()) if (child instanceof ButtonWidget button
                && (button.getX() < 0 || button.getY() < 0 || button.getX() + button.getWidth() > screen.width
                || button.getY() + button.getHeight() > screen.height)) throw new AssertionError("Share control outside screen");
    }
}
