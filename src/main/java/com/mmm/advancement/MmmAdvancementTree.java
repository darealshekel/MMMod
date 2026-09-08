package com.mmm.advancement;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.advancement.criterion.ImpossibleCriterion;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class MmmAdvancementTree
{
    private static final String CRITERION = "unlock";
    private static final String PROGRESS_CRITERION_PREFIX = "progress_";
    private static final int PROGRESS_STEPS = 100;
    private static final Identifier ROOT_ID = Identifier.of("mmm", "root");
    private static final Map<MmmAdvancementDefinition, Advancement> NODES = new EnumMap<>(MmmAdvancementDefinition.class);
    private static final Map<Category, Advancement> CATEGORY_NODES = new EnumMap<>(Category.class);
    private static final Advancement ROOT;

    static
    {
        ROOT = createNode(
                ROOT_ID,
                null,
                "MMM",
                "Session hours, streaks, speed, and endurance.",
                Items.NETHERITE_PICKAXE,
                AdvancementFrame.TASK,
                0F,
                3F,
                Optional.of(Identifier.of("minecraft", "textures/block/blackstone.png")));

        addCategory(Category.SESSION_HOURS, "Session Hours", "Earned from active time in saved sessions.", Items.CLOCK, 0F);
        addCategory(Category.STREAKS, "Streaks", "Mine at least 10,000 blocks on consecutive days.", Items.FLINT_AND_STEEL, 2F);
        addCategory(Category.SPEED, "Speed Achievements", "Earned from a session's rolling Best Hour.", Items.GOLDEN_PICKAXE, 4F);
        addCategory(Category.ENDURANCE, "Endurance", "Earned from active time in one session.", Items.DIAMOND_PICKAXE, 6F);
        addCategory(Category.PRECISION, "Precision", "Earned from consistently fast completed sessions.", Items.COMPASS, 8F);

        Map<Category, Advancement> branchParents = new EnumMap<>(Category.class);
        branchParents.putAll(CATEGORY_NODES);
        for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED)
        {
            Category category = categoryFor(definition);
            Advancement parent = branchParents.get(category);
            Advancement node = createNode(
                    Identifier.of("mmm", definition.id()),
                    parent,
                    definition.title(),
                    definition.description(),
                    iconFor(definition),
                    frameFor(definition),
                    definition.x() + 1F,
                    definition.y(),
                    Optional.empty(),
                    PROGRESS_STEPS);
            parent.addChild(node);
            NODES.put(definition, node);
            branchParents.put(category, node);
        }
    }

    private MmmAdvancementTree()
    {
    }

    public static void install(AdvancementsScreen screen)
    {
        if (screen == null)
        {
            return;
        }
        if (screen.getAdvancementWidget(ROOT) == null)
        {
            screen.onRootAdded(ROOT);
            for (Category category : Category.values())
            {
                screen.onDependentAdded(CATEGORY_NODES.get(category));
            }
            for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED)
            {
                screen.onDependentAdded(NODES.get(definition));
            }
        }
        updateProgress(screen);
    }

    public static void updateProgress(AdvancementsScreen screen)
    {
        if (screen == null || screen.getAdvancementWidget(ROOT) == null)
        {
            return;
        }
        MmmAdvancementManager.refreshForDisplay();
        screen.setProgress(ROOT, completedProgress(ROOT));
        for (Category category : Category.values())
        {
            Advancement node = CATEGORY_NODES.get(category);
            screen.setProgress(node, completedProgress(node));
        }
        MmmAdvancementSnapshot snapshot = MmmAdvancementManager.snapshot();
        for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED)
        {
            Advancement node = NODES.get(definition);
            screen.setProgress(node, progressFor(node, definition, snapshot));
            Object widget = screen.getAdvancementWidget(node);
            if (widget instanceof MmmAdvancementWidgetProgress progressWidget)
            {
                progressWidget.mmm$setProgressDescription(definition.description(), definition.progressLabel(snapshot));
            }
        }
    }

    public static Advancement entry(MmmAdvancementDefinition definition)
    {
        Advancement node = NODES.get(definition);
        return node == null ? ROOT : node;
    }

    public static Advancement rootEntry()
    {
        return ROOT;
    }

    private static Advancement createNode(
            Identifier id,
            Advancement parent,
            String title,
            String description,
            Item icon,
            AdvancementFrame frame,
            float x,
            float y,
            Optional<Identifier> background)
    {
        return createNode(id, parent, title, description, icon, frame, x, y, background, 1);
    }

    private static Advancement createNode(
            Identifier id,
            Advancement parent,
            String title,
            String description,
            Item icon,
            AdvancementFrame frame,
            float x,
            float y,
            Optional<Identifier> background,
            int progressSteps)
    {
        AdvancementDisplay display = new AdvancementDisplay(
                new ItemStack(icon),
                Text.literal(title),
                Text.literal(description),
                background.orElse(null),
                frame,
                true,
                false,
                false);
        display.setPos(x, y);
        Map<String, AdvancementCriterion> criteria = new LinkedHashMap<>();
        int steps = Math.max(1, progressSteps);
        for (int index = 0; index < steps; index++)
        {
            String key = steps == 1 ? CRITERION : progressCriterion(index);
            criteria.put(key, new AdvancementCriterion(new ImpossibleCriterion.Conditions()));
        }
        Advancement advancement = new Advancement(
                id,
                parent,
                display,
                AdvancementRewards.NONE,
                criteria,
                criteria.keySet().stream().map(key -> new String[] {key}).toArray(String[][]::new),
                false);
        return advancement;
    }

    private static AdvancementProgress emptyProgress(Advancement node)
    {
        AdvancementProgress progress = new AdvancementProgress();
        progress.init(node.getCriteria(), node.getRequirements());
        return progress;
    }

    private static AdvancementProgress completedProgress(Advancement node)
    {
        AdvancementProgress progress = emptyProgress(node);
        node.getCriteria().keySet().forEach(progress::obtain);
        return progress;
    }

    private static AdvancementProgress progressFor(
            Advancement node,
            MmmAdvancementDefinition definition,
            MmmAdvancementSnapshot snapshot)
    {
        AdvancementProgress progress = emptyProgress(node);
        int obtained = MmmAdvancementManager.isUnlocked(definition)
                ? PROGRESS_STEPS
                : Math.min(PROGRESS_STEPS - 1, (int) Math.floor(definition.progressFraction(snapshot) * PROGRESS_STEPS));
        for (int index = 0; index < obtained; index++)
        {
            progress.obtain(progressCriterion(index));
        }
        return progress;
    }

    private static String progressCriterion(int index)
    {
        return PROGRESS_CRITERION_PREFIX + String.format("%03d", index + 1);
    }

    private static Item iconFor(MmmAdvancementDefinition definition)
    {
        return switch (definition.kind())
        {
            case FIRST_USE -> Items.WOODEN_PICKAXE;
            case SESSION_HOURS -> Items.CLOCK;
            case STREAK_DAYS -> Items.BLAZE_POWDER;
            case BEST_HOUR_BLOCKS -> Items.GOLDEN_PICKAXE;
            case SESSION_ENDURANCE_HOURS -> Items.DIAMOND_PICKAXE;
            case PRECISION_40K_SESSIONS -> Items.IRON_PICKAXE;
            case PRECISION_50K_SESSIONS -> Items.NETHERITE_PICKAXE;
        };
    }

    private static void addCategory(Category category, String title, String description, Item icon, float y)
    {
        Advancement node = createNode(
                Identifier.of("mmm", "category/" + category.name().toLowerCase()),
                ROOT,
                title,
                description,
                icon,
                AdvancementFrame.TASK,
                1F,
                y,
                Optional.empty());
        ROOT.addChild(node);
        CATEGORY_NODES.put(category, node);
    }

    private static Category categoryFor(MmmAdvancementDefinition definition)
    {
        return switch (definition.kind())
        {
            case FIRST_USE, SESSION_HOURS -> Category.SESSION_HOURS;
            case STREAK_DAYS -> Category.STREAKS;
            case BEST_HOUR_BLOCKS -> Category.SPEED;
            case SESSION_ENDURANCE_HOURS -> Category.ENDURANCE;
            case PRECISION_40K_SESSIONS, PRECISION_50K_SESSIONS -> Category.PRECISION;
        };
    }

    private static AdvancementFrame frameFor(MmmAdvancementDefinition definition)
    {
        return switch (definition.kind())
        {
            case FIRST_USE -> AdvancementFrame.TASK;
            case SESSION_HOURS, STREAK_DAYS -> AdvancementFrame.GOAL;
            case BEST_HOUR_BLOCKS, SESSION_ENDURANCE_HOURS, PRECISION_40K_SESSIONS, PRECISION_50K_SESSIONS -> AdvancementFrame.CHALLENGE;
        };
    }

    public static boolean isMmmRoot(Advancement root)
    {
        return root != null && ROOT_ID.equals(root.getId());
    }

    private enum Category
    {
        SESSION_HOURS,
        STREAKS,
        SPEED,
        ENDURANCE,
        PRECISION
    }
}
