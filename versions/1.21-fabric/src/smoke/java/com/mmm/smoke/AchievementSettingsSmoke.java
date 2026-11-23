package com.mmm.smoke;

import com.mmm.advancement.AchievementCardRenderer;
import com.mmm.advancement.AchievementCardState;
import com.mmm.advancement.MmmAdvancementDefinition;
import com.mmm.advancement.MmmAdvancementManager;
import com.mmm.ui.MmmSettingsScreen;
import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.storage.MiningCalendarStore;
import com.mmm.tracker.CollectionLiveProgress;
import com.mmm.tracker.CollectionWebsiteClient;
import com.mmm.tracker.LiveCollections;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
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
                    check(organized.remove("Test Achievement"), "Preview build has no Test Achievement action");
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
                    check(pageTitle().equals("Everyday settings") && labels((List<?>) get(screen, "visibleSections")).contains("Mining Tracker"),
                            "Settings did not open directly to Everyday controls");
                    shot(client, "settings-overview.png");
                    ((TextFieldWidget) get(screen, "searchField")).setText("rainbow");
                }
                if (ticks == 15)
                {
                    check(((List<?>) get(screen, "visibleSections")).size() == 1
                            && labels((List<?>) get(screen, "visibleSections")).contains("Rainbow Speed"), "Global search missed Rainbow Speed");
                    shot(client, "settings-search.png");
                    ((TextFieldWidget) get(screen, "searchField")).setText("Test Achievement");
                }
                if (ticks == 20)
                {
                    shot(client, "settings-preview-button.png");
                    unlocked = MmmAdvancementManager.isUnlocked(MmmAdvancementDefinition.DIG_MASTER);
                    check(labels((List<?>) get(screen, "visibleSections")).equals(List.of("Test Achievement")), "Wrong preview search result");
                    clickFirstRowControl();
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
                if (ticks == 135) clickRail("HUD");
                if (ticks == 145)
                {
                    checkHudGroups();
                    var hudX = (TextFieldWidget) ((Map<?, ?>) get(screen, "textFields")).get(Configs.Generic.HUD_X);
                    check(hudX.isVisible() && screen.mouseClicked(hudX.getX() + 2, hudX.getY() + 2, 0)
                            && hudX.isFocused(), "Inline HUD X control did not take focus");
                }
                if (ticks == 155)
                {
                    shot(client, "settings-category-detail.png");
                    ((TextFieldWidget) get(screen, "searchField")).setText("Flat Digger");
                }
                if (ticks == 165)
                {
                    check(labels((List<?>) get(screen, "visibleSections")).contains("Flat Digger"), "HUD search was not global");
                    clickButton((ButtonWidget) get(screen, "backButton"), "< Back");
                    checkHudGroups();
                }
                if (ticks == 175)
                {
                    ((TextFieldWidget) get(screen, "searchField")).setText("Flat Digger");
                    check(screen.keyPressed(256, 0, 0) && client.currentScreen == screen, "Search Escape closed Settings");
                    checkHudGroups();
                }
                if (ticks == 185)
                {
                    check(screen.keyPressed(256, 0, 0) && client.currentScreen == null, "Category Escape did not return to game");
                    client.options.getGuiScale().setValue(3);
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 960, 540);
                    client.onResolutionChanged();
                    screen = new MmmSettingsScreen(null);
                    client.setScreen(screen);
                }
                if (ticks == 195)
                {
                    check(screen.width < 510 && pageTitle().equals("Everyday settings"), "Compact Everyday entry missing");
                    clickRail("Digging & goals");
                }
                if (ticks == 205)
                {
                    var toggle = FeatureToggle.MMM_FLAT_DIGGER;
                    boolean original = toggle.getBooleanValue();
                    clickFirstRowControl();
                    check(toggle.getBooleanValue() != original, "Compact inline Flat Digger did not toggle");
                    clickFirstRowControl();
                    check(toggle.getBooleanValue() == original, "Compact inline Flat Digger did not restore");
                }
                if (ticks == 215) clickRail("HUD");
                if (ticks == 225)
                {
                    checkHudGroups();
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
                    check(screen.keyPressed(268, 0, 0) && (double) get(screen, "scrollY") == 0, "Home did not reset compact scroll");
                }
                if (ticks == 255) screen.mouseScrolled(screen.width - 10, screen.height - 10, 0, -1);
                if (ticks == 265)
                {
                    check((double) get(screen, "scrollY") > 0, "Short viewport did not scroll");
                    shot(client, "settings-category-detail-forced-gui3-scrolled.png");
                    done = true;
                    Files.writeString(Path.of("mmm-achievement-settings-smoke.txt"),
                            "PASS: settings preserved, Everyday and category rail, inline HUD groups, compact Digging toggle, search Back/Escape, category exit, preview click, renderer hook, no unlock mutation, hidden HUD, GUI scales, cleanup, live collection card, one local chat with real streak, daily goal streak, sender-specific remote streak and old-event compatibility, no public duplicate, history/reload suppression");
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

    private void clickRail(String label) throws Exception
    {
        for (Object item : (List<?>) get(screen, "categoryRail"))
        {
            var button = (ButtonWidget) item;
            if (button.getMessage().getString().equals(label))
            {
                clickButton(button, label);
                check(pageTitle().equals(label) && screen.getFocused() != button, "Category or focus did not change: " + label);
                return;
            }
        }
        throw new AssertionError("Missing category button: " + label);
    }

    private String pageTitle() throws Exception { return (String) invoke(get(screen, "currentPage"), "title"); }

    private void clickButton(ButtonWidget button, String label)
    {
        check(button.getMessage().getString().equals(label) && button.visible && button.active
                && screen.mouseClicked(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, 0),
                "Missing or inactive button: " + label);
    }

    private void clickFirstRowControl() throws Exception
    {
        var search = (TextFieldWidget) get(screen, "searchField");
        for (Object hit : (List<?>) get(screen, "clickTargets"))
        {
            int y = (int) invoke(hit, "y"), h = (int) invoke(hit, "height"), w = (int) invoke(hit, "width");
            if (y < search.getY() + search.getHeight() || w <= h) continue;
            check(screen.mouseClicked((int) invoke(hit, "x") + w / 2.0, y + h / 2.0, 0), "Inline click was not handled");
            return;
        }
        throw new AssertionError("No painted row control below search");
    }

    private void checkHudGroups() throws Exception
    {
        check(pageTitle().equals("HUD") && ((TextFieldWidget) get(screen, "searchField")).getText().isEmpty()
                && ((List<?>) get(screen, "navigationButtons")).isEmpty(), "Expected inline HUD, not search or topic cards");
        for (Object group : (List<?>) get(screen, "visibleSections"))
        {
            if ("Mining totals".equals(invoke(group, "title")))
            {
                var rows = labels(List.of(group));
                check(rows.size() <= 8 && rows.containsAll(List.of("Mining Totals", "Global Total", "World Total", "Session Total")),
                        "Wrong inline Mining totals group: " + rows);
                return;
            }
        }
        throw new AssertionError("Mining totals was not inline in HUD");
    }

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
