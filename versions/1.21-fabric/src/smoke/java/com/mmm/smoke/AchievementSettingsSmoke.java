package com.mmm.smoke;

import com.mmm.advancement.AchievementCardRenderer;
import com.mmm.advancement.AchievementCardState;
import com.mmm.advancement.MmmAdvancementDefinition;
import com.mmm.advancement.MmmAdvancementManager;
import com.mmm.ui.MmmSettingsScreen;
import com.mmm.config.Configs;
import com.mmm.storage.MiningCalendarStore;
import com.mmm.tracker.CollectionLiveProgress;
import com.mmm.tracker.CollectionWebsiteClient;
import com.mmm.tracker.LiveCollections;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotRecorder;

final class AchievementSettingsSmoke
{
    private int ticks;
    private boolean done;
    private MmmSettingsScreen screen;
    private boolean unlocked;

    void install()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done) return;
            try
            {
                ticks++;
                if (ticks == 1)
                {
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    client.options.pauseOnLostFocus = false;
                    client.options.getGuiScale().setValue(2);
                    client.onResolutionChanged();
                    AchievementCardRenderer.clear();
                    screen = new MmmSettingsScreen(null);
                    List<String> organized = labels(screen);
                    organized.remove("Test Achievement");
                    var raw = new ArrayList<>();
                    var create = MmmSettingsScreen.class.getDeclaredMethod("createSections", List.class);
                    create.setAccessible(true);
                    create.invoke(null, raw);
                    List<String> original = labels(raw);
                    Collections.sort(organized);
                    Collections.sort(original);
                    check(original.equals(organized), "Settings were removed or duplicated");
                    client.setScreen(screen);
                    check(get(MmmAdvancementManager.class, "notificationRenderer") != null, "Card handler missing");
                }
                if (ticks == 10)
                {
                    shot(client, "settings-overview.png");
                    ((TextFieldWidget) get(screen, "searchField")).setText("rainbow");
                }
                if (ticks == 15)
                {
                    check(((List<?>) get(screen, "visibleSections")).size() == 1, "Search did not filter closed sections");
                    shot(client, "settings-search.png");
                    ((TextFieldWidget) get(screen, "searchField")).setText("Test Achievement");
                }
                if (ticks == 20)
                {
                    shot(client, "settings-preview-button.png");
                    unlocked = MmmAdvancementManager.isUnlocked(MmmAdvancementDefinition.DIG_MASTER);
                    List<?> targets = (List<?>) get(screen, "clickTargets");
                    Object button = targets.getLast();
                    int x = (int) invoke(button, "x"), y = (int) invoke(button, "y");
                    screen.mouseClicked(x + 4, y + 4, 0);
                    check(client.currentScreen == null, "Preview button did not return to game");
                    check(state().current() == MmmAdvancementDefinition.DIG_MASTER, "Preview did not queue card");
                }
                if (ticks == 24) shot(client, "achievement-sliding.png");
                if (ticks == 40)
                {
                    shot(client, "achievement-card.png");
                    check(MmmAdvancementManager.isUnlocked(MmmAdvancementDefinition.DIG_MASTER) == unlocked,
                            "Preview changed achievement progress");
                    client.options.hudHidden = true;
                }
                if (ticks == 70)
                {
                    check(state().current() == MmmAdvancementDefinition.DIG_MASTER, "Hidden HUD lost card");
                    client.options.hudHidden = false;
                    client.options.getGuiScale().setValue(3);
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 960, 540);
                    client.onResolutionChanged();
                }
                if (ticks == 80)
                {
                    shot(client, "achievement-large-gui.png");
                    client.setScreen(new MmmSettingsScreen(null));
                }
                if (ticks == 90)
                {
                    shot(client, "settings-large-gui.png");
                    client.setScreen(null);
                    AchievementCardRenderer.clear();
                    check(state().currentCard() == null, "Disconnect cleanup did not clear card");
                    verifyCollections(client);
                }
                if (ticks == 110)
                {
                    shot(client, "collection-card-large-gui.png");
                    client.options.getGuiScale().setValue(2);
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    client.onResolutionChanged();
                }
                if (ticks == 125)
                {
                    shot(client, "collection-card.png");
                    AchievementCardRenderer.clear();
                    LiveCollections.counts(new CollectionWebsiteClient.Snapshot(java.util.Map.of("minecraft:stone", 1000000L), 1));
                    check(state().currentCard() == null, "Website history replayed a card");
                    LiveCollections.mined("minecraft:stone");
                    check(state().currentCard() == null, "Mining after history import replayed a card");
                    set(LiveCollections.class, "progress", CollectionLiveProgress.load(
                            ((CollectionLiveProgress) get(LiveCollections.class, "progress")).save()));
                    LiveCollections.mined("minecraft:stone");
                    check(state().currentCard() == null, "Reload replayed a card");
                    verifyDailyGoalChat(client);
                    verifyRemoteMilestoneChat(client);
                    screen = new MmmSettingsScreen(null);
                    client.setScreen(screen);
                }
                if (ticks == 135) clickNavigation("HUD >");
                if (ticks == 145) clickNavigation("HUD content >");
                if (ticks == 155) clickNavigation("Mining totals >");
                if (ticks == 165)
                {
                    check(pageTitle().equals("Mining totals"), "Category drill did not reach detail page");
                    check(((List<?>) invoke(get(screen, "currentPage"), "rows")).size() <= 8, "Detail page is too dense");
                    shot(client, "settings-category-detail.png");
                    var back = (net.minecraft.client.gui.widget.ButtonWidget) get(screen, "backButton");
                    screen.mouseClicked(back.getX() + 4, back.getY() + 4, 0);
                    check(pageTitle().equals("HUD content"), "Back button did not return to topics");
                }
                if (ticks == 175)
                {
                    screen.keyPressed(256, 0, 0);
                    check(client.currentScreen == screen && pageTitle().equals("HUD"), "Escape did not return one category");
                }
                if (ticks == 185)
                {
                    screen.keyPressed(256, 0, 0);
                    check(pageTitle().equals("Settings"), "Escape did not return to category home");
                    client.options.getGuiScale().setValue(3);
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 960, 540);
                    client.onResolutionChanged();
                }
                if (ticks == 195) clickNavigation("HUD >");
                if (ticks == 205) clickNavigation("HUD content >");
                if (ticks == 215) clickNavigation("Mining totals >");
                if (ticks == 225)
                {
                    check(pageTitle().equals("Mining totals"), "Small viewport category drill failed");
                    shot(client, "settings-category-detail-small.png");
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 960, 720);
                    client.options.getGuiScale().setValue(3);
                    client.onResolutionChanged();
                }
                if (ticks == 235)
                {
                    check(screen.width == 320 && screen.height == 240, "Actual GUI scale 3 was not applied");
                    screen.mouseScrolled(screen.width - 10, screen.height - 10, 0, -1);
                    check((double) get(screen, "scrollY") > 0, "Narrow detail page did not scroll");
                }
                if (ticks == 245)
                {
                    shot(client, "settings-category-detail-gui3-scrolled.png");
                    // Also stress the short GUI layout beyond vanilla's automatic scale clamp.
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 960, 540);
                    client.onResolutionChanged();
                    client.getWindow().setScaleFactor(3.0);
                    screen.resize(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                    set(screen, "scrollY", 0.0D);
                }
                if (ticks == 255) screen.mouseScrolled(screen.width - 10, screen.height - 10, 0, -1);
                if (ticks == 265)
                {
                    shot(client, "settings-category-detail-forced-gui3-scrolled.png");
                    done = true;
                    Files.writeString(Path.of("mmm-achievement-settings-smoke.txt"),
                            "PASS: settings preserved, category/topic navigation, Back/Escape, small detail page, global search, preview click, renderer hook, no unlock mutation, hidden HUD, GUI scales, cleanup, live collection card, one local chat with real streak, daily goal streak, sender-specific remote streak and old-event compatibility, no public duplicate, history/reload suppression");
                    System.out.println("MMM_ACHIEVEMENT_SETTINGS_SMOKE_PASS");
                    client.scheduleStop();
                }
            }
            catch (Throwable failure)
            {
                done = true;
                failure.printStackTrace();
                try { Files.writeString(Path.of("mmm-achievement-settings-smoke.txt"), "FAIL: " + failure); }
                catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }

    private void clickNavigation(String label) throws Exception
    {
        for (Object item : (List<?>) get(screen, "navigationButtons"))
        {
            var button = (net.minecraft.client.gui.widget.ButtonWidget) item;
            if (button.getMessage().getString().equals(label))
            {
                check(button.visible && button.active, "Category button is not usable: " + label);
                screen.mouseClicked(button.getX() + 4, button.getY() + 4, 0);
                check(screen.getFocused() != button, "Removed category button retained focus");
                return;
            }
        }
        throw new AssertionError("Missing category button: " + label);
    }

    private String pageTitle() throws Exception { return (String) invoke(get(screen, "currentPage"), "title"); }

    private static void verifyCollections(MinecraftClient client) throws Exception
    {
        check(!com.mmm.sync.WebsiteLinkManager.isCurrentPlayerLinked(), "Smoke must stay unlinked");
        Configs.Generic.SHARE_COLLECTION_MILESTONES.setBooleanValue(false);
        LiveCollections.counts(null);
        var progress = new CollectionLiveProgress();
        progress.seed(java.util.Map.of("minecraft:stone", 2499L));
        progress.finishCatchUp();
        set(LiveCollections.class, "progress", progress);
        MiningCalendarStore.recordBlock(System.currentTimeMillis());
        int streak = MiningCalendarStore.currentMiningStreakDays(System.currentTimeMillis());
        List<?> chat = (List<?>) get(client.inGameHud.getChatHud(), "messages");
        int before = chat.size();
        int outbox = ((java.util.Deque<?>) get(LiveCollections.class, "outbox")).size();
        boolean achievementUnlocked = MmmAdvancementManager.isUnlocked(MmmAdvancementDefinition.DIG_MASTER);
        LiveCollections.mined("minecraft:stone");
        check(state().currentCard() != null && state().currentCard().milestone() == 2500,
                "New live collection milestone did not queue card");
        check(state().currentCard().collectionId().equals("minecraft:stone"), "Wrong collection icon");
        check(chat.size() == before + 1, "Collection chat was missing or duplicated");
        String text = ((net.minecraft.text.Text) invoke(chat.getFirst(), "content")).getString();
        check(text.contains("Stone Collection 2.5K") && text.endsWith(com.mmm.social.MilestoneChatText.streakSuffix(streak)),
                "Collection chat did not use the authoritative calendar streak: " + text);
        check(((java.util.Deque<?>) get(LiveCollections.class, "outbox")).size() == outbox,
                "Local collection milestone also queued a public notification");
        LiveCollections.mined("minecraft:stone");
        check(chat.size() == before + 1, "Collection milestone announced twice");
        check(MmmAdvancementManager.isUnlocked(MmmAdvancementDefinition.DIG_MASTER) == achievementUnlocked,
                "Collection card changed achievement progress");
    }

    private static void verifyDailyGoalChat(MinecraftClient client) throws Exception
    {
        com.mmm.config.FeatureToggle.MMM_NOTIFICATIONS.setBooleanValue(true);
        Configs.Generic.GOAL_PICKAXE_ANIMATION.setBooleanValue(false);
        Configs.Generic.SHARE_GOAL_MILESTONES.setBooleanValue(false);
        com.mmm.tracker.GoalNotificationManager.clear();
        List<?> chat = (List<?>) get(client.inGameHud.getChatHud(), "messages");
        int before = chat.size();
        var progress = new com.mmm.tracker.MiningStats.GoalProgress("Daily Goal", true, 10000, 40000);
        com.mmm.tracker.GoalNotificationManager.onGoalProgressChanged(9999, progress);
        check(chat.size() == before + 1, "Daily goal chat was missing or duplicated");
        String text = ((net.minecraft.text.Text) invoke(chat.getFirst(), "content")).getString();
        check(text.contains("25% reached") && text.endsWith(com.mmm.social.MilestoneChatText.streakSuffix(
                MiningCalendarStore.currentMiningStreakDays(System.currentTimeMillis()))), "Daily goal streak missing: " + text);
        com.mmm.tracker.GoalNotificationManager.onGoalProgressChanged(9999, progress);
        check(chat.size() == before + 1, "Daily goal milestone announced twice");
    }

    private static void verifyRemoteMilestoneChat(MinecraftClient client) throws Exception
    {
        Configs.Generic.RECEIVE_GOAL_MILESTONES.setBooleanValue(true);
        var method = com.mmm.social.PublicChatClient.class.getDeclaredMethod("handleMilestoneEvent", com.google.gson.JsonObject.class);
        method.setAccessible(true);
        var event = com.google.gson.JsonParser.parseString(
                "{\"eventId\":\"streak-smoke\",\"username\":\"OtherMiner\",\"threshold\":25,\"current\":10000,\"target\":40000,\"streakDays\":7}")
                .getAsJsonObject();
        List<?> chat = (List<?>) get(client.inGameHud.getChatHud(), "messages");
        int before = chat.size();
        method.invoke(null, event);
        check(chat.size() == before + 1, "Remote milestone missing");
        String text = ((net.minecraft.text.Text) invoke(chat.getFirst(), "content")).getString();
        check(text.contains("OtherMiner") && text.endsWith("Daily streak: 7 days."), "Remote streak replaced by local streak: " + text);
        method.invoke(null, event);
        check(chat.size() == before + 1, "Remote event deduplication broken");
        event.addProperty("eventId", "old-streak-smoke");
        event.remove("streakDays");
        method.invoke(null, event);
        check(chat.size() == before + 2, "Old remote milestone missing");
        text = ((net.minecraft.text.Text) invoke(chat.getFirst(), "content")).getString();
        check(!text.contains("Daily streak"), "Missing remote streak was fabricated");
    }

    private static List<String> labels(MmmSettingsScreen screen) throws Exception
    {
        return labels((List<?>)get(screen, "sections"));
    }

    private static List<String> labels(List<?> sections) throws Exception
    {
        List<String> labels = new ArrayList<>();
        for (Object section : sections)
            for (Object row : (List<?>) invoke(section, "rows")) labels.add((String) invoke(row, "label"));
        return labels;
    }

    private static Object get(Object target, String name) throws Exception
    {
        var field = (target instanceof Class<?> type ? type : target.getClass()).getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target instanceof Class<?> ? null : target);
    }

    private static void set(Object target, String name, Object value) throws Exception
    {
        var field = (target instanceof Class<?> type ? type : target.getClass()).getDeclaredField(name);
        field.setAccessible(true);
        field.set(target instanceof Class<?> ? null : target, value);
    }

    private static Object invoke(Object target, String name) throws Exception
    {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static AchievementCardState state() throws Exception
    {
        return (AchievementCardState) get(AchievementCardRenderer.class, "STATE");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }

    private static void shot(MinecraftClient client, String name) throws Exception
    {
        Path directory = Path.of("screenshots");
        Files.createDirectories(directory);
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer()))
        {
            image.writeTo(directory.resolve(name));
        }
    }
}
