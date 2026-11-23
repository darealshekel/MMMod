package com.mmm.smoke;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.config.value.IConfigBoolean;
import com.mmm.feature.SmallDigItemRenderer;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.TextGuiElementRenderState;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.joml.Vector3f;

/** Run on the client thread after the smoke world and resource pack have loaded. */
public final class RenderParityChecks
{
    private RenderParityChecks() {}

    public static void run(MinecraftClient client) throws Exception
    {
        check(client.isOnThread() && client.world != null && client.player != null
                && client.interactionManager != null, "Render parity checks require a loaded client world");
        check(client.player.getJumpingMount() == null, "Render parity fixture must not be riding a jumping mount");
        checkGoalLabel(client);
        checkItemScaling(client);
        System.out.println("PASS: XP-zero goal label, vanilla fallback, and small-item transform parity");
    }

    private static void checkGoalLabel(MinecraftClient client) throws Exception
    {
        Method render = InGameHud.class.getDeclaredMethod("renderMainHud", DrawContext.class, RenderTickCounter.class);
        render.setAccessible(true);
        Field gameMode = field(ClientPlayerInteractionManager.class, "gameMode");
        Field currentBar = field(InGameHud.class, "currentBar");
        Object oldMode = gameMode.get(client.interactionManager);
        Object oldBar = currentBar.get(client.inGameHud);
        int oldLevel = client.player.experienceLevel;
        boolean oldTab = client.options.playerListKey.isPressed();
        IConfigBoolean[] gates = {
                FeatureToggle.MMM_MINING_TRACKER, FeatureToggle.MMM_DAILY_GOAL,
                FeatureToggle.MMM_HUD_GOAL_PROGRESS, Configs.Generic.SHOW_GOAL_PERCENT,
                Configs.Generic.ALWAYS_OVERRIDE_XP_BAR
        };
        boolean[] oldGates = new boolean[gates.length];
        for (int i = 0; i < gates.length; i++) oldGates[i] = gates[i].getBooleanValue();
        try
        {
            // Only change the local HUD gate, not player abilities or the server's game mode.
            gameMode.set(client.interactionManager, GameMode.SURVIVAL);
            client.options.playerListKey.setPressed(false);
            for (IConfigBoolean gate : gates) gate.setBooleanValue(true);
            String percent = UiFormat.formatGoalPercent(MiningStats.getDailyGoalProgress());
            for (int level : new int[] {0, 7})
            {
                client.player.experienceLevel = level;
                assertLabels(client, render, percent, true, false);
                for (IConfigBoolean gate : gates)
                {
                    gate.setBooleanValue(false);
                    assertLabels(client, render, percent, false, level > 0);
                    gate.setBooleanValue(true);
                }
            }

            client.player.experienceLevel = 0;
            Configs.Generic.ALWAYS_OVERRIDE_XP_BAR.setBooleanValue(false);
            client.options.playerListKey.setPressed(true);
            assertLabels(client, render, percent, true, false);
            client.options.playerListKey.setPressed(false);
            assertLabels(client, render, percent, false, false);
            Configs.Generic.ALWAYS_OVERRIDE_XP_BAR.setBooleanValue(true);
            for (GameMode mode : new GameMode[] {GameMode.CREATIVE, GameMode.SPECTATOR})
            {
                gameMode.set(client.interactionManager, mode);
                client.player.experienceLevel = 7;
                assertLabels(client, render, percent, false, false);
            }
        }
        finally
        {
            for (int i = 0; i < gates.length; i++) gates[i].setBooleanValue(oldGates[i]);
            client.options.playerListKey.setPressed(oldTab);
            client.player.experienceLevel = oldLevel;
            gameMode.set(client.interactionManager, oldMode);
            currentBar.set(client.inGameHud, oldBar);
        }
    }

    private static void assertLabels(MinecraftClient client, Method render, String percent,
                                     boolean expectGoal, boolean expectVanilla) throws Exception
    {
        GuiRenderState state = new GuiRenderState();
        DrawContext context = new DrawContext(client, state, 0, 0);
        // Exercise vanilla's actual XP-zero branch, not just the injected helper.
        render.invoke(client.inGameHud, context, RenderTickCounter.ZERO);
        List<TextGuiElementRenderState> labels = new ArrayList<>();
        int y = context.getScaledWindowHeight() - 35;
        state.forEachTextElement(text -> {
            if (Math.abs(text.y - y) <= 1) labels.add(text);
        });
        String vanilla = Text.translatable("gui.experience.level", client.player.experienceLevel).getString();
        List<TextGuiElementRenderState> goal = labels.stream()
                .filter(text -> plain(text.orderedText).equals(percent)).toList();
        long vanillaCount = labels.stream().filter(text -> plain(text.orderedText).equals(vanilla)).count();
        check(goal.size() == (expectGoal ? 5 : 0), "Wrong goal label count at XP " + client.player.experienceLevel);
        check(vanillaCount == (expectVanilla ? 5 : 0), "Vanilla XP label was lost or drawn over goal percent");
        if (expectGoal)
        {
            int x = (context.getScaledWindowWidth() - client.textRenderer.getWidth(percent)) / 2;
            int color = UiFormat.getGoalProgressColor(MiningStats.getDailyGoalProgress());
            check(goal.stream().filter(text -> text.x == x && text.y == y && text.color == color).count() == 1,
                    "Goal foreground is not centered or has the wrong color");
            for (int[] offset : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                check(goal.stream().anyMatch(text -> text.x == x + offset[0] && text.y == y + offset[1]
                        && text.color == 0xFF000000), "Goal outline is missing or transparent");
        }
        state.clear();
    }

    private static void checkItemScaling(MinecraftClient client) throws Exception
    {
        boolean oldEnabled = Configs.Generic.SMALL_DIG_ITEMS.getBooleanValue();
        double oldScale = Configs.Generic.SMALL_DIG_ITEM_SCALE.getDoubleValue();
        float oldActive = SmallDigItemRenderer.getActiveScale();
        try
        {
            Configs.Generic.SMALL_DIG_ITEM_SCALE.setDoubleValue(0.4D);
            Configs.Generic.SMALL_DIG_ITEMS.setBooleanValue(true);
            Transformation source = new Transformation(new Vector3f(23, -37, 11),
                    new Vector3f(0.3F, -0.2F, 0.8F), new Vector3f(0.7F, 1.1F, -0.4F));
            Vector3f rotation = new Vector3f(source.rotation());
            Vector3f translation = new Vector3f(source.translation());
            Vector3f modelScale = new Vector3f(source.scale());
            for (float scale : new float[] {0.05F, 0.2F, 0.6F, 1.0F})
            {
                SmallDigItemRenderer.begin(scale);
                Transformation scaled = SmallDigItemRenderer.applyActiveScale(source);
                check(scaled == SmallDigItemRenderer.applyActiveScale(source), "Transform cache missed unchanged scale");
                if (scale == 1.0F) check(scaled == source, "Unit scale replaced the original transform");
                assertOuterScale(source, scaled, scale, false);
                assertOuterScale(source, scaled, scale, true);
                assertOuterScale(Transformation.IDENTITY,
                        SmallDigItemRenderer.applyActiveScale(Transformation.IDENTITY), scale, false);
                check(source.rotation().equals(rotation) && source.translation().equals(translation)
                        && source.scale().equals(modelScale), "Scaling mutated the baked model transform");
            }
            check(SmallDigItemRenderer.applyActiveScale(null) == null, "Null transform changed");
            SmallDigItemRenderer.end();
            check(SmallDigItemRenderer.getActiveScale() == 1.0F, "Active scale leaked after end");

            ItemStack stone = new ItemStack(Items.STONE);
            for (ItemDisplayContext display : ItemDisplayContext.values())
            {
                float expected = switch (display)
                {
                    case GROUND -> 0.2F;
                    case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                            THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.4F;
                    default -> 1.0F;
                };
                Configs.Generic.SMALL_DIG_ITEMS.setBooleanValue(true);
                check(SmallDigItemRenderer.getScale(stone, display) == expected, "Wrong scale for " + display);
                List<Transformation> scaled = modelTransforms(client, stone, display);
                Configs.Generic.SMALL_DIG_ITEMS.setBooleanValue(false);
                List<Transformation> vanilla = modelTransforms(client, stone, display);
                check(!vanilla.isEmpty() && vanilla.size() == scaled.size(), "Missing item-model layers for " + display);
                boolean left = display == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                        || display == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                for (int i = 0; i < vanilla.size(); i++) assertOuterScale(vanilla.get(i), scaled.get(i), expected, left);
                check(SmallDigItemRenderer.getActiveScale() == 1.0F, "Item-model update leaked active scale");
            }
            Configs.Generic.SMALL_DIG_ITEMS.setBooleanValue(true);
            for (ItemStack stack : new ItemStack[] {ItemStack.EMPTY, new ItemStack(Items.DIAMOND_PICKAXE),
                    new ItemStack(Items.SHEARS), new ItemStack(Items.BEDROCK)})
                check(SmallDigItemRenderer.getScale(stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) == 1.0F,
                        "Empty stack, tool, or untracked block was scaled");
            Configs.Generic.SMALL_DIG_ITEM_SCALE.setDoubleValue(0.1D);
            check(SmallDigItemRenderer.getScale(stone, ItemDisplayContext.GROUND) == 0.05F, "Ground minimum changed");
            Configs.Generic.SMALL_DIG_ITEM_SCALE.setDoubleValue(1.0D);
            check(SmallDigItemRenderer.getScale(stone, ItemDisplayContext.GROUND) == 0.5F, "Ground half-scale changed");
            check(SmallDigItemRenderer.getScale(stone, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) == 1.0F,
                    "Full-size hand scale changed");
        }
        finally
        {
            Configs.Generic.SMALL_DIG_ITEMS.setBooleanValue(oldEnabled);
            Configs.Generic.SMALL_DIG_ITEM_SCALE.setDoubleValue(oldScale);
            SmallDigItemRenderer.begin(oldActive);
        }
    }

    private static List<Transformation> modelTransforms(MinecraftClient client, ItemStack stack,
                                                        ItemDisplayContext display) throws Exception
    {
        ItemRenderState state = new ItemRenderState();
        client.getItemModelManager().updateForLivingEntity(state, stack, display, client.player);
        int count = field(ItemRenderState.class, "layerCount").getInt(state);
        var layers = (ItemRenderState.LayerRenderState[]) field(ItemRenderState.class, "layers").get(state);
        Field transform = field(ItemRenderState.LayerRenderState.class, "transform");
        List<Transformation> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add((Transformation) transform.get(layers[i]));
        return result;
    }

    private static void assertOuterScale(Transformation source, Transformation actual, float scale, boolean left)
    {
        MatrixStack expected = baseMatrix();
        expected.scale(scale, scale, scale);
        source.apply(left, expected.peek());
        MatrixStack result = baseMatrix();
        actual.apply(left, result.peek());
        check(expected.peek().getPositionMatrix().equals(result.peek().getPositionMatrix(), 0.00001F),
                "Small-item matrix differs from outer scale (scale=" + scale + ", left=" + left + ")");
        // Match VertexConsumer.quad: Entry normalizes nonuniform scales while transforming each normal.
        for (Vector3f normal : new Vector3f[] {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0),
                new Vector3f(0, 0, 1), new Vector3f(-1, 0, 0), new Vector3f(1, 2, -3).normalize()})
            check(expected.peek().transformNormal(normal, new Vector3f()).equals(
                    result.peek().transformNormal(normal, new Vector3f()), 0.00001F),
                    "Small-item rendered normal changed");
    }

    private static MatrixStack baseMatrix()
    {
        MatrixStack matrices = new MatrixStack();
        matrices.translate(0.4F, -0.7F, 1.2F);
        matrices.multiply(new org.joml.Quaternionf().rotationXYZ(0.2F, -0.5F, 0.1F));
        return matrices;
    }

    private static String plain(OrderedText text)
    {
        StringBuilder result = new StringBuilder();
        text.accept((index, style, codePoint) -> { result.appendCodePoint(codePoint); return true; });
        return result.toString();
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException
    {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
