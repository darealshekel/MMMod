package com.mmm.legacy.settings;

import java.util.*;
import java.util.function.Function;
import net.minecraft.client.gui.screen.Screen;

/** Registration is performed on the client thread only, after a feature has its legacy runtime hooks. */
public final class LegacyUiRegistry {
    public record Entry(String id, String label, String category, Function<Screen, Screen> screen) {}
    private static final Map<String, Entry> entries = new LinkedHashMap<>();
    private static final Set<String> capabilities = new HashSet<>();
    private LegacyUiRegistry() {}
    public static void enable(String capability) { capabilities.add(capability); }
    public static boolean available(String capability) { return capability.isEmpty() || capabilities.contains(capability); }
    public static void register(String id, String label, String category, Function<Screen, Screen> screen) {
        entries.put(id, new Entry(id, label, category, Objects.requireNonNull(screen)));
    }
    public static List<Entry> entries() { return List.copyOf(entries.values()); }
}
