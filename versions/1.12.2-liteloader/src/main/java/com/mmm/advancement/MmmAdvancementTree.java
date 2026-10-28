package com.mmm.advancement;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementInfo;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.advancement.FrameType;
import net.minecraft.advancement.criterion.Criterion;
import net.minecraft.client.gui.screen.menu.advancements.AdvancementsScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.Identifier;
import net.minecraft.text.LiteralText;

/** Client-only nodes. Nothing is inserted into the server advancement manager. */
public final class MmmAdvancementTree {
    private static final int STEPS = 100;
    private static final Identifier ROOT_ID = new Identifier("mmm", "root");
    private static final Map<MmmAdvancementDefinition, Advancement> NODES = new EnumMap<>(MmmAdvancementDefinition.class);
    private static final Map<Category, Advancement> CATEGORIES = new EnumMap<>(Category.class);
    private static final Advancement ROOT;
    private static final Map<AdvancementsScreen, MmmAdvancementSnapshot> DISPLAYED = new WeakHashMap<>();

    static {
        ROOT = node(ROOT_ID, null, "MMM", "Session hours, streaks, speed, and endurance.",
                Items.DIAMOND_PICKAXE, FrameType.TASK, 0, 3, new Identifier("textures/blocks/obsidian.png"), 1);
        category(Category.SESSION_HOURS, "Session Hours", "Earned from active time in saved sessions.", Items.CLOCK, 0);
        category(Category.STREAKS, "Streaks", "Mine at least 10,000 blocks on consecutive days.", Items.FLINT_AND_STEEL, 2);
        category(Category.SPEED, "Speed Achievements", "Earned from a session's rolling Best Hour.", Items.GOLDEN_PICKAXE, 4);
        category(Category.ENDURANCE, "Endurance", "Earned from active time in one session.", Items.DIAMOND_PICKAXE, 6);
        category(Category.PRECISION, "Precision", "Earned from consistently fast completed sessions.", Items.COMPASS, 8);
        Map<Category, Advancement> parents = new EnumMap<>(CATEGORIES);
        for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED) {
            Category branch = categoryFor(definition);
            Advancement advancement = node(new Identifier("mmm", definition.id()), parents.get(branch),
                    definition.title(), definition.description(), iconFor(definition), frameFor(definition),
                    definition.x() + 1, definition.y(), null, STEPS);
            NODES.put(definition, advancement);
            parents.put(branch, advancement);
        }
    }

    private MmmAdvancementTree() {}

    public static void install(AdvancementsScreen screen) {
        if (screen.findAdvancementWidget(ROOT) == null) {
            DISPLAYED.remove(screen);
            screen.onAdvancementRootAdded(ROOT);
            for (Category category : Category.values()) screen.onAdvancementTaskAdded(CATEGORIES.get(category));
            for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED)
                screen.onAdvancementTaskAdded(NODES.get(definition));
        }
        updateProgress(screen);
    }

    public static void updateProgress(AdvancementsScreen screen) {
        if (screen.findAdvancementWidget(ROOT) == null) return;
        MmmAdvancementManager.refreshForDisplay();
        MmmAdvancementSnapshot snapshot = MmmAdvancementManager.isProgressReady() ? MmmAdvancementManager.snapshot() : null;
        if (DISPLAYED.containsKey(screen) && java.util.Objects.equals(DISPLAYED.get(screen), snapshot)) return;
        DISPLAYED.put(screen, snapshot);
        screen.onUpdateAdvancementProgress(ROOT, progress(ROOT, 1));
        for (Advancement category : CATEGORIES.values()) screen.onUpdateAdvancementProgress(category, progress(category, 1));
        for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED) {
            Advancement node = NODES.get(definition);
            int obtained = MmmAdvancementManager.isUnlocked(definition) ? STEPS
                    : Math.min(STEPS - 1, (int) (definition.progressFraction(snapshot) * STEPS));
            screen.onUpdateAdvancementProgress(node, progress(node, obtained));
            if (screen.findAdvancementWidget(node) instanceof MmmAdvancementWidgetProgress widget)
                widget.mmm$setProgressDescription(definition.description(), snapshot == null
                        ? "Loading session history..." : definition.progressLabel(snapshot));
        }
    }

    public static Advancement entry(MmmAdvancementDefinition definition) { return NODES.getOrDefault(definition, ROOT); }
    public static Advancement rootEntry() { return ROOT; }
    public static boolean isMmmRoot(Advancement node) { return node != null && ROOT_ID.equals(node.getId()); }

    private static Advancement node(Identifier id, Advancement parent, String title, String description, Item icon,
                                    FrameType frame, float x, float y, Identifier background, int steps) {
        AdvancementInfo info = new AdvancementInfo(new ItemStack(icon), new LiteralText(title), new LiteralText(description),
                background, frame, true, false, false);
        info.setPosition(x, y);
        Map<String, Criterion> criteria = new LinkedHashMap<>();
        String[][] requirements = new String[steps][];
        for (int i = 0; i < steps; i++) {
            String key = "progress_" + i;
            criteria.put(key, new Criterion());
            requirements[i] = new String[]{key};
        }
        return new Advancement(id, parent, info, AdvancementRewards.EMPTY, criteria, requirements);
    }

    private static AdvancementProgress progress(Advancement node, int steps) {
        AdvancementProgress progress = new AdvancementProgress();
        progress.set(node.getCriteria(), node.getRequirements());
        for (int i = 0; i < steps; i++) progress.add("progress_" + i);
        return progress;
    }

    private static void category(Category category, String title, String description, Item icon, float y) {
        CATEGORIES.put(category, node(new Identifier("mmm", "category/" + category.name().toLowerCase(Locale.ROOT)),
                ROOT, title, description, icon, FrameType.TASK, 1, y, null, 1));
    }

    private static Category categoryFor(MmmAdvancementDefinition definition) {
        return switch (definition.kind()) {
            case FIRST_USE, SESSION_HOURS -> Category.SESSION_HOURS;
            case STREAK_DAYS -> Category.STREAKS;
            case BEST_HOUR_BLOCKS -> Category.SPEED;
            case SESSION_ENDURANCE_HOURS -> Category.ENDURANCE;
            case PRECISION_40K_SESSIONS, PRECISION_50K_SESSIONS -> Category.PRECISION;
        };
    }

    private static Item iconFor(MmmAdvancementDefinition definition) {
        return switch (definition.kind()) {
            case FIRST_USE -> Items.WOODEN_PICKAXE;
            case SESSION_HOURS -> Items.CLOCK;
            case STREAK_DAYS -> Items.BLAZE_POWDER;
            case BEST_HOUR_BLOCKS -> Items.GOLDEN_PICKAXE;
            case SESSION_ENDURANCE_HOURS, PRECISION_50K_SESSIONS -> Items.DIAMOND_PICKAXE;
            case PRECISION_40K_SESSIONS -> Items.IRON_PICKAXE;
        };
    }

    private static FrameType frameFor(MmmAdvancementDefinition definition) {
        return switch (definition.kind()) {
            case FIRST_USE -> FrameType.TASK;
            case SESSION_HOURS, STREAK_DAYS -> FrameType.GOAL;
            default -> FrameType.CHALLENGE;
        };
    }

    private enum Category { SESSION_HOURS, STREAKS, SPEED, ENDURANCE, PRECISION }
}
