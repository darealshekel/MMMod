package com.mmm.legacy.achievement;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.legacy.LegacyDrawContext;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;

public final class LegacyGoalHud {
    private LegacyGoalHud() {}

    public static boolean render(Window window, int x) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options == null
                || !FeatureToggle.MMM_MINING_TRACKER.getBooleanValue()
                || !FeatureToggle.MMM_DAILY_GOAL.getBooleanValue()
                || !FeatureToggle.MMM_HUD_GOAL_PROGRESS.getBooleanValue()
                || (!Configs.Generic.ALWAYS_OVERRIDE_XP_BAR.getBooleanValue() && !client.options.playerListKey.isPressed()))
            return false;
        MiningStats.GoalProgress progress = MiningStats.getDailyGoalProgress();
        if (!progress.enabled()) return false;
        int y = window.getHeight() - 29;
        int color = UiFormat.getGoalProgressColor(progress);
        int filled = filledWidth(progress.current(), progress.target());
        LegacyDrawContext context = new LegacyDrawContext();
        GlStateManager.enableBlend();
        GlStateManager.color4f(1, 1, 1, 1);
        client.getTextureManager().bind(GuiElement.ICONS_LOCATION);
        GuiElement.drawTexture(x, y, 0F, 64F, 182, 5, 256F, 256F);
        if (filled > 0) {
            Identifier texture = GoalProgressTexture.get(client, color);
            if (texture != null) {
                client.getTextureManager().bind(texture);
                GuiElement.drawTexture(x, y, 0F, 0F, filled, 5, 182F, 5F);
            } else {
                context.fill(x, y + 1, x + filled, y + 4, color);
                if (filled > 1) {
                    context.fill(x + 1, y, x + Math.min(filled, 181), y + 1, color);
                    context.fill(x + 1, y + 4, x + Math.min(filled, 181), y + 5, color);
                }
            }
        }
        boolean showPercent = Configs.Generic.SHOW_GOAL_PERCENT.getBooleanValue();
        String text = showPercent ? UiFormat.formatGoalPercent(progress)
                : client.player.xpLevel > 0 ? Integer.toString(client.player.xpLevel) : "";
        int textColor = showPercent ? color : 8453920;
        int textX = (window.getWidth() - client.textRenderer.getWidth(text)) / 2;
        int textY = window.getHeight() - 35;
        context.drawText(client.textRenderer, text, textX + 1, textY, 0, false);
        context.drawText(client.textRenderer, text, textX - 1, textY, 0, false);
        context.drawText(client.textRenderer, text, textX, textY + 1, 0, false);
        context.drawText(client.textRenderer, text, textX, textY - 1, 0, false);
        context.drawText(client.textRenderer, text, textX, textY, textColor, false);
        client.getTextureManager().bind(GuiElement.ICONS_LOCATION);
        GlStateManager.color4f(1, 1, 1, 1);
        GlStateManager.disableBlend();
        return true;
    }

    public static int filledWidth(long current, long target) {
        if (current <= 0 || target <= 0) return 0;
        return Math.max(1, Math.min(182, (int) (Math.min(1D, current / (double) target) * 183D)));
    }
}
