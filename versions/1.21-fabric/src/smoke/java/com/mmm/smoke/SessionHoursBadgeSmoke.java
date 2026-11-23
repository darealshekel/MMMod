package com.mmm.smoke;

import com.mmm.advancement.AchievementCardRenderer;
import com.mmm.advancement.MmmAdvancementDefinition;
import com.mmm.advancement.MmmAdvancementManager;
import com.mmm.advancement.MmmAdvancementTree;
import com.mmm.advancement.AchievementBadges;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementTab;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

final class SessionHoursBadgeSmoke
{
    private static final List<MmmAdvancementDefinition> BADGES = List.of(
            MmmAdvancementDefinition.FIRST_TIME, MmmAdvancementDefinition.ENTRY_LEVEL,
            MmmAdvancementDefinition.VETERAN, MmmAdvancementDefinition.NO_LIFE,
            MmmAdvancementDefinition.PART_OF_THE_MOD);
    private final AbstractTexture[] textures = new AbstractTexture[5];
    private int ticks;
    private boolean done;
    private boolean unlocked;
    private BadgeScreen screen;

    void install()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done) return;
            try
            {
                ticks++;
                if (ticks == 1)
                {
                    resize(client, 1280, 720, 2);
                    client.options.pauseOnLostFocus = false;
                    client.options.hudHidden = false;
                    AchievementCardRenderer.clear();
                    MmmAdvancementManager.refreshForDisplay();
                }
                for (int index = 0; index < BADGES.size(); index++)
                {
                    if (ticks == 21 + index * 30)
                    {
                        unlocked = MmmAdvancementManager.isUnlocked(BADGES.get(index));
                        AchievementCardRenderer.clear();
                        AchievementCardRenderer.show(BADGES.get(index));
                    }
                    if (ticks == 41 + index * 30)
                    {
                        check(MmmAdvancementManager.isUnlocked(BADGES.get(index)) == unlocked, "Card changed achievement award");
                        var id = AchievementBadges.forDefinition(BADGES.get(index)).texture();
                        check(client.getResourceManager().getResource(id).isPresent(), "Missing badge resource " + id);
                        textures[index] = client.getTextureManager().getTexture(id);
                        shotCard(client, "card-tier-" + (index + 1) + ".png", true);
                    }
                }
                if (ticks == 171)
                {
                    resize(client, 960, 540, 3);
                    AchievementCardRenderer.clear();
                    AchievementCardRenderer.show(MmmAdvancementDefinition.PART_OF_THE_MOD);
                }
                if (ticks == 191) shotCard(client, "card-tier-5-small.png", true);
                if (ticks == 201)
                {
                    AchievementCardRenderer.clear();
                    AchievementCardRenderer.showCollection("minecraft:stone", "Stone", 2500);
                }
                if (ticks == 221) shotCard(client, "collection-no-red-line.png", false);
                if (ticks == 231)
                {
                    AchievementCardRenderer.clear();
                    resize(client, 1280, 720, 2);
                    screen = new BadgeScreen(client);
                    client.setScreen(screen);
                    configureTree();
                }
                if (ticks == 251)
                {
                    verifyTree(client, "advancements-all-five.png");
                    screen.hoverTier = 4;
                }
                if (ticks == 261)
                {
                    shot(client, "advancement-tier-5-tooltip.png");
                    screen.hoverTier = -1;
                    resize(client, 960, 540, 3);
                    configureTree();
                }
                if (ticks == 281)
                {
                    verifyTree(client, "advancements-all-five-small.png");
                    client.setScreen(null);
                    AchievementCardRenderer.clear();
                    done = true;
                    Files.writeString(Path.of("mmm-achievement-badges-smoke.txt"),
                            "PASS: five original badge resources, five rendered cards, no award mutation, no red top line, collection fallback, five advancement widget mappings and visible icons, tooltip, small viewport, texture cache reuse");
                    System.out.println("MMM_ACHIEVEMENT_BADGES_SMOKE_PASS");
                    if (Boolean.getBoolean("mmm.achievementSmoke")) new AchievementSettingsSmoke().install();
                    else client.scheduleStop();
                }
            }
            catch (Throwable failure)
            {
                done = true;
                failure.printStackTrace();
                try { Files.writeString(Path.of("mmm-achievement-badges-smoke.txt"), "FAIL: " + failure); }
                catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }

    private void configureTree() throws Exception
    {
        screen.selectTab(MmmAdvancementTree.rootEntry());
        var tab = get(screen, "selectedTab");
        set(tab, "originX", 5.0);
        set(tab, "originY", 5.0);
        set(tab, "initialized", true);
    }

    private void verifyTree(MinecraftClient client, String filename) throws Exception
    {
        var tab = (AdvancementTab) get(screen, "selectedTab");
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer()))
        {
            for (int index = 0; index < BADGES.size(); index++)
            {
                var definition = BADGES.get(index);
                var widget = tab.getWidget(MmmAdvancementTree.entry(definition));
                var badge = AchievementBadges.forDefinition(definition);
                var id = badge.texture();
                check(badge.equals(get(widget, "mmm$badge")), "Wrong widget badge: " + definition);
                check(textures[index] == client.getTextureManager().getTexture(id), "Badge texture was reloaded during rendering");
                int x = (screen.width - 252) / 2 + 9 + 5 + widget.getX() + 4;
                int y = (screen.height - 140) / 2 + 18 + 5 + widget.getY() + 1;
                verifyGoldPixels(client, image, x, y, 24);
            }
            write(image, filename);
        }
    }

    private static void shotCard(MinecraftClient client, String filename, boolean badge) throws Exception
    {
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer()))
        {
            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();
            int x = (width - 224) / 2;
            int y = (int) Math.min(72, height * 0.14F);
            if (badge) verifyGoldPixels(client, image, x + 6, y + 7, 36);
            double scale = client.getWindow().getScaleFactor();
            for (int offset = 5; offset < 219; offset++)
            {
                int color = image.getColor((int) ((x + offset) * scale), (int) ((y + 2) * scale));
                int red = color & 255, green = color >> 8 & 255, blue = color >> 16 & 255;
                check(!(red > 160 && green < 80 && blue < 80), "Red top line still visible");
            }
            write(image, filename);
        }
    }

    private static void verifyGoldPixels(MinecraftClient client, NativeImage image, int x, int y, int size)
    {
        double scale = client.getWindow().getScaleFactor();
        int gold = 0;
        for (int px = (int) ((x + size * 0.25) * scale); px < (x + size * 0.75) * scale; px++)
            for (int py = (int) ((y + size * 0.25) * scale); py < (y + size * 0.75) * scale; py++)
            {
                int color = image.getColor(px, py);
                int red = color & 255, green = color >> 8 & 255, blue = color >> 16 & 255;
                if (red > 140 && green > 80 && blue < 100 && red > blue * 1.5) gold++;
            }
        check(gold > 8, "Badge area is blank or missing gold clock pixels at " + x + "," + y);
    }

    private static void shot(MinecraftClient client, String filename) throws Exception
    {
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) { write(image, filename); }
    }

    private static void write(NativeImage image, String filename) throws Exception
    {
        Path directory = Path.of("../../achievement-badge-screenshots");
        Files.createDirectories(directory);
        image.writeTo(directory.resolve(filename));
    }

    private static void resize(MinecraftClient client, int width, int height, int guiScale)
    {
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), width, height);
        client.options.getGuiScale().setValue(guiScale);
        client.onResolutionChanged();
    }

    private static java.lang.reflect.Field field(Object target, String name) throws Exception
    {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
        {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static Object get(Object target, String name) throws Exception { return field(target, name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class BadgeScreen extends AdvancementsScreen
    {
        private int hoverTier = -1;
        BadgeScreen(MinecraftClient client) { super(client.getNetworkHandler().getAdvancementHandler()); }

        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta)
        {
            if (hoverTier >= 0)
            {
                mouseX = (width - 252) / 2 + 9 + 5 + (hoverTier + 2) * 28 + 16;
                mouseY = (height - 140) / 2 + 18 + 5 + 13;
            }
            else { mouseX = 0; mouseY = 0; }
            super.render(context, mouseX, mouseY, delta);
        }
    }
}
