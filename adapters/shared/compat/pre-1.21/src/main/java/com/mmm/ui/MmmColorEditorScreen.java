package com.mmm.ui;

import com.mmm.config.Configs;
import com.mmm.config.value.ConfigColor;
import java.awt.Color;
import com.mmm.ui.BoardPlatform.Canvas;
import com.mmm.ui.BoardPlatform.BoardField;
import static com.mmm.ui.BoardEditorStyle.*;
import net.minecraft.client.gui.screen.Screen;

public final class MmmColorEditorScreen extends BoardPlatform.BoardScreen implements BoardChrome.Page
{
    private int left, top, formWidth, fieldHeight;
    private int fieldWidth() { return formWidth - 38; }
    private int hueX() { return left + formWidth - 20; }
    private int fieldY() { return top + 24; }
    private int inputY() { return fieldY() + fieldHeight + 12; }
    private final Screen parent;
    private final ConfigColor config;
    private final Runnable onClose;
    private BoardField hexField;
    private float hue;
    private float saturation;
    private float brightness;
    private int alpha;
    private DragTarget dragTarget = DragTarget.NONE;
    private boolean syncingField;

    public MmmColorEditorScreen(Screen parent, ConfigColor config, Runnable onClose)
    {
        super(BoardPlatform.literal("Color Picker"));
        this.parent = parent;
        this.config = config;
        this.onClose = onClose == null ? () -> {} : onClose;
        loadColor(config.getStringValue());
    }

    @Override
    protected void init()
    {
        dragTarget = DragTarget.NONE;
        String draft = hexField == null ? formatColor() : hexField.getText();
        formWidth = contentWidth(width, 268);
        left = contentX(width, 268);
        fieldHeight = Math.min(134, Math.max(8, height - HEADER_HEIGHT - 86));
        top = HEADER_HEIGHT + Math.max(4, (height - HEADER_HEIGHT - fieldHeight - 76) / 2);
        hexField = new BoardField(textRenderer, left, inputY(), formWidth - 62, 20, BoardPlatform.literal("Hex color"));
        hexField.setMaxLength(9);
        hexField.setText(draft);
        hexField.setChangedListener(this::onHexChanged);
        addDrawableChild(hexField);
        addDrawableChild(BoardChrome.control(left + formWidth - 56, inputY(), 56, 20, "Done", false, this::close));
    }

    @Override public void renderBackground(Canvas canvas) {
        canvas.raw().fill(0, 0, width, height, FLOOR);
    }

    @Override public void render(Canvas canvas, int mx, int my, float delta) {
        // Even if menu opacity is zero, editing a color must not sample the world behind it.
        renderBackground(canvas);
        BoardChrome.header(canvas, width, height, "Color picker");
        var c = canvas.raw();
        MmmUi.drawTextWithin(c, textRenderer, config.getPrettyName(), left, top + 5, formWidth - 48, TEXT, false);
        int chip = left + formWidth - 38;
        c.fill(chip - 1, top + 1, chip + 39, top + 16, LINE);
        c.fill(chip, top + 2, chip + 38, top + 15, currentArgb());
        int fy = fieldY(), fw = fieldWidth(), hx = hueX();
        c.drawBorder(left - 1, fy - 1, fw + 2, fieldHeight + 2, LINE);
        c.drawBorder(hx - 1, fy - 1, 22, fieldHeight + 2, LINE);
        PickerGradients.field(c.getMatrices(), left, fy, fw, fieldHeight, hue);
        PickerGradients.hue(c.getMatrices(), hx, fy, 20, fieldHeight);
        int sx = left + Math.round(saturation * (fw - 1)), sy = fy + Math.round((1 - brightness) * (fieldHeight - 1));
        c.drawBorder(sx - 3, sy - 3, 7, 7, FLOOR);
        c.drawBorder(sx - 2, sy - 2, 5, 5, 0xffffffff);
        int hy = fy + Math.round(hue * (fieldHeight - 1));
        c.fill(hx - 4, hy - 1, hx, hy + 2, 0xffffffff);
        c.fill(hx + 20, hy - 1, hx + 24, hy + 2, 0xffffffff);
        super.render(canvas, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && !inside(mouseX, mouseY, left - 6, top - 4, formWidth + 12, inputY() + 26 - top + 4))
        {
            close();
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, left, fieldY(), fieldWidth(), fieldHeight))
        {
            this.dragTarget = DragTarget.COLOR;
            updateFromMouse(mouseX, mouseY);
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, hueX(), fieldY(), 20, fieldHeight))
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
            this.saturation = clamp((float) ((mouseX - left) / (fieldWidth() - 1.0D)));
            this.brightness = 1.0F - clamp((float) ((mouseY - fieldY()) / (fieldHeight - 1.0D)));
        }
        else if (this.dragTarget == DragTarget.HUE)
        {
            this.hue = clamp((float) ((mouseY - fieldY()) / (fieldHeight - 1.0D)));
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
        if (this.syncingField || value == null || !value.matches("#?(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})"))
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
