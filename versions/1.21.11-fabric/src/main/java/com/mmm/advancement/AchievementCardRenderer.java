package com.mmm.advancement;

import com.mmm.config.FeatureToggle;
import com.mmm.tracker.CollectionProgress;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

public final class AchievementCardRenderer
{
    private static final AchievementCardState STATE = new AchievementCardState();
    private static long lastFrame;
    private static AchievementCardState.Card displayed;
    private static ItemStack icon = ItemStack.EMPTY;
    private static AchievementBadges.Badge badge;

    private AchievementCardRenderer() {}

    public static void show(MmmAdvancementDefinition definition) { STATE.offer(definition); }

    public static void showCollection(String collectionId, String name, long milestone)
    {
        STATE.offerCollection(collectionId, name, milestone);
    }

    public static void clear()
    {
        STATE.clear();
        lastFrame = 0;
        displayed = null;
        icon = ItemStack.EMPTY;
        badge = null;
    }

    public static void render(DrawContext context, MinecraftClient client)
    {
        if (client.player == null || client.world == null)
        {
            clear();
            return;
        }
        long now = System.nanoTime();
        if (client.options.hudHidden || client.currentScreen != null || client.isPaused())
        {
            lastFrame = 0;
            return;
        }
        if (lastFrame != 0) STATE.advance(Math.min(0.1, (now - lastFrame) / 1_000_000_000.0));
        lastFrame = now;
        AchievementCardState.Card card = STATE.currentCard();
        if (card == null)
        {
            displayed = null;
            return;
        }
        if (displayed != card)
        {
            displayed = card;
            badge = AchievementBadges.forDefinition(card.definition());
            icon = card.definition() != null
                    ? MmmAdvancementTree.entry(card.definition()).value().display()
                        .map(display -> display.getIcon().copy()).orElse(ItemStack.EMPTY)
                    : new ItemStack(Registries.BLOCK.get(Identifier.of(CollectionProgress.iconId(card.collectionId()))));
            if (FeatureToggle.MMM_SOUND_ALERTS.getBooleanValue())
            {
                client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1F));
            }
        }

        String heading = card.heading();
        int width = Math.max(224, 58 + Math.max(client.textRenderer.getWidth(heading),
                client.textRenderer.getWidth(card.title())));
        int height = 50;
        float scale = Math.min(1F, Math.max(1, context.getScaledWindowWidth() - 16) / (float) width);
        float targetY = Math.min(72, context.getScaledWindowHeight() * 0.14F);
        float y = (float) (-height * scale - 2 + (targetY + height * scale + 2) * STATE.visibility());
        // The 2D GUI stack has no Z translation; a root layer keeps the card above earlier HUD content.
        context.createNewRootLayer();
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        try
        {
            matrices.translate((context.getScaledWindowWidth() - width * scale) / 2F, y);
            matrices.scale(scale, scale);
            context.fill(0, 0, width, height, 0xF2101010);
            com.mmm.ui.MmmUi.drawBorder(context, 0, 0, width, height, 0xFF454545);
            if (badge != null)
            {
                AchievementBadges.draw(context, badge, 6, 7, 36);
            }
            else
            {
                matrices.pushMatrix();
                try
                {
                    matrices.translate(12, 13);
                    matrices.scale(1.5F, 1.5F);
                    context.drawItem(icon, 0, 0);
                }
                finally
                {
                    matrices.popMatrix();
                }
            }
            context.drawText(client.textRenderer, Text.literal(heading), 46, 13, 0xFFFFDC6E, false);
            context.drawText(client.textRenderer, Text.literal(card.title()), 46, 28, 0xFFFFFFFF, false);
        }
        finally
        {
            matrices.popMatrix();
        }
    }
}
