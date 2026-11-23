package com.mmm.smoke;

import com.mmm.advancement.AchievementBadges;
import com.mmm.advancement.AchievementCardRenderer;
import com.mmm.advancement.MmmAdvancementDefinition;
import com.mmm.advancement.MmmAdvancementManager;
import com.mmm.advancement.MmmAdvancementTree;
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

/** Opt-in client probe. Install once after world load; never included in release sources. */
public final class SessionHoursBadgeSmoke
{
    private static final List<MmmAdvancementDefinition> BADGES = List.of(
            MmmAdvancementDefinition.FIRST_TIME, MmmAdvancementDefinition.ENTRY_LEVEL,
            MmmAdvancementDefinition.VETERAN, MmmAdvancementDefinition.NO_LIFE,
            MmmAdvancementDefinition.PART_OF_THE_MOD, MmmAdvancementDefinition.ENDURANCE_I,
            MmmAdvancementDefinition.ENDURANCE_II, MmmAdvancementDefinition.ENDURANCE_III,
            MmmAdvancementDefinition.ENDURANCE_IV, MmmAdvancementDefinition.ENDURANCE_V);
    private static final int AFTER_CARDS = 21 + BADGES.size() * 30;
    private static final Path RESULT = Path.of("mmm-achievement-badges-smoke.txt");
    private static final Path SHOTS = Path.of("achievement-badge-screenshots");
    private final AbstractTexture[] textures = new AbstractTexture[BADGES.size()];
    private int ticks;
    private boolean installed;
    private boolean done;
    private boolean unlocked;
    private volatile boolean capturing;
    private volatile Throwable captureFailure;
    private BadgeScreen screen;
    private long started;

    public void install() { install(() -> MinecraftClient.getInstance().scheduleStop()); }

    /** The callback runs on the client thread only after every asynchronous capture passes. */
    public void install(Runnable onComplete)
    {
        if (installed) throw new IllegalStateException("Badge smoke already installed");
        installed = true;
        started = System.nanoTime();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done) return;
            try
            {
                if (captureFailure != null) throw new AssertionError("Screenshot verification failed", captureFailure);
                check(System.nanoTime() - started < 180_000_000_000L, "Badge smoke timed out at tick " + ticks);
                if (capturing) return;
                check(client.player != null && client.world != null, "Install badge smoke after world load");
                ticks++;
                if (ticks == 1)
                {
                    resize(client, 1280, 720, 2);
                    client.options.pauseOnLostFocus = false;
                    client.options.hudHidden = false;
                    client.setScreen(null);
                    AchievementCardRenderer.clear();
                    MmmAdvancementManager.refreshForDisplay();
                }
                for (int index = 0; index < BADGES.size(); index++)
                {
                    var definition = BADGES.get(index);
                    if (ticks == 21 + index * 30)
                    {
                        unlocked = MmmAdvancementManager.isUnlocked(definition);
                        AchievementCardRenderer.clear();
                        AchievementCardRenderer.show(definition);
                    }
                    if (ticks == 41 + index * 30)
                    {
                        check(MmmAdvancementManager.isUnlocked(definition) == unlocked, "Card changed achievement award");
                        var id = AchievementBadges.forDefinition(definition).texture();
                        check(client.getResourceManager().getResource(id).isPresent(), "Missing badge resource " + id);
                        textures[index] = client.getTextureManager().getTexture(id);
                        shotCard(client, "card-" + definition.id() + ".png", "Achievement Unlocked!", definition.title(), index < 5 ? 1 : 2);
                    }
                }
                if (ticks == AFTER_CARDS)
                {
                    resize(client, 960, 540, 3);
                    AchievementCardRenderer.clear();
                    AchievementCardRenderer.show(MmmAdvancementDefinition.PART_OF_THE_MOD);
                }
                if (ticks == AFTER_CARDS + 20)
                    shotCard(client, "card-tier-5-small.png", "Achievement Unlocked!", "Part of the Mod", 1);
                if (ticks == AFTER_CARDS + 30)
                {
                    AchievementCardRenderer.clear();
                    AchievementCardRenderer.showCollection("minecraft:stone", "Stone", 2500);
                }
                if (ticks == AFTER_CARDS + 50)
                    shotCard(client, "collection-no-red-line.png", "Collection Milestone!", "Stone - 2.5K", 0);
                if (ticks == AFTER_CARDS + 60)
                {
                    AchievementCardRenderer.clear();
                    resize(client, 1280, 720, 2);
                    screen = new BadgeScreen(client);
                    client.setScreen(screen);
                    configureTree(0);
                }
                if (ticks == AFTER_CARDS + 80)
                {
                    verifyTree(client, "advancements-session-hours.png", 0);
                    screen.hoverTier = 4;
                }
                if (ticks == AFTER_CARDS + 90)
                    verifyTooltip(client, "session-hours-tooltip.png", 4);
                if (ticks == AFTER_CARDS + 100)
                {
                    screen.hoverTier = -1;
                    configureTree(5);
                }
                if (ticks == AFTER_CARDS + 120)
                {
                    verifyTree(client, "advancements-endurance.png", 5);
                    screen.hoverTier = 9;
                }
                if (ticks == AFTER_CARDS + 130)
                    verifyTooltip(client, "endurance-tooltip.png", 9);
                if (ticks == AFTER_CARDS + 140)
                {
                    screen.hoverTier = -1;
                    resize(client, 960, 540, 3);
                    configureTree(0);
                }
                if (ticks == AFTER_CARDS + 160)
                    verifyTree(client, "advancements-session-hours-small.png", 0);
                if (ticks == AFTER_CARDS + 170)
                {
                    client.setScreen(null);
                    AchievementCardRenderer.clear();
                    done = true;
                    Files.writeString(RESULT, "PASS: ten badge resources and rendered cards, unchanged awards, "
                            + "no red top stripe, collection item fallback, ten widget mappings and visible badges, "
                            + "session/endurance tooltip badges, small viewport, cached textures, async readback");
                    System.out.println("MMM_ACHIEVEMENT_BADGES_SMOKE_PASS");
                    onComplete.run();
                }
            }
            catch (Throwable failure)
            {
                done = true;
                failure.printStackTrace();
                try { Files.writeString(RESULT, "FAIL: " + failure); }
                catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }

    private void configureTree(int first) throws Exception
    {
        screen.selectTab(MmmAdvancementTree.rootEntry());
        var tab = (AdvancementTab) get(screen, "selectedTab");
        check(tab != null, "MMM advancements tab was not installed");
        var widget = tab.getWidget(MmmAdvancementTree.entry(BADGES.get(first)));
        screen.originY = 5 - widget.getY();
        set(tab, "originX", 5.0);
        set(tab, "originY", (double) screen.originY);
        set(tab, "initialized", true);
    }

    private void verifyTree(MinecraftClient client, String filename, int first) throws Exception
    {
        var tab = (AdvancementTab) get(screen, "selectedTab");
        int[][] positions = new int[5][2];
        for (int offset = 0; offset < 5; offset++)
        {
            int index = first + offset;
            var definition = BADGES.get(index);
            var widget = tab.getWidget(MmmAdvancementTree.entry(definition));
            var badge = AchievementBadges.forDefinition(definition);
            check(badge.equals(get(widget, "mmm$badge")), "Wrong widget badge: " + definition);
            check(textures[index] == client.getTextureManager().getTexture(badge.texture()), "Badge texture was reloaded");
            positions[offset][0] = (screen.width - 252) / 2 + 9 + 5 + widget.getX() + 4;
            positions[offset][1] = (screen.height - 140) / 2 + 18 + screen.originY + widget.getY() + 1;
        }
        double scale = client.getWindow().getScaleFactor();
        capture(client, filename, image -> {
            for (var position : positions) verifyBadgePixels(image, position[0], position[1], 24, scale, first == 0);
        });
    }

    private void shotCard(MinecraftClient client, String filename, String heading, String title, int badgeKind)
    {
        int width = Math.max(224, 58 + Math.max(client.textRenderer.getWidth(heading), client.textRenderer.getWidth(title)));
        float cardScale = Math.min(1F, Math.max(1, client.getWindow().getScaledWidth() - 16) / (float) width);
        float x = (client.getWindow().getScaledWidth() - width * cardScale) / 2F;
        float y = Math.min(72, client.getWindow().getScaledHeight() * 0.14F);
        double scale = client.getWindow().getScaleFactor();
        capture(client, filename, image -> {
            if (badgeKind != 0) verifyBadgePixels(image, x + 6 * cardScale, y + 7 * cardScale, 36 * cardScale, scale, badgeKind == 1);
            else verifyItemPixels(image, x + 12 * cardScale, y + 13 * cardScale, 24 * cardScale, scale);
            for (int offset = 5; offset < width - 5; offset++)
            {
                int color = image.getColorArgb((int) ((x + offset * cardScale) * scale), (int) ((y + 2 * cardScale) * scale));
                int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
                check(!(red > 160 && green < 80 && blue < 80), "Red top stripe still visible");
                check(red < 80 && green < 80 && blue < 80, "Card background was not rendered at expected position");
            }
        });
    }

    private void verifyTooltip(MinecraftClient client, String filename, int index) throws Exception
    {
        var tab = (AdvancementTab) get(screen, "selectedTab");
        check(((Number) get(tab, "alpha")).floatValue() > 0, "Advancement tooltip did not open");
        var widget = tab.getWidget(MmmAdvancementTree.entry(BADGES.get(index)));
        int x = (screen.width - 252) / 2 + 9 + 5 + widget.getX() + 4;
        int y = (screen.height - 140) / 2 + 18 + screen.originY + widget.getY() + 1;
        double scale = client.getWindow().getScaleFactor();
        capture(client, filename, image -> verifyBadgePixels(image, x, y, 24, scale, index < 5));
    }

    private static void verifyBadgePixels(NativeImage image, float x, float y, float size, double scale, boolean sessionHours)
    {
        int colored = 0;
        for (int px = (int) ((x + size * 0.25) * scale); px < (x + size * 0.75) * scale; px++)
            for (int py = (int) ((y + size * 0.25) * scale); py < (y + size * 0.75) * scale; py++)
            {
                int color = image.getColorArgb(px, py);
                int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
                if (sessionHours ? red > 140 && green > 80 && blue < 100 && red > blue * 1.5
                        : red > 65 && green > 65 && blue > 65 && Math.max(red, Math.max(green, blue)) > 100) colored++;
            }
        check(colored > 8, "Badge area missing expected pixels at " + x + "," + y);
    }

    private static void verifyItemPixels(NativeImage image, float x, float y, float size, double scale)
    {
        int visible = 0;
        for (int px = (int) ((x + 3) * scale); px < (x + size - 3) * scale; px++)
            for (int py = (int) ((y + 3) * scale); py < (y + size - 3) * scale; py++)
            {
                int color = image.getColorArgb(px, py);
                if ((color >> 16 & 255) > 45 && (color >> 8 & 255) > 45 && (color & 255) > 45) visible++;
            }
        check(visible > 8, "Collection item icon is blank");
    }

    private void capture(MinecraftClient client, String filename, ImageCheck verification)
    {
        capturing = true;
        ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> {
            try (image)
            {
                verification.verify(image);
                Files.createDirectories(SHOTS);
                image.writeTo(SHOTS.resolve(filename));
            }
            catch (Throwable failure) { captureFailure = failure; }
            finally { capturing = false; }
        });
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
    @FunctionalInterface private interface ImageCheck { void verify(NativeImage image) throws Exception; }

    private static final class BadgeScreen extends AdvancementsScreen
    {
        private int hoverTier = -1;
        private int originY;
        BadgeScreen(MinecraftClient client) { super(client.getNetworkHandler().getAdvancementHandler()); }

        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta)
        {
            if (hoverTier >= 0)
            {
                var definition = BADGES.get(hoverTier);
                mouseX = (width - 252) / 2 + 9 + 5 + (int) ((definition.x() + 1) * 28) + 16;
                mouseY = (height - 140) / 2 + 18 + originY + (int) (definition.y() * 27) + 13;
            }
            else { mouseX = 0; mouseY = 0; }
            super.render(context, mouseX, mouseY, delta);
        }
    }
}
