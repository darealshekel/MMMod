package com.mmm.mixin;

import java.util.List;

import com.mmm.config.Configs;
import com.mmm.config.Configs.ScoreboardPosition;
import com.mmm.scoreboard.ScoreboardService;
import com.mmm.scoreboard.ScoreboardState;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class ScoreboardInGameHudMixin
{
    @Shadow @Final private MinecraftClient client;

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mmm$renderConfiguredScoreboard(DrawContext context, ScoreboardObjective objective, CallbackInfo ci)
    {
        ci.cancel();
        if (!Configs.Generic.SCOREBOARD_VISIBLE.getBooleanValue())
        {
            return;
        }

        List<ScoreboardService.RenderEntry> allEntries = ScoreboardService.getRenderEntries(objective);
        int pageSize = Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue();
        ScoreboardState.clampPage(allEntries.size(), Math.max(1, pageSize));
        int from = pageSize <= 0 ? 0 : Math.min(ScoreboardState.getPageOffset(), allEntries.size());
        int to = pageSize <= 0 ? 0 : Math.min(allEntries.size(), from + pageSize);
        List<ScoreboardService.RenderEntry> entries = allEntries.subList(from, to);

        TextRenderer renderer = this.client.textRenderer;
        Text title = objective.getDisplayName();
        int separatorWidth = renderer.getWidth(": ");
        int contentWidth = renderer.getWidth(title);
        for (ScoreboardService.RenderEntry entry : entries)
        {
            int scoreWidth = renderer.getWidth(entry.score());
            contentWidth = Math.max(contentWidth,
                    renderer.getWidth(entry.name()) + (scoreWidth > 0 ? separatorWidth + scoreWidth : 0));
        }

        int rowHeight = renderer.fontHeight;
        int panelWidth = contentWidth + 6;
        int titleHeight = rowHeight + 2;
        int panelHeight = titleHeight + entries.size() * rowHeight + 2;
        float scale = (float) Math.max(0.5D, Math.min(2.0D, Configs.Generic.SCOREBOARD_SCALE.getDoubleValue()));
        ScoreboardPosition position = (ScoreboardPosition) Configs.Generic.SCOREBOARD_POSITION.getOptionListValue();
        int scaledPanelWidth = Math.round(panelWidth * scale);
        int scaledPanelHeight = Math.round(panelHeight * scale);
        int x = position.isLeft() ? 3 : context.getScaledWindowWidth() - scaledPanelWidth - 3;
        int y = switch (position)
        {
            case LEFT_UPPER, RIGHT_UPPER -> 3;
            case LEFT_LOWER, RIGHT_LOWER -> context.getScaledWindowHeight() - scaledPanelHeight - 3;
            case LEFT, RIGHT -> (context.getScaledWindowHeight() - scaledPanelHeight) / 2;
        };
        y += Configs.Generic.SCOREBOARD_Y_OFFSET.getIntegerValue();

        int bodyBackground = this.client.options.getTextBackgroundColor(
                (float) Configs.Generic.SCOREBOARD_BODY_OPACITY.getDoubleValue());
        int titleBackground = this.client.options.getTextBackgroundColor(
                (float) Configs.Generic.SCOREBOARD_TITLE_OPACITY.getDoubleValue());
        int textColor = withAlpha(0xFFFFFF, Configs.Generic.SCOREBOARD_TEXT_OPACITY.getDoubleValue());
        int titleColor = withAlpha(0xFFFFFF, Configs.Generic.SCOREBOARD_TITLE_TEXT_OPACITY.getDoubleValue());

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.fill(0, 0, panelWidth, titleHeight, titleBackground);
        context.fill(0, titleHeight, panelWidth, panelHeight, bodyBackground);
        context.drawText(renderer, title, (panelWidth - renderer.getWidth(title)) / 2, 1, titleColor, false);
        for (int index = 0; index < entries.size(); index++)
        {
            ScoreboardService.RenderEntry entry = entries.get(index);
            int rowY = titleHeight + 1 + index * rowHeight;
            context.drawText(renderer, entry.name(), 2, rowY, textColor, false);
            int scoreWidth = renderer.getWidth(entry.score());
            if (scoreWidth > 0)
            {
                context.drawText(renderer, entry.score(), panelWidth - scoreWidth - 2, rowY, textColor, false);
            }
        }
        context.getMatrices().popMatrix();
    }

    private static int withAlpha(int rgb, double opacity)
    {
        int alpha = Math.max(0, Math.min(255, (int) Math.round(opacity * 255.0D)));
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
