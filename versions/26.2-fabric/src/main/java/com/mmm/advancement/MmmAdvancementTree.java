package com.mmm.advancement;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.triggers.ImpossibleTrigger;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MmmAdvancementTree
{
    private static final String CRITERION = "unlock";
    private static final String PROGRESS_CRITERION_PREFIX = "progress_";
    private static final int PROGRESS_STEPS = 100;
    private static final Identifier ROOT_ID = Identifier.fromNamespaceAndPath("mmm", "root");
    private static final Map<MmmAdvancementDefinition, AdvancementNode> NODES = new EnumMap<>(MmmAdvancementDefinition.class);
    private static final Map<Category, AdvancementNode> CATEGORY_NODES = new EnumMap<>(Category.class);
    private static final AdvancementNode ROOT;

    static
    {
        ROOT = createNode(
                ROOT_ID,
                null,
                "MMM",
                "Session hours, streaks, speed, and endurance.",
                Items.NETHERITE_PICKAXE,
                AdvancementType.TASK,
                0F,
                3F,
                Optional.of(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("block/blackstone"))));

        addCategory(Category.SESSION_HOURS, "Session Hours", "Earned from active time in saved sessions.", Items.CLOCK, 0F);
        addCategory(Category.STREAKS, "Streaks", "Mine at least 10,000 blocks on consecutive days.", Items.FLINT_AND_STEEL, 2F);
        addCategory(Category.SPEED, "Speed Achievements", "Earned from a session's rolling Best Hour.", Items.GOLDEN_PICKAXE, 4F);
        addCategory(Category.ENDURANCE, "Endurance", "Earned from active time in one session.", Items.DIAMOND_PICKAXE, 6F);
        addCategory(Category.PRECISION, "Precision", "Earned from consistently fast completed sessions.", Items.COMPASS, 8F);

        Map<Category, AdvancementNode> branchParents = new EnumMap<>(Category.class);
        branchParents.putAll(CATEGORY_NODES);
        for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED)
        {
            Category category = categoryFor(definition);
            AdvancementNode parent = branchParents.get(category);
            AdvancementNode node = createNode(
                    Identifier.fromNamespaceAndPath("mmm", definition.id()),
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
            var tabs = ((MmmAdvancementScreenAccess) screen).mmm$tabs();
            var tab = net.minecraft.client.gui.screens.advancements.AdvancementTab.create(net.minecraft.client.Minecraft.getInstance(), screen, tabs.size(), ROOT);
            if (tab == null) return;
            tabs.put(ROOT.holder(), tab);
            for (Category category : Category.values())
            {
                tab.addAdvancement(CATEGORY_NODES.get(category));
            }
            for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED)
            {
                tab.addAdvancement(NODES.get(definition));
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
        screen.getAdvancementWidget(ROOT).setProgress(completedProgress(ROOT));
        for (Category category : Category.values())
        {
            AdvancementNode node = CATEGORY_NODES.get(category);
            screen.getAdvancementWidget(node).setProgress(completedProgress(node));
        }
        MmmAdvancementSnapshot snapshot = MmmAdvancementManager.snapshot();
        for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED)
        {
            AdvancementNode node = NODES.get(definition);
            screen.getAdvancementWidget(node).setProgress(progressFor(node, definition, snapshot));
            Object widget = screen.getAdvancementWidget(node);
            if (widget instanceof MmmAdvancementWidgetProgress progressWidget)
            {
                progressWidget.mmm$setProgressDescription(definition.description(), definition.progressLabel(snapshot));
            }
        }
    }

    public static AdvancementHolder entry(MmmAdvancementDefinition definition)
    {
        AdvancementNode node = NODES.get(definition);
        return node == null ? ROOT.holder() : node.holder();
    }

    public static AdvancementHolder rootEntry()
    {
        return ROOT.holder();
    }

    private static AdvancementNode createNode(
            Identifier id,
            AdvancementNode parent,
            String title,
            String description,
            Item icon,
            AdvancementType frame,
            float x,
            float y,
            Optional<ClientAsset.ResourceTexture> background)
    {
        return createNode(id, parent, title, description, icon, frame, x, y, background, 1);
    }

    private static AdvancementNode createNode(
            Identifier id,
            AdvancementNode parent,
            String title,
            String description,
            Item icon,
            AdvancementType frame,
            float x,
            float y,
            Optional<ClientAsset.ResourceTexture> background,
            int progressSteps)
    {
        DisplayInfo display = new DisplayInfo(
                new net.minecraft.world.item.ItemStackTemplate(icon),
                Component.literal(title),
                Component.literal(description),
                background,
                frame,
                true,
                false,
                false);
        Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
        int steps = Math.max(1, progressSteps);
        for (int index = 0; index < steps; index++)
        {
            String key = steps == 1 ? CRITERION : progressCriterion(index);
            criteria.put(key, new Criterion<>(CriteriaTriggers.IMPOSSIBLE, new ImpossibleTrigger.TriggerInstance()));
        }
        Advancement advancement = new Advancement(
                parent == null ? Optional.empty() : Optional.of(parent.holder().id()),
                Optional.of(display),
                AdvancementRewards.EMPTY,
                criteria,
                AdvancementRequirements.allOf(criteria.keySet()),
                false);
        var node = new AdvancementNode(new AdvancementHolder(id, advancement), parent);
        display.setLocation(x, y);
        return node;
    }

    private static AdvancementProgress emptyProgress(AdvancementNode node)
    {
        AdvancementProgress progress = new AdvancementProgress();
        progress.update(node.advancement().requirements());
        return progress;
    }

    private static AdvancementProgress completedProgress(AdvancementNode node)
    {
        AdvancementProgress progress = emptyProgress(node);
        node.advancement().criteria().keySet().forEach(progress::grantProgress);
        return progress;
    }

    private static AdvancementProgress progressFor(
            AdvancementNode node,
            MmmAdvancementDefinition definition,
            MmmAdvancementSnapshot snapshot)
    {
        AdvancementProgress progress = emptyProgress(node);
        int obtained = MmmAdvancementManager.isUnlocked(definition)
                ? PROGRESS_STEPS
                : Math.min(PROGRESS_STEPS - 1, (int) Math.floor(definition.progressFraction(snapshot) * PROGRESS_STEPS));
        for (int index = 0; index < obtained; index++)
        {
            progress.grantProgress(progressCriterion(index));
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
        AdvancementNode node = createNode(
                Identifier.fromNamespaceAndPath("mmm", "category/" + category.name().toLowerCase()),
                ROOT,
                title,
                description,
                icon,
                AdvancementType.TASK,
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

    private static AdvancementType frameFor(MmmAdvancementDefinition definition)
    {
        return switch (definition.kind())
        {
            case FIRST_USE -> AdvancementType.TASK;
            case SESSION_HOURS, STREAK_DAYS -> AdvancementType.GOAL;
            case BEST_HOUR_BLOCKS, SESSION_ENDURANCE_HOURS, PRECISION_40K_SESSIONS, PRECISION_50K_SESSIONS -> AdvancementType.CHALLENGE;
        };
    }

    public static boolean isMmmRoot(AdvancementNode root)
    {
        return root != null && ROOT_ID.equals(root.holder().id());
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
