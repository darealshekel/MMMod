package smoke;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

/** Test-only. Click the installed client's own widgets and rendered search rows on the client thread. */
final class PackagedRoutes {
    private record Route(String label, String dest) {}
    private static final Route HOTKEYS = new Route("Hotkeys", "com.mmm.gui.GuiConfigs");
    private static final Route WEBSITE = new Route("Website Link", "com.mmm.ui.WebsiteLinkScreen");
    private static final String EVERYDAY = "Everyday settings", DIGGING = "Digging & goals";
    private final List<Route> tools = new ArrayList<>(List.of(HOTKEYS,
            new Route("Projects", "com.mmm.ui.ProjectManagerScreen"), new Route("Profile", "com.mmm.ui.PlayerProfileScreen"),
            new Route("History", "com.mmm.hud.SessionHistoryScreen"), new Route("Summary", "com.mmm.hud.SummaryScreen"),
            new Route("Scoreboard", "com.mmm.scoreboard.ScoreboardScreen")));
    private final List<Route> editors = new ArrayList<>(List.of(
            new Route("Custom Sounds", "com.mmm.ui.GoalSoundSettingsScreen"), new Route("Perimeter Block List", "com.mmm.ui.PerimeterBlockListScreen"),
            new Route("Sync Scoreboard", "com.mmm.ui.SyncScoreboardScreen"), new Route("Custom Color", "com.mmm.ui.MmmColorEditorScreen")));
    private final boolean mapped, typedClick, sdl;
    private Object board;
    private Object book;
    private int bookStep;
    private int index, step, navStep;
    private final List<String> otherCategories = new ArrayList<>();
    private int categoryIndex;
    private boolean compact, navigationDone;
    private ResizeWait resizeWait;
    private String oldColor;

    PackagedRoutes(String mc) {
        mapped = mc.startsWith("1.");
        typedClick = !mapped || mc.equals("1.21.11");
        sdl = mc.equals("26.3");
        if (!mc.equals("1.17.1") && !mc.equals("26.1.2")) {
            editors.add(1, new Route("Ignored Players", "com.mmm.ui.MmmChatIgnoreListScreen"));
        }
        if (!List.of("1.17.1", "1.20.1", "26.1.2").contains(mc))
            tools.add(new Route("Leaderboards", "com.mmm.ui.LeaderboardScreen"));
    }

    private static Object field(Object obj, String name) throws Exception {
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(obj.getClass() + "." + name);
    }

    private static Object get(Object obj, String name) throws Exception {
        Method m = obj.getClass().getMethod(name);
        m.setAccessible(true);
        return m.invoke(obj);
    }

    private void setText(Object box, String text) throws Exception {
        Method m = box.getClass().getMethod(mapped ? "method_1852" : "setValue", String.class);
        m.setAccessible(true);
        m.invoke(box, text);
    }

    private List<?> children(Object screen) throws Exception {
        return (List<?>) get(screen, mapped ? "method_25396" : "children");
    }

    private String label(Object widget) throws Exception {
        return (String) get(get(widget, mapped ? "method_25369" : "getMessage"), "getString");
    }

    private boolean visible(Object widget) throws Exception {
        return (boolean) field(widget, mapped ? "field_22764" : "visible");
    }

    private boolean active(Object widget) throws Exception {
        return (boolean) field(widget, mapped ? "field_22763" : "active");
    }

    private record Box(int x, int y, int w, int h) {
        boolean overlaps(Box b) { return x < b.x+b.w && b.x < x+w && y < b.y+b.h && b.y < y+h; }
    }

    private int screenWidth(Object screen) throws Exception {
        return (int) field(screen, mapped ? "field_22789" : "width");
    }

    private Box bounds(Object screen, Object widget) throws Exception {
        PackagedProbe.check(children(screen).contains(widget) && visible(widget), "unregistered or hidden widget: " + widget);
        Box b = mapped ? new Box((int) field(widget, "field_22760"), (int) field(widget, "field_22761"),
                (int) field(widget, "field_22758"), (int) field(widget, "field_22759"))
                : new Box((int) get(widget, "getX"), (int) get(widget, "getY"),
                        (int) get(widget, "getWidth"), (int) get(widget, "getHeight"));
        PackagedProbe.check(b.w > 0 && b.h > 0 && b.x >= 0 && b.y >= 0 && b.x+b.w <= screenWidth(screen)
                && b.y+b.h <= (int) field(screen, mapped ? "field_22790" : "height"), "clipped widget: " + label(widget) + " " + b);
        return b;
    }

    private Object button(Object screen, List<?> candidates, String... labels) throws Exception {
        Class<?> widget = Class.forName(mapped ? "net.minecraft.class_339" : "net.minecraft.client.gui.components.AbstractWidget");
        List<String> available = new ArrayList<>();
        for (String wanted : labels) {
            Object match = null;
            for (Object child : candidates) {
                if (!widget.isInstance(child) || !visible(child)) continue;
                String name = label(child);
                available.add(name);
                if (!name.equals(wanted)) continue;
                PackagedProbe.check(match == null, "ambiguous visible " + name + " on " + screen.getClass());
                match = child;
            }
            if (match != null) {
                bounds(screen, match);
                PackagedProbe.check(active(match), "disabled " + wanted + " on " + screen.getClass());
                return match;
            }
        }
        throw new AssertionError("missing visible " + List.of(labels) + " on " + screen.getClass() + "; saw " + available);
    }

    private void click(Object screen, double x, double y) throws Exception {
        String name = mapped ? "method_25402" : "mouseClicked";
        Object handled;
        if (typedClick) {
            Class<?> info = Class.forName(mapped ? "net.minecraft.class_11910" : "net.minecraft.client.input.MouseButtonInfo");
            Class<?> event = Class.forName(mapped ? "net.minecraft.class_11909" : "net.minecraft.client.input.MouseButtonEvent");
            Object left = info.getConstructor(int.class, int.class).newInstance(sdl ? 1 : 0, 0);
            Object input = event.getConstructor(double.class, double.class, info).newInstance(x, y, left);
            handled = screen.getClass().getMethod(name, event, boolean.class).invoke(screen, input, false);
        } else {
            handled = screen.getClass().getMethod(name, double.class, double.class, int.class).invoke(screen, x, y, 0);
        }
        PackagedProbe.check(Boolean.TRUE.equals(handled), "unhandled click " + x + "," + y + " on " + screen.getClass());
    }

    private void press(Object screen, Object widget) throws Exception {
        Box b = bounds(screen, widget);
        PackagedProbe.check(active(widget), "disabled " + label(widget));
        click(screen, b.x + b.w/2.0, b.y + b.h/2.0);
    }

    private void activateSearch(Route route) throws Exception {
        List<?> targets = (List<?>) field(board, "clickTargets");
        boolean swatch = route.label.equals("Custom Color");
        for (Object t : targets) {
            int x = (int) get(t, "x"), y = (int) get(t, "y");
            int w = (int) get(t, "width"), h = (int) get(t, "height");
            // The board builds these hit regions from the filtered rows during render.
            // Ignore the header/clear control; action rows are wide and swatches square.
            if (y < 85 || (swatch ? w != h : w < 44)) continue;
            click(board, x + w / 2.0, y + h / 2.0);
            return;
        }
        throw new AssertionError("no rendered action for " + route.label + "; targets=" + targets);
    }

    void shot(Object client, String name) throws Exception {
        Object target = mapped ? get(client, "method_1522")
                : PackagedProbe.prop("mc").equals("26.1.2") ? get(client, "getMainRenderTarget")
                : get(field(client, "gameRenderer"), "mainRenderTarget");
        Class<?> api = Class.forName(mapped ? "net.minecraft.class_318" : "net.minecraft.client.Screenshot");
        Consumer<Object> done = message -> System.out.println("MMM_UI_CAPTURE " + name + " " + message);
        for (Method m : api.getMethods()) {
            if (!m.getName().equals(mapped ? "method_22690" : "grab")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length < 4 || p[0] != File.class || p[1] != String.class || !p[2].isInstance(target)) continue;
            if (p.length == 4) m.invoke(null, new File("."), name + ".png", target, done);
            else if (p.length == 5 && p[3] == int.class) m.invoke(null, new File("."), name + ".png", target, 1, done);
            else continue;
            return;
        }
        throw new AssertionError("no Minecraft framebuffer screenshot API");
    }

    private boolean slot(String kind, String caption) throws Exception {
        boolean overview = true;
        for (Object slot : (List<?>) field(book, "slots")) {
            if (!kind.equals(field(slot, "kind").toString()) ||
                    (caption != null && !caption.equalsIgnoreCase((String) field(slot, "caption")))) continue;
            if (kind.equals("TAB") && overview) { overview = false; continue; }
            bounds(book, slot);
            if (!active(slot)) return false;
            press(book, slot);
            return true;
        }
        throw new AssertionError("missing Collections action " + kind + " " + caption);
    }

    boolean collection(Object client, Object current) throws Exception {
        if (book == null) book = current;
        switch (bookStep++) {
            case 0 -> PackagedProbe.check(slot("TAB", null), "category unexpectedly disabled");
            case 1 -> PackagedProbe.check(slot("ENTRY", null), "entry unexpectedly disabled");
            case 2 -> {
                boolean active = slot("ACTION", "Share");
                if (!mapped) {
                    PackagedProbe.check(!active, "pre-world Share not gated on 26.x");
                    System.out.println("MMM_UI_GATED " + PackagedProbe.prop("nonce") + " ProgressShareScreen:no-world");
                    shot(client, "collection-detail");
                    return true;
                }
                PackagedProbe.check(active, "Share unexpectedly disabled");
            }
            case 3 -> {
                PackagedProbe.check(current != null && current.getClass().getName().equals("com.mmm.ui.ProgressShareScreen"),
                        "Share did not open from selected collection");
                shot(client, "ProgressShareScreen");
            }
            case 4 -> press(current, button(current, children(current), "Back", "< Back", "Done"));
            case 5 -> {
                PackagedProbe.check(current == book, "Share Back did not restore Collections");
                passed("ProgressShareScreen", "com.mmm.ui.ProgressShareScreen");
                return true;
            }
            default -> throw new AssertionError();
        }
        return false;
    }

    private void passed(String path, String dest) {
        System.out.println("MMM_UI_ROUTE " + PackagedProbe.prop("nonce") + " " + path + " " + dest);
    }

    private void destination(Object current, Route route) {
        PackagedProbe.check(current != null && current.getClass().getName().equals(route.dest),
                "wrong destination for " + route.label + ": " + (current == null ? "null" : current.getClass()));
    }

    private void page(Object current, boolean home) throws Exception {
        railPage(current, home ? EVERYDAY : "Tools");
    }

    private List<?> grid() throws Exception { return (List<?>) field(board, "navigationButtons"); }

    private List<?> rail() throws Exception { return (List<?>) field(board, "categoryRail"); }

    private void railPage(Object current, String title) throws Exception {
        PackagedProbe.check(current == board && title.equals(get(field(board, "currentPage"), "title")),
                "expected Settings category " + title + ", got " + current);
        PackagedProbe.check("".equals(field(board, "searchQuery")) && !visible(field(board, "backButton")),
                "unexpected search or category Back on " + title);
        List<?> pages = (List<?>) get(field(board, "home"), "children");
        PackagedProbe.check(rail().size() == pages.size(), "missing category rail entries");
        List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            Object tab = rail().get(i);
            Box box = bounds(board, tab);
            PackagedProbe.check(get(pages.get(i), "title").equals(label(tab)) && active(tab)
                    && boxes.stream().noneMatch(box::overlaps), "wrong, disabled, or overlapping rail tab: " + label(tab));
            boxes.add(box);
        }
        if (!title.equals("Tools")) {
            List<?> sections = (List<?>) field(board, "visibleSections");
            PackagedProbe.check(grid().isEmpty() && !sections.isEmpty(), "expected inline settings, not cards: " + title);
            List<?> groups = (List<?>) get(field(board, "currentPage"), "rowGroups");
            PackagedProbe.check(groups.size() == sections.size(), "wrong number of inline groups: " + title);
            for (int i = 0; i < groups.size(); i++) {
                Object section = sections.get(i), group = groups.get(i);
                List<?> rows = (List<?>) get(section, "rows");
                PackagedProbe.check(!rows.isEmpty() && get(group, "source") == get(section, "section")
                        && rows.equals(get(group, "rows")), "missing/reordered inline rows: " + title + "/" + get(group, "title"));
            }
        }
    }

    private void exerciseInline(String name, boolean digging) throws Exception {
        String toggle = digging ? "Flat Digger" : "Mining Tracker";
        Object config = null;
        var groups = new HashSet<String>();
        var labels = new HashSet<String>();
        for (Object section : (List<?>) field(board, "visibleSections")) {
            groups.add((String) get(get(section, "section"), "title"));
            for (Object row : (List<?>) get(section, "rows")) {
                String label = (String) get(row, "label");
                labels.add(label);
                if (toggle.equals(label)) config = get(row, "config");
            }
        }
        PackagedProbe.check(groups.containsAll(digging ? List.of("Digging helpers", "Goals & notifications") : List.of(EVERYDAY))
                && labels.containsAll(digging ? List.of(toggle, "Perimeter Block List", "Custom Sounds")
                        : List.of(toggle, "Main HUD", "Daily Goal")), "wrong inline groups/rows: " + groups + " / " + labels);
        PackagedProbe.check(config != null, "missing setting: " + toggle);

        // Both categories start with a toggle. Use the rendered hitbox and verify which config the click edited.
        Box search = bounds(board, field(board, "searchField"));
        for (Object target : (List<?>) field(board, "clickTargets")) {
            Box b = new Box((int) get(target, "x"), (int) get(target, "y"), (int) get(target, "width"), (int) get(target, "height"));
            if (b.y < search.y + search.h || b.w <= b.h) continue;
            boolean original = (boolean) get(config, "getBooleanValue");
            click(board, b.x + b.w/2.0, b.y + b.h/2.0);
            PackagedProbe.check((boolean) get(config, "getBooleanValue") != original, "click did not toggle " + toggle);
            click(board, b.x + b.w/2.0, b.y + b.h/2.0);
            PackagedProbe.check((boolean) get(config, "getBooleanValue") == original, "click did not restore " + toggle);
            recordBox(board, b, name, digging ? "FlatDigger" : "MiningTracker");
            return;
        }
        throw new AssertionError("missing rendered control for " + toggle);
    }

    private Object back(Object screen) throws Exception {
        return button(screen, children(screen), "Back", "< Back");
    }

    // Some editors paint their own chrome and register only the text field; Esc is their user-facing exit.
    private void leaveEditor(Object screen) throws Exception {
        List<?> children = children(screen);
        Class<?> widget = Class.forName(mapped ? "net.minecraft.class_339" : "net.minecraft.client.gui.components.AbstractWidget");
        for (Object child : children) {
            if (widget.isInstance(child) && visible(child) && List.of("Back", "< Back", "Done").contains(label(child))) {
                press(screen, button(screen, children, "Back", "< Back", "Done"));
                return;
            }
        }
        escape(screen);
    }

    private void escape(Object screen) throws Exception {
        String key = mapped ? "method_25404" : "keyPressed";
        if (typedClick) {
            Class<?> event = Class.forName(mapped ? "net.minecraft.class_11908" : "net.minecraft.client.input.KeyEvent");
            // 26.3 KeyEvent takes SDL_SCANCODE_ESCAPE, SDLK_ESCAPE, modifiers.
            Object esc = event.getConstructor(int.class, int.class, int.class).newInstance(sdl ? 41 : 256, sdl ? 27 : 0, 0);
            screen.getClass().getMethod(key, event).invoke(screen, esc);
        } else {
            screen.getClass().getMethod(key, int.class, int.class, int.class).invoke(screen, 256, 0, 0);
        }
    }

    // Record widget bounds in GUI units; the receipt also checks for painted content at these positions.
    private Box evidence(Object screen, Object widget, String shot, String key) throws Exception {
        Box b = bounds(screen, widget);
        recordBox(screen, b, shot, key);
        return b;
    }

    private void recordBox(Object screen, Box b, String shot, String key) throws Exception {
        System.out.printf("MMM_UI_WIDGET %s %s %s %d %d %d %d %d %d%n", PackagedProbe.prop("nonce"), shot, key,
                b.x, b.y, b.w, b.h, screenWidth(screen), (int) field(screen, mapped ? "field_22790" : "height"));
    }

    private void captureRail(String name) throws Exception {
        for (Object tab : rail()) evidence(board, tab, name, railKey(label(tab)));
    }

    private static String railKey(String title) {
        return switch (title) {
            case EVERYDAY -> "Everyday";
            case DIGGING -> "Digging";
            case "Mining visuals" -> "MiningVisuals";
            case "Website sync" -> "WebsiteSync";
            default -> title;
        };
    }

    private void captureHome(Object client, String name) throws Exception {
        int width = screenWidth(board);
        PackagedProbe.check(compact ? width >= 320 && width <= 400 : width >= 530,
                "wrong " + name + " logical width: " + width);
        Object hotkeys = button(board, children(board), HOTKEYS.label);
        Object website = button(board, children(board), WEBSITE.label);
        Box h = evidence(board, hotkeys, name, "Hotkeys");
        Box w = evidence(board, website, name, "WebsiteLink");
        Box search = bounds(board, field(board, "searchField"));
        PackagedProbe.check(!h.overlaps(w) && !h.overlaps(search) && !w.overlaps(search), "overlapping home controls");
        captureRail(name);
        exerciseInline(name, false);
        shot(client, name);
    }

    private void captureTools(Object client, String name) throws Exception {
        List<?> tiles = grid();
        PackagedProbe.check(tiles.size() == tools.size(), "unexpected number of Tools tiles: " + tiles.size());
        List<Box> boxes = new ArrayList<>();
        for (Route t : tools) {
            Box b = evidence(board, button(board, tiles, t.label), name, t.label);
            PackagedProbe.check(boxes.stream().noneMatch(b::overlaps), "overlapping Tools tile: " + t.label);
            boxes.add(b);
        }
        if (!compact)
            PackagedProbe.check(new HashSet<>(boxes.stream().map(Box::x).toList()).size() > 1, "Tools is not a grid");
        captureRail(name);
        shot(client, name);
    }

    // GLFW/SDL can return before Minecraft receives the resize and rebuilds this screen.
    // Sample on separate client ticks; never advance the route or capture at the old size.
    static final class ResizeWait {
        final int width, height;
        final long start;
        private int lastWidth, lastHeight;
        private boolean retried;

        ResizeWait(int width, int height, long start) {
            this.width = width;
            this.height = height;
            this.start = start;
        }

        boolean settled(int sw, int sh, long now) {
            PackagedProbe.check(now - start < 12_000_000_000L, "resize timed out: requested " + width + "x" + height
                    + " framebuffer at GUI scale 2; last Settings layout " + sw + "x" + sh);
            boolean matches = Math.abs(2 * sw - width) <= 1 && sh > 0;
            boolean ready = matches && sw == lastWidth && sh == lastHeight;
            lastWidth = sw;
            lastHeight = sh;
            return ready;
        }

        boolean retry(int sw, long now) {
            if (retried || Math.abs(2 * sw - width) <= 1 || now - start < 3_000_000_000L) return false;
            retried = true;
            return true;
        }
    }

    private void requestSize(Object client, int width, int height) throws Exception {
        Object window = get(client, mapped ? "method_22683" : "getWindow");
        long handle = (long) get(window, mapped ? "method_4490" : "handle");
        if (sdl) {
            Object resized = Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_SetWindowSize", long.class, int.class, int.class)
                    .invoke(null, handle, width, height);
            PackagedProbe.check(Boolean.TRUE.equals(resized), "SDL_SetWindowSize failed: " + width + "x" + height);
            return;
        }
        Class.forName("org.lwjgl.glfw.GLFW").getMethod("glfwSetWindowSize", long.class, int.class, int.class)
                .invoke(null, handle, width, height);
    }

    private void resize(Object client, int width, int height) throws Exception {
        requestSize(client, width, height);
        resizeWait = new ResizeWait(width, height, System.nanoTime());
    }

    private void navigation(Object client, Object current) throws Exception {
        String view = compact ? "compact-" : "wide-";
        String entry = view + "everyday";
        Route route = tools.get(index);
        switch (navStep++) {
            case 0 -> { page(current, true); captureHome(client, entry); }
            case 1 -> press(board, button(board, children(board), HOTKEYS.label));
            case 2 -> {
                destination(current, HOTKEYS);
                evidence(current, back(current), entry + "-Hotkeys", "Back");
                shot(client, entry + "-Hotkeys");
            }
            case 3 -> press(current, back(current));
            case 4 -> {
                page(current, true);
                passed(entry + "-Hotkeys", HOTKEYS.dest);
                press(board, button(board, children(board), WEBSITE.label));
            }
            case 5 -> { destination(current, WEBSITE); shot(client, entry + "-WebsiteLink"); }
            case 6 -> press(current, button(current, children(current), "Back", "< Back", "Done"));
            case 7 -> {
                page(current, true);
                passed(entry + "-WebsiteLink", WEBSITE.dest);
                press(board, button(board, rail(), DIGGING));
                navStep = 14;
            }
            case 8 -> { page(current, false); captureTools(client, view + "tools"); }
            case 9 -> press(board, button(board, grid(), route.label));
            case 10 -> {
                destination(current, route);
                evidence(current, back(current), view + "tools-" + route.label, "Back");
                shot(client, view + "tools-" + route.label);
            }
            case 11 -> press(current, back(current));
            case 12 -> {
                page(current, false);
                passed(view + "tools-" + route.label, route.dest);
                if (index + 1 < tools.size()) { index++; navStep = 9; }
                else press(board, button(board, rail(), EVERYDAY));
            }
            case 13 -> {
                page(current, true);
                button(board, rail(), "Tools");
                passed(view + "tools", board.getClass().getName());
                passed(entry, board.getClass().getName());
                index = navStep = 0;
                if (compact) { navigationDone = true; resize(client, 960, 640); }
                else { compact = true; resize(client, 680, 840); }
            }
            case 14 -> {
                railPage(current, DIGGING);
                captureRail(view + "digging");
                exerciseInline(view + "digging", true);
                shot(client, view + "digging");
                otherCategories.clear();
                for (Object tab : rail()) {
                    String name = label(tab);
                    if (!List.of(EVERYDAY, DIGGING, "Tools").contains(name)) otherCategories.add(name);
                }
                categoryIndex = 0;
            }
            case 15 -> {
                if (categoryIndex > 0) {
                    String title = otherCategories.get(categoryIndex-1);
                    railPage(current, title);
                    String path = view + "rail-" + railKey(title);
                    captureRail(path);
                    shot(client, path);
                    passed(path, board.getClass().getName());
                }
                if (categoryIndex < otherCategories.size()) {
                    press(board, button(board, rail(), otherCategories.get(categoryIndex++)));
                    navStep = 15;
                } else {
                    passed(view + "digging", board.getClass().getName());
                    press(board, button(board, rail(), "Tools"));
                    navStep = 8;
                }
            }
            default -> throw new AssertionError(navStep);
        }
    }

    boolean advance(Object client, Object current) throws Exception {
        if (board == null) board = current;
        if (resizeWait != null) {
            PackagedProbe.check(current == board, "lost Settings while resizing");
            int w = screenWidth(board), h = (int) field(board, mapped ? "field_22790" : "height");
            long now = System.nanoTime();
            if (!resizeWait.settled(w, h, now)) {
                if (resizeWait.retry(w, now)) requestSize(client, resizeWait.width, resizeWait.height);
                return false;
            }
            resizeWait = null;
        }
        if (!navigationDone) { navigation(client, current); return false; }
        if (index == editors.size()) return true;
        Route route = editors.get(index);
        boolean color = route.label.equals("Custom Color");
        switch (step) {
            case 0 -> {
                page(current, true);
                Object search = field(board, "searchField");
                press(board, search);
                setText(search, route.label);
            }
            case 1 -> {
                PackagedProbe.check(current == board, "lost Settings before activating " + route);
                activateSearch(route);
            }
            case 2 -> {
                destination(current, route);
                if (color) {
                    oldColor = (String) get(field(current, "config"), "getStringValue");
                    setText(field(current, "hexField"), "#FFA03030");
                    PackagedProbe.check("#FFA03030".equalsIgnoreCase((String) get(field(current, "config"), "getStringValue")),
                            "hex editing did not change the actual setting");
                }
            }
            case 3 -> shot(client, color ? "picker" : current.getClass().getSimpleName());
            case 4 -> {
                if (color) setText(field(current, "hexField"), oldColor);
                leaveEditor(current);
            }
            case 5 -> {
                PackagedProbe.check(current == board, "Back did not restore Settings: " + route);
                PackagedProbe.check(route.label.equals(field(board, "searchQuery")), "editor lost active search");
                if (index % 2 == 1) escape(board);
                else press(board, button(board, children(board), "< Back"));
            }
            case 6 -> {
                page(current, true);
                passed(color ? "picker" : route.dest.substring(route.dest.lastIndexOf('.') + 1), route.dest);
                index++;
                step = -1;
            }
            default -> throw new AssertionError();
        }
        step++;
        return false;
    }
}
