package com.mmm.ui;

import com.mmm.config.Configs;
import com.mmm.config.value.ConfigColor;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class MmmColorEditorScreen extends com.mmm.ui.CompatScreen
{
    private final Screen parent;
    private final ConfigColor config;
    private final Runnable onClose;
    private TextFieldWidget hexField;
    private int fieldX, fieldY, fieldWidth, fieldHeight;
    private int hueX() { return fieldX + fieldWidth + 9; }
    private int controlsY() { return fieldY + fieldHeight + 9; }
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
        fieldX = BoardEditorStyle.contentX(width, 292);
        fieldWidth = BoardEditorStyle.contentWidth(width, 292) - 31;
        fieldY = BoardEditorStyle.HEADER_HEIGHT + 18;
        fieldHeight = Math.max(12, Math.min(143, height - fieldY - 50));
        this.hexField = new TextFieldWidget(textRenderer, fieldX, controlsY(), fieldWidth - 44, 20, Text.literal("Hex color"));
        this.hexField.setMaxLength(9);
        this.hexField.setText(formatColor());
        this.hexField.setChangedListener(this::onHexChanged);
        this.addDrawableChild(this.hexField);
        this.addDrawableChild(MmmUi.backButton(this, hueX() + 22));
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // The game calls this before render(); its default implementation blurs the world.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.backdrop(context, width, height);
        MmmUi.boardHeader(context, textRenderer, width, fieldX, "Color picker");
        MmmUi.drawTextWithin(context, textRenderer, config.getPrettyName(), fieldX, fieldY - 13,
                fieldWidth, BoardEditorStyle.MUTED, false);

        // White-to-hue horizontally, then transparent-to-black vertically: HSV at raster resolution.
        var matrix = context.getMatrices();
        matrix.pushMatrix();
        matrix.translate(fieldX + fieldWidth, fieldY);
        matrix.rotate((float) (Math.PI / 2));
        context.fillGradient(0, 0, fieldHeight, fieldWidth, Color.HSBtoRGB(hue, 1, 1), 0xffffffff);
        matrix.popMatrix();
        context.fillGradient(fieldX, fieldY, fieldX + fieldWidth, fieldY + fieldHeight, 0x00000000, 0xff000000);
        for (int i = 0; i < 6; i++) {
            context.fillGradient(hueX(), fieldY + fieldHeight * i / 6, hueX() + 22, fieldY + fieldHeight * (i+1) / 6,
                    Color.HSBtoRGB(i / 6f, 1, 1), Color.HSBtoRGB((i+1) / 6f, 1, 1));
        }
        int mx = fieldX + Math.round(saturation * (fieldWidth - 1));
        int my = fieldY + Math.round((1 - brightness) * (fieldHeight - 1));
        MmmUi.drawBorder(context, mx - 3, my - 3, 7, 7, 0xff000000);
        MmmUi.drawBorder(context, mx - 2, my - 2, 5, 5, 0xffffffff);
        int hy = fieldY + Math.round(hue * (fieldHeight - 1));
        MmmUi.drawBorder(context, hueX()-2, hy-2, 26, 5, 0xff000000);
        MmmUi.drawBorder(context, hueX()-1, hy-1, 24, 3, 0xffffffff);
        int px = fieldX + fieldWidth - 36;
        context.fill(px, controlsY(), hueX()+22, controlsY()+20, BoardEditorStyle.TILE);
        context.fill(px+2, controlsY()+2, hueX()+20, controlsY()+18, currentArgb());
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && (mouseX < fieldX - 6 || mouseX >= hueX() + 28 || mouseY >= controlsY() + 27)) {
            close();
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, fieldX, fieldY, fieldWidth, fieldHeight))
        {
            this.dragTarget = DragTarget.COLOR;
            updateFromMouse(mouseX, mouseY);
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, hueX(), fieldY, 22, fieldHeight))
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
            this.saturation = clamp((float) ((mouseX - fieldX) / (fieldWidth - 1)));
            this.brightness = 1.0F - clamp((float) ((mouseY - fieldY) / (fieldHeight - 1)));
        }
        else if (this.dragTarget == DragTarget.HUE)
        {
            this.hue = clamp((float) ((mouseY - fieldY) / (fieldHeight - 1)));
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
