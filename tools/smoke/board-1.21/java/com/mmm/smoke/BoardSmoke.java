package com.mmm.smoke;

import com.mmm.ui.CollectionBoardState;
import com.mmm.ui.CollectionBookScreen;
import com.mmm.ui.CollectionSlotButton;
import com.mmm.ui.InventoryCollectionButton;
import com.mmm.ui.MmmSettingsScreen;
import com.mmm.ui.ProgressShareScreen;
import com.mmm.ui.WebsiteLinkScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.navigation.GuiNavigationType;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

/** Offline input and render checks against a disposable single-player session. */
final class BoardSmoke {
    private final ToolControlsSmoke controls = new ToolControlsSmoke();
    private int phase, ticks;
    private long started;
    private MmmSettingsScreen settings;
    private CollectionBookScreen book;
    private ButtonWidget hud;
    private Path exportDir;
    private long existingExports;

    void install(MinecraftClient client) {
        client.options.pauseOnLostFocus = false;
        client.options.getGuiScale().setValue(1);
        client.onResolutionChanged();
        settings = new MmmSettingsScreen(null);
        client.setScreen(settings);
        started = System.currentTimeMillis();
        ClientTickEvents.END_CLIENT_TICK.register(current -> {
            if (phase < 0) return;
            try {
                if (System.currentTimeMillis() - started > 90_000) throw new AssertionError("Timeout in board phase " + phase);
                if (++ticks % 10 == 0) step(current);
            } catch (Throwable ex) {
                ex.printStackTrace();
                finish(current, "FAIL: phase " + phase + ": " + ex);
            }
        });
    }

    private void step(MinecraftClient client) throws Exception {
        if (!controls.done(client, settings)) return;
        switch (phase) {
            case 0 -> {
                require(showsRow("Mining Tracker") && !showsRow("HUD X"), "Settings did not open on Everyday controls");
                hud = button(settings, "HUD");
                client.setNavigationType(GuiNavigationType.KEYBOARD_TAB);
                for (int i = 0; i <= settings.children().size() && settings.getFocused() != hud; i++)
                    settings.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                require(settings.getFocused() == hud, "HUD category not reachable by Tab");
            }
            case 1 -> {
                MiningFeaturesSmoke.shot(client, "board-settings.png");
                require(settings.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0), "Enter did not activate focused HUD category");
            }
            case 2 -> {
                require(showsRow("HUD X") && !showsRow("Mining Tracker"), "HUD category did not replace Everyday controls");
                var search = (TextFieldWidget)field(settings, "searchField");
                settings.setFocused(search);
                for (char ch : "active digger icons".toCharArray()) settings.charTyped(ch, 0);
                require(showsRow("Active Digger Icons") && !showsRow("HUD X"), "Global search did not isolate the matching setting");
                settings.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(search.getText().isEmpty() && client.currentScreen == settings && showsRow("HUD X"),
                        "Escape did not restore the active HUD category");
                button(settings, "Website Link").onPress();
                require(client.currentScreen instanceof WebsiteLinkScreen, "Website link button did not navigate");
                var link = button(client.currentScreen, "Link Account");
                require(!link.active, "Empty code accepted");
                ((TextFieldWidget)field(client.currentScreen, "code")).setText("ab-1234");
                require(link.active && ((TextFieldWidget)field(client.currentScreen, "code")).getText().equals("AB1234"),
                        "Code entry did not sanitize and enable the link action");
                client.currentScreen.close();
                require(client.currentScreen == settings, "Website Back lost its parent");
                client.setScreen(new InventoryScreen(client.player));
            }
            case 3 -> {
                Screen inventory = client.currentScreen;
                var entry = inventory.children().stream().filter(InventoryCollectionButton.class::isInstance)
                        .map(InventoryCollectionButton.class::cast).findFirst().orElseThrow();
                client.player.currentScreenHandler.setCursorStack(new ItemStack(Items.STONE));
                entry.onPress();
                require(client.currentScreen == inventory, "Book intercepted held stack");
                client.player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
                entry.onPress();
                require(client.currentScreen instanceof CollectionBookScreen, "Inventory did not open Collections");
                client.currentScreen.close();
                require(client.currentScreen == inventory, "Collection Back lost inventory");
                client.setScreen(null);
                var source = (FabricClientCommandSource)client.getNetworkHandler().getCommandSource();
                ClientCommandManager.getActiveDispatcher().execute("mmm collections", source);
            }
            case 4 -> {
                require(client.currentScreen instanceof CollectionBookScreen, "Command did not open Collections");
                book = (CollectionBookScreen)client.currentScreen;
                MiningFeaturesSmoke.shot(client, "board-collections.png");
                slot(book, "All collections").onPress();
                slot(book, "World scope").onPress();
                slot(book, "Progress filter").onPress();
                slot(book, "Sort collections").onPress();
                var reopened = new CollectionBookScreen(null);
                for (var key : List.of("category", "allWorlds", "sort", "filter"))
                    require(field(book, key).equals(field(reopened, key)), "Lost " + key + " on reopen");
                client.setScreen(reopened);
                book = reopened;
                // Return the progress filter to All before searching for an unmined block.
                while ((int)field(book, "filter") != 0) slot(book, "Progress filter").onPress();
                ((TextFieldWidget)field(book, "searchField")).setText("gilded");
                slot(book, "Gilded Blackstone").onPress();
                checkTiers(book, 5, 0);
                slot(book, "Tier III").onPress();
            }
            case 5 -> {
                require(((CollectionBoardState)field(book, "board")).inspectedTier() == 3, "Locked record not inspectable");
                MiningFeaturesSmoke.shot(client, "board-tier-inspector.png");
                book.init(client, 320, 240);
                require(((CollectionBoardState)field(book, "board")).inspectedTier() == 3, "Resize lost inspected tier");
                slot(book, "Tier III").onPress();
                require(((CollectionBoardState)field(book, "board")).inspectedTier() == 0, "Second click did not follow next tier");
                client.onResolutionChanged();
                slot(book, "Share collection").onPress();
                require(client.currentScreen instanceof ProgressShareScreen, "Share did not open");
                exportDir = client.runDirectory.toPath().resolve("screenshots/mmm");
                existingExports = exports();
                button(client.currentScreen, "Save PNG").onPress();
            }
            case 6 -> {
                if (exports() <= existingExports) return;
                if (!"Saved to screenshots/mmm".equals(field(client.currentScreen, "status"))) return;
                try (var files = Files.list(exportDir)) {
                    var png = files.filter(p -> p.toString().endsWith(".png"))
                            .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified())).orElseThrow();
                    var img = javax.imageio.ImageIO.read(png.toFile());
                    require(img != null && img.getWidth() == 420 && img.getHeight() == 196, "Incorrect share panel crop");
                    int glyphs = 0, rule = 0;
                    for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                        int color = img.getRGB(x, y) & 0xffffff;
                        if ((color & 0xff) > 195 && (color >> 16) > 220 && ((color >> 8) & 255) > 210) glyphs++;
                        if (color == 0xd90000) rule++;
                    }
                    require(glyphs > 30 && rule >= 400, "Share panel text or top rule missing/blurred");
                }
                client.currentScreen.close();
                require(client.currentScreen == book, "Share Back lost collection");
                finish(client, "PASS: Everyday controls, keyboard category navigation, global search, Website Link, inventory and command entry, persistent filters, tier inspection, cropped PNG");
                return;
            }
        }
        phase++;
    }

    private boolean showsRow(String label) throws Exception {
        for (Object group : (List<?>)field(settings, "visibleSections"))
            for (Object row : (List<?>)field(group, "rows"))
                if (label.equals(field(row, "label"))) return true;
        return false;
    }

    private long exports() throws Exception {
        if (!Files.exists(exportDir)) return 0;
        try (var files = Files.list(exportDir)) { return files.filter(p -> p.toString().endsWith(".png")).count(); }
    }

    static void checkTiers(Screen screen, int total, int earned) throws Exception {
        var records = screen.children().stream().filter(CollectionSlotButton.class::isInstance)
                .map(CollectionSlotButton.class::cast).filter(b -> b.getMessage().getString().startsWith("Tier ")).toList();
        require(records.size() == total, "Expected " + total + " tier records, got " + records.size());
        var state = (CollectionBoardState)field(screen, "board");
        for (var record : records) {
            int n = (int)field(record, "tierNumber");
            String kind = n <= earned ? "EARNED" : n == earned + 1 ? "NEXT" : "LOCKED";
            require(field(record, "tier").toString().equals(kind), "Incorrect tier status: " + n);
            record.onPress();
            require(state.inspectedTier() == n, "Tier click did not inspect " + n);
            record.onPress();
            require(state.inspectedTier() == 0, "Tier click did not release " + n);
        }
    }

    private static ButtonWidget button(Screen screen, String name) { return widget(screen, name, ButtonWidget.class); }
    private static CollectionSlotButton slot(Screen screen, String name) { return widget(screen, name, CollectionSlotButton.class); }
    private static <T extends PressableWidget> T widget(Screen screen, String name, Class<T> type) {
        return screen.children().stream().filter(type::isInstance).map(type::cast)
                .filter(w -> w.visible && name.equals(w.getMessage().getString())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + name));
    }

    private static Object field(Object obj, String key) throws Exception {
        var f = obj.getClass().getDeclaredField(key);
        f.setAccessible(true);
        return f.get(obj);
    }
    private static void require(boolean test, String message) { if (!test) throw new AssertionError(message); }
    private void finish(MinecraftClient client, String result) {
        phase = -1;
        try { Files.writeString(client.runDirectory.toPath().resolve("mmm-board-smoke-result.txt"), result); }
        catch (Exception ex) { throw new AssertionError(ex); }
        System.out.println("MMM_BOARD_SMOKE_" + result);
        client.scheduleStop();
    }
}
