package com.mmm.ui;

import com.mmm.config.Configs;
import com.mmm.config.value.ConfigColor;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class MmmColorEditorScreen extends Screen
{
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 218;
    private final Screen parent;
    private final ConfigColor config;
    private final Runnable onClose;
    private TextFieldWidget hexField;
    private int panelX;
    private int panelY;
    private float hue;
    private float saturation;
    private float brightness;
    private int alpha;
    private DragTarget dragTarget = DragTarget.NONE;
    private boolean syncingField;

    public MmmColorEditorScreen(Screen parent, ConfigColor config, Runnable onClose)
    {
        super(Text.literal("Color Picker"));
        this.parent = parent;
        this.config = config;
        this.onClose = onClose == null ? () -> {} : onClose;
        loadColor(config.getStringValue());
    }

    @Override
    protected void init()
    {
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;
        this.hexField = new TextFieldWidget(this.textRenderer, this.panelX + 18, this.panelY + 166, 150, 20, Text.literal("Hex color"));
        this.hexField.setMaxLength(9);
        this.hexField.setText(formatColor());
        this.hexField.setChangedListener(this::onHexChanged);
        this.addDrawableChild(this.hexField);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("DONE"), button -> close())
                .dimensions(this.panelX + 176, this.panelY + 166, 66, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        context.fill(0, 0, this.width, this.height, 0xB8000000);
        context.fill(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + PANEL_HEIGHT, 0xFF090909);
        context.drawBorder(this.panelX, this.panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF2A2A2A);
        context.fill(this.panelX + 14, this.panelY + 14, this.panelX + 18, this.panelY + 28, MmmUi.accent());
        context.drawText(this.textRenderer, Text.literal("CUSTOM COLOR"), this.panelX + 26, this.panelY + 17, 0xFFF5F5F5, false);

        int colorX = this.panelX + 18;
        int colorY = this.panelY + 40;
        int colorW = 188;
        int colorH = 112;
        for (int row = 0; row < 28; row++)
        {
            float value = 1.0F - row / 27.0F;
            int y1 = colorY + row * colorH / 28;
            int y2 = colorY + (row + 1) * colorH / 28;
            for (int column = 0; column < 47; column++)
            {
                float sat = column / 46.0F;
                int x1 = colorX + column * colorW / 47;
                int x2 = colorX + (column + 1) * colorW / 47;
                context.fill(x1, y1, x2, y2, 0xFF000000 | Color.HSBtoRGB(this.hue, sat, value));
            }
        }
        context.drawBorder(colorX, colorY, colorW, colorH, 0xFF343434);
        int markerX = colorX + Math.round(this.saturation * (colorW - 1));
        int markerY = colorY + Math.round((1.0F - this.brightness) * (colorH - 1));
        context.drawBorder(markerX - 2, markerY - 2, 5, 5, 0xFFFFFFFF);

        int hueX = this.panelX + 218;
        for (int row = 0; row < 28; row++)
        {
            int y1 = colorY + row * colorH / 28;
            int y2 = colorY + (row + 1) * colorH / 28;
            context.fill(hueX, y1, hueX + 24, y2, 0xFF000000 | Color.HSBtoRGB(row / 27.0F, 1.0F, 1.0F));
        }
        context.drawBorder(hueX, colorY, 24, colorH, 0xFF343434);
        int hueMarkerY = colorY + Math.round(this.hue * (colorH - 1));
        context.fill(hueX - 2, hueMarkerY - 1, hueX + 26, hueMarkerY + 2, 0xFFFFFFFF);

        int preview = currentArgb();
        context.fill(this.panelX + 18, this.panelY + 194, this.panelX + 242, this.panelY + 204, preview);
        context.drawBorder(this.panelX + 18, this.panelY + 194, 224, 10, 0xFF343434);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && !inside(mouseX, mouseY, this.panelX, this.panelY, PANEL_WIDTH, PANEL_HEIGHT))
        {
            close();
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, this.panelX + 18, this.panelY + 40, 188, 112))
        {
            this.dragTarget = DragTarget.COLOR;
            updateFromMouse(mouseX, mouseY);
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, this.panelX + 218, this.panelY + 40, 24, 112))
        {
            this.dragTarget = DragTarget.HUE;
            updateFromMouse(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        if (button == 0 && this.dragTarget != DragTarget.NONE)
        {
            updateFromMouse(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        this.dragTarget = DragTarget.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close()
    {
        applyColor();
        this.onClose.run();
        if (this.client != null)
        {
            this.client.setScreen(this.parent);
        }
    }

    private void updateFromMouse(double mouseX, double mouseY)
    {
        if (this.dragTarget == DragTarget.COLOR)
        {
            this.saturation = clamp((float) ((mouseX - (this.panelX + 18)) / 187.0D));
            this.brightness = 1.0F - clamp((float) ((mouseY - (this.panelY + 40)) / 111.0D));
        }
        else if (this.dragTarget == DragTarget.HUE)
        {
            this.hue = clamp((float) ((mouseY - (this.panelY + 40)) / 111.0D));
        }
        applyColor();
    }

    private void applyColor()
    {
        this.config.setValueFromString(formatColor());
        Configs.saveToFile();
        if (this.hexField != null && !this.hexField.getText().equalsIgnoreCase(formatColor()))
        {
            this.syncingField = true;
            this.hexField.setText(formatColor());
            this.syncingField = false;
        }
    }

    private void onHexChanged(String value)
    {
        if (this.syncingField || value == null || !value.matches("#?[0-9a-fA-F]{6,8}"))
        {
            return;
        }
        loadColor(value);
        this.config.setValueFromString(formatColor());
        Configs.saveToFile();
    }

    private void loadColor(String value)
    {
        String hex = value == null ? "" : value.trim().replace("#", "");
        try
        {
            long parsed = Long.parseLong(hex, 16);
            int argb = hex.length() == 8 ? (int) parsed : 0xFF000000 | (int) parsed;
            this.alpha = (argb >>> 24) & 0xFF;
            float[] hsb = Color.RGBtoHSB((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, null);
            this.hue = hsb[0];
            this.saturation = hsb[1];
            this.brightness = hsb[2];
        }
        catch (RuntimeException ignored)
        {
            this.alpha = 0xFF;
            this.hue = 0.0F;
            this.saturation = 1.0F;
            this.brightness = 0.88F;
        }
    }

    private int currentArgb()
    {
        return (this.alpha << 24) | (Color.HSBtoRGB(this.hue, this.saturation, this.brightness) & 0x00FFFFFF);
    }

    private String formatColor()
    {
        return String.format("#%08X", currentArgb());
    }

    private static float clamp(float value)
    {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height)
    {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private enum DragTarget
    {
        NONE, COLOR, HUE
    }
}