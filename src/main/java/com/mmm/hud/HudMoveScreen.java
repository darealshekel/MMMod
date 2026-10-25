package com.mmm.hud;

import com.mmm.ui.CompatScreen;

import com.mmm.config.Configs;
import com.mmm.config.Configs.HudAlignment;
import com.mmm.timer.MmmTimerState;
import com.mmm.timer.TimerHudRenderer;
import com.mmm.ui.MmmUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HudMoveScreen extends CompatScreen
{
    private final Screen parent;
    private HudModuleId selected = HudModuleId.MAIN;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public HudMoveScreen(Screen parent)
    {
        super(Component.literal("HUD"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        ControlLayout layout = this.controlLayout();
        this.addRenderableWidget(Button.builder(Component.literal("-"), button -> adjustScale(-0.05D)).bounds(layout.x() + 12, layout.y() + layout.height() - 28, 22, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), button -> adjustScale(0.05D)).bounds(layout.x() + 38, layout.y() + layout.height() - 28, 22, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            TimerHudRenderer.resetLayout();
            Configs.Generic.HUD_X.resetToDefault();
            Configs.Generic.HUD_Y.resetToDefault();
            Configs.Generic.HUD_SCALE.resetToDefault();
            Configs.saveToFile();
        }).bounds(layout.x() + layout.width() - 150, layout.y() + layout.height() - 28, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds(layout.x() + layout.width() - 80, layout.y() + layout.height() - 28, 64, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        context.fill(0, 0, this.width, this.height, 0x22050505);

        Minecraft client = Minecraft.getInstance();
        if (client != null)
        {
            MiningHudRenderer.render(context, client);
            for (HudModuleId module : movableModules())
            {
                if (TimerHudRenderer.isVisible(module) || module == this.selected)
                {
                    TimerHudRenderer.drawModule(context, client, module, true);
                }
            }
            drawModuleBounds(context, client);
        }

        drawControls(context, mouseX, mouseY);
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

        ControlLayout layout = this.controlLayout();
        int rowY = layout.y() + 48;
        for (HudModuleId module : movableModules())
        {
            if (mouseX >= layout.x() + 12 && mouseX < layout.x() + layout.width() - 12 && mouseY >= rowY && mouseY < rowY + 22)
            {
                if (mouseX >= layout.x() + layout.width() - 58 && module != HudModuleId.MAIN)
                {
                    TimerHudRenderer.toggleVisible(module);
                    return true;
                }
                this.selected = module;
                return true;
            }
            rowY += 25;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null)
        {
            return false;
        }

        for (HudModuleId module : movableModules())
        {
            int[] bounds = TimerHudRenderer.getBounds(client, module);
            if (mouseX >= bounds[0] && mouseX <= bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[3])
            {
                this.selected = module;
                this.dragging = true;
                this.dragOffsetX = (int) mouseX - bounds[0];
                this.dragOffsetY = (int) mouseY - bounds[1];
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        if (this.dragging)
        {
            Minecraft client = Minecraft.getInstance();
            if (client != null)
            {
                int actualX = (int) mouseX - this.dragOffsetX;
                int actualY = (int) mouseY - this.dragOffsetY;
                if (this.selected == HudModuleId.MAIN)
                {
                    setMainHudPosition(client, actualX, actualY);
                }
                else
                {
                    TimerHudRenderer.setModulePosition(client, this.selected, actualX, actualY);
                }
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (button == 0)
        {
            this.dragging = false;
            Configs.saveToFile();
            MmmTimerState.save();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        adjustScale(verticalAmount > 0 ? 0.05D : -0.05D);
        return true;
    }

    @Override
    public void onClose()
    {
        Configs.saveToFile();
        MmmTimerState.save();
        Minecraft.getInstance().setScreen(this.parent);
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

    private void drawControls(GuiGraphicsExtractor context, int mouseX, int mouseY)
    {
        ControlLayout layout = this.controlLayout();
        MmmUi.card(context, layout.x(), layout.y(), layout.width(), layout.height(), MmmUi.PANEL, MmmUi.BORDER);
        MmmUi.drawSectionHeading(context, this.font, "MOVE HUD", layout.x() + 12, layout.y() + 12, layout.width() - 24);
        MmmUi.drawTextWithin(context, this.font, "Drag modules. Scroll or +/- to resize selected.", layout.x() + 12, layout.y() + 28, layout.width() - 24, MmmUi.MUTED, false);

        int rowY = layout.y() + 48;
        for (HudModuleId module : movableModules())
        {
            boolean active = module == this.selected;
            int rowFill = active ? MmmUi.accentSoft() : MmmUi.INSET;
            int border = active ? MmmUi.accent() : MmmUi.BORDER_SOFT;
            context.fill(layout.x() + 12, rowY, layout.x() + layout.width() - 12, rowY + 20, rowFill);
            MmmUi.drawBorder(context, layout.x() + 12, rowY, layout.width() - 24, 20, border);
            MmmUi.drawTextWithin(context, this.font, module.label(), layout.x() + 20, rowY + 6, layout.width() - 94, active ? MmmUi.TEXT : MmmUi.MUTED, false);
            if (module != HudModuleId.MAIN)
            {
                String visibility = TimerHudRenderer.isVisible(module) ? "ON" : "OFF";
                int toggleX = layout.x() + layout.width() - 58;
                MmmUi.drawBorder(context, toggleX, rowY + 3, 38, 14, TimerHudRenderer.isVisible(module) ? MmmUi.accent() : MmmUi.BORDER_SOFT);
                MmmUi.drawTextWithin(context, this.font, visibility, toggleX + 8, rowY + 6, 26, TimerHudRenderer.isVisible(module) ? MmmUi.TEXT : MmmUi.MUTED, false);
            }
            rowY += 25;
        }

        String size = "Size: " + Math.round(getSelectedScale() * 100D) + "%";
        MmmUi.drawTextWithin(context, this.font, size, layout.x() + 70, layout.y() + layout.height() - 22, 90, MmmUi.accent(), false);
    }

    private void drawModuleBounds(GuiGraphicsExtractor context, Minecraft client)
    {
        for (HudModuleId module : movableModules())
        {
            if (module != HudModuleId.MAIN && TimerHudRenderer.isVisible(module) == false && module != this.selected)
            {
                continue;
            }
            int[] bounds = TimerHudRenderer.getBounds(client, module);
            int color = module == this.selected ? MmmUi.accent() : MmmUi.accentSoft();
            MmmUi.drawBorder(context, bounds[0] - 2, bounds[1] - 2, bounds[2] - bounds[0] + 4, bounds[3] - bounds[1] + 4, color);
        }
    }

    private void adjustScale(double delta)
    {
        if (this.selected == HudModuleId.MAIN)
        {
            Configs.Generic.HUD_SCALE.setDoubleValue(Math.max(0.25D, Math.min(1.75D, Configs.Generic.HUD_SCALE.getDoubleValue() + delta)));
            Configs.saveToFile();
            return;
        }
        TimerHudRenderer.adjustScale(this.selected, delta);
    }

    private double getSelectedScale()
    {
        return switch (this.selected)
        {
            case MAIN -> Configs.Generic.HUD_SCALE.getDoubleValue();
            case TIMER -> Configs.Generic.TIMER_HUD_SCALE.getDoubleValue();
            case HOURLY -> Configs.Generic.HOURLY_STATS_SCALE.getDoubleValue();
            case BLOCK_STATS -> Configs.Generic.BLOCK_STATS_SCALE.getDoubleValue();
            case NOTIFICATION -> Configs.Generic.TIMER_NOTIFICATION_SCALE.getDoubleValue();
        };
    }

    private void setMainHudPosition(Minecraft client, int actualX, int actualY)
    {
        int[] bounds = MiningHudRenderer.getBounds(client);
        int width = bounds[2] - bounds[0];
        int height = bounds[3] - bounds[1];
        int maxX = Math.max(1, client.getWindow().getGuiScaledWidth() - width);
        int maxY = Math.max(1, client.getWindow().getGuiScaledHeight() - height);
        int clampedX = Math.max(0, Math.min(maxX, actualX));
        int clampedY = Math.max(0, Math.min(maxY, actualY));
        HudAlignment alignment = (HudAlignment) Configs.Generic.HUD_ALIGNMENT.getOptionListValue();

        int storedX = switch (alignment)
        {
            case TOP_RIGHT, BOTTOM_RIGHT -> (int) Math.round((1.0D - (clampedX / (double) maxX)) * 820.0D);
            default -> (int) Math.round((clampedX / (double) maxX) * 820.0D);
        };
        int storedY = switch (alignment)
        {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> (int) Math.round((1.0D - (clampedY / (double) maxY)) * 460.0D);
            default -> (int) Math.round((clampedY / (double) maxY) * 460.0D);
        };
        Configs.Generic.HUD_X.setIntegerValue(Math.max(0, Math.min(820, storedX)));
        Configs.Generic.HUD_Y.setIntegerValue(Math.max(0, Math.min(460, storedY)));
    }

    private ControlLayout controlLayout()
    {
        int panelW = 284;
        int panelH = 210;
        int panelX = Math.max(12, this.width - panelW - 12);
        int panelY = 12;
        return new ControlLayout(panelX, panelY, panelW, panelH);
    }

    private record ControlLayout(int x, int y, int width, int height) {}

    private static HudModuleId[] movableModules()
    {
        return new HudModuleId[] { HudModuleId.MAIN, HudModuleId.TIMER, HudModuleId.BLOCK_STATS };
    }
}
