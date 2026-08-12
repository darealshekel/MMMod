package com.mmm.scoreboard;

import com.mmm.ui.CompatScreen;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import com.mmm.config.Configs;
import com.mmm.ui.MmmUi;

public final class ScoreboardMoveScreen extends CompatScreen
{
    private final Screen parent;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public ScoreboardMoveScreen(Screen parent)
    {
        super(Component.literal("Move Scoreboard"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        ControlLayout layout = this.controlLayout();
        this.addRenderableWidget(Button.builder(Component.literal("-"), ignored -> this.adjustScale(-0.05D))
                .bounds(layout.x() + 12, layout.y() + layout.height() - 28, 22, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> this.adjustScale(0.05D))
                .bounds(layout.x() + 38, layout.y() + layout.height() - 28, 22, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), ignored -> this.resetLayout())
                .bounds(layout.x() + layout.width() - 150, layout.y() + layout.height() - 28, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> this.onClose())
                .bounds(layout.x() + layout.width() - 80, layout.y() + layout.height() - 28, 64, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        context.fill(0, 0, this.width, this.height, 0x22050505);

        Minecraft client = Minecraft.getInstance();
        Optional<Objective> objective = ScoreboardService.getSidebarObjective(client);
        if (objective.isPresent())
        {
            ScoreboardHudRenderer.Bounds bounds = ScoreboardHudRenderer.render(context, client, objective.get());
            MmmUi.drawBorder(context, bounds.left() - 2, bounds.top() - 2,
                    bounds.width() + 4, bounds.height() + 4, MmmUi.accent());
        }
        else
        {
            int messageWidth = this.font.width("No sidebar scoreboard is visible.");
            context.text(this.font, Component.literal("No sidebar scoreboard is visible."),
                    Math.max(8, (this.width - messageWidth) / 2), Math.max(96, this.height / 2), MmmUi.MUTED, false);
        }

        this.drawControls(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (super.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
        if (button != 0)
        {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        Optional<Objective> objective = ScoreboardService.getSidebarObjective(client);
        if (objective.isEmpty())
        {
            return false;
        }
        ScoreboardHudRenderer.Bounds bounds = ScoreboardHudRenderer.getBounds(client, objective.get());
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
            Minecraft client = Minecraft.getInstance();
            ScoreboardService.getSidebarObjective(client).ifPresent(objective -> ScoreboardHudRenderer.setPosition(
                    client, objective, (int) mouseX - this.dragOffsetX, (int) mouseY - this.dragOffsetY));
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
    public void onClose()
    {
        Configs.saveToFile();
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen()
    {
        return MmmUi.shouldPauseGame();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
    {
    }

    private void drawControls(GuiGraphicsExtractor context)
    {
        ControlLayout layout = this.controlLayout();
        MmmUi.card(context, layout.x(), layout.y(), layout.width(), layout.height(), MmmUi.PANEL, MmmUi.BORDER);
        MmmUi.drawSectionHeading(context, this.font, "MOVE SCOREBOARD",
                layout.x() + 12, layout.y() + 12, layout.width() - 24);
        MmmUi.drawTextWithin(context, this.font, "Drag the live sidebar. Scroll or +/- to resize it.",
                layout.x() + 12, layout.y() + 28, layout.width() - 24, MmmUi.MUTED, false);
        String size = "Size: " + Math.round(Configs.Generic.SCOREBOARD_SCALE.getDoubleValue() * 100.0D) + "%";
        MmmUi.drawTextWithin(context, this.font, size, layout.x() + 70,
                layout.y() + layout.height() - 22, 90, MmmUi.accent(), false);
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
        int panelWidth = 294;
        int panelHeight = 80;
        return new ControlLayout(12, 12, panelWidth, panelHeight);
    }

    private record ControlLayout(int x, int y, int width, int height)
    {
    }
}
