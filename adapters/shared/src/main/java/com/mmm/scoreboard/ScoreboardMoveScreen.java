package com.mmm.scoreboard;

import java.util.Optional;

import com.mmm.config.Configs;
import com.mmm.ui.MmmUi;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

public final class ScoreboardMoveScreen extends Screen
{
    private final Screen parent;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public ScoreboardMoveScreen(Screen parent)
    {
        super(Text.literal("Move Scoreboard"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        ControlLayout layout = this.controlLayout();
        this.addDrawableChild(MmmUi.backButton(this, MmmUi.contentLeft(this.width)));
        this.addDrawableChild(ButtonWidget.builder(Text.literal("-"), ignored -> this.adjustScale(-0.05D))
                .dimensions(layout.x(), layout.y(), 22, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), ignored -> this.adjustScale(0.05D))
                .dimensions(layout.x() + 26, layout.y(), 22, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), ignored -> this.resetLayout())
                .dimensions(layout.x() + layout.width() - 52, layout.y(), 52, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        context.fill(0, 0, this.width, this.height, 0x22050505);

        MinecraftClient client = MinecraftClient.getInstance();
        Optional<ScoreboardObjective> objective = ScoreboardService.getSidebarObjective(client);
        if (objective.isPresent() || com.mmm.leaderboard.LeaderboardSidebar.enabled())
        {
            ScoreboardHudRenderer.Bounds bounds = ScoreboardHudRenderer.render(context, client, objective.orElse(null));
            context.drawBorder(bounds.left() - 2, bounds.top() - 2,
                    bounds.width() + 4, bounds.height() + 4, MmmUi.accent());
        }
        else
        {
            int messageWidth = this.textRenderer.getWidth("No sidebar scoreboard is visible.");
            context.drawText(this.textRenderer, Text.literal("No sidebar scoreboard is visible."),
                    Math.max(8, (this.width - messageWidth) / 2), Math.max(96, this.height / 2), MmmUi.MUTED, false);
        }

        MmmUi.toolHeader(context, this.textRenderer, this.width, MmmUi.contentLeft(this.width), "Scoreboard / Move");
        this.drawControls(context);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (super.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
        ControlLayout layout = this.controlLayout();
        if (button != 0 || mouseY < MmmUi.TOP_BAR_HEIGHT || (mouseY >= layout.y() && mouseY < layout.y() + 20
                && mouseX >= layout.x() && mouseX < layout.x() + layout.width()))
        {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Optional<ScoreboardObjective> objective = ScoreboardService.getSidebarObjective(client);
        if (objective.isEmpty() && !com.mmm.leaderboard.LeaderboardSidebar.enabled())
        {
            return false;
        }
        ScoreboardHudRenderer.Bounds bounds = ScoreboardHudRenderer.getBounds(client, objective.orElse(null));
        if (!bounds.contains(mouseX, mouseY))
        {
            return false;
        }
        this.dragging = true;
        this.dragOffsetX = (int) mouseX - bounds.left();
        this.dragOffsetY = (int) mouseY - bounds.top();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        if (this.dragging)
        {
            MinecraftClient client = MinecraftClient.getInstance();
            var objective = ScoreboardService.getSidebarObjective(client);
            if (objective.isPresent() || com.mmm.leaderboard.LeaderboardSidebar.enabled())
                ScoreboardHudRenderer.setPosition(client, objective.orElse(null),
                        (int) mouseX - this.dragOffsetX, (int) mouseY - this.dragOffsetY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (button == 0 && this.dragging)
        {
            this.dragging = false;
            Configs.saveToFile();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        this.adjustScale(verticalAmount > 0.0D ? 0.05D : -0.05D);
        return true;
    }

    @Override
    public void close()
    {
        Configs.saveToFile();
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean shouldPause()
    {
        return MmmUi.shouldPauseGame();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta)
    {
    }

    private void drawControls(DrawContext context)
    {
        ControlLayout layout = this.controlLayout();
        MmmUi.boardRow(context, layout.x(), layout.y(), layout.width(), 20, false, false);
        String size = "Size: " + Math.round(Configs.Generic.SCOREBOARD_SCALE.getDoubleValue() * 100.0D) + "%";
        MmmUi.drawTextWithin(context, this.textRenderer, size, layout.x() + 55, layout.y() + 6,
                layout.width() - 111, MmmUi.TEXT, false);
    }

    private void adjustScale(double delta)
    {
        double scale = Math.max(0.5D, Math.min(2.0D,
                Configs.Generic.SCOREBOARD_SCALE.getDoubleValue() + delta));
        Configs.Generic.SCOREBOARD_SCALE.setDoubleValue(Math.round(scale * 100.0D) / 100.0D);
        Configs.saveToFile();
    }

    private void resetLayout()
    {
        ScoreboardHudRenderer.resetPosition();
        Configs.Generic.SCOREBOARD_SCALE.resetToDefault();
        Configs.saveToFile();
    }

    private ControlLayout controlLayout()
    {
        int width = Math.min(210, MmmUi.contentWidth(this.width));
        return new ControlLayout((this.width - width) / 2, MmmUi.TOP_BAR_HEIGHT + 5, width);
    }

    private record ControlLayout(int x, int y, int width)
    {
    }
}
