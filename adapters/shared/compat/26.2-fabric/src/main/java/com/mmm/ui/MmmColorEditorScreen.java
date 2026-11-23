package com.mmm.ui;

import static com.mmm.ui.BoardEditorStyle.*;

import com.mmm.config.Configs;
import com.mmm.config.value.ConfigColor;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class MmmColorEditorScreen extends CompatScreen
{
    private static final int MAX_WIDTH = 352;
    private static final int HUE_WIDTH = 20;
    private final Screen parent;
    private final ConfigColor config;
    private final Runnable onClose;
    private EditBox hexField;
    private Button back;
    private int left, top, sheetWidth, sheetHeight, planeX, planeY, planeWidth, planeHeight, hueX;
    private float hue;
    private float saturation;
    private float brightness;
    private int alpha;
    private DragTarget dragTarget = DragTarget.NONE;
    private boolean syncingField;

    public MmmColorEditorScreen(Screen parent, ConfigColor config, Runnable onClose)
    {
        super(Component.literal("Color Picker"));
        this.parent = parent;
        this.config = config;
        this.onClose = onClose == null ? () -> {} : onClose;
        loadColor(config.getStringValue());
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        dragTarget = DragTarget.NONE;
        sheetWidth = contentWidth(width, MAX_WIDTH);
        left = contentX(width, MAX_WIDTH);
        sheetHeight = Math.max(1, Math.min(210, height - HEADER_HEIGHT - 8));
        top = HEADER_HEIGHT + (height - HEADER_HEIGHT - sheetHeight) / 2;
        planeX = left + 8;
        planeY = top + 28;
        planeWidth = Math.max(2, sheetWidth - HUE_WIDTH - 25);
        planeHeight = Math.max(2, sheetHeight - 60);
        hueX = left + sheetWidth - 8 - HUE_WIDTH;

        // Keep partially typed hex values on resize, without treating them as edits.
        String draft = hexField == null ? formatColor() : hexField.getValue();
        hexField = new EditBox(font, left + 65, top + sheetHeight - 20,
                Math.max(1, sheetWidth - 79), 14, Component.literal("Hex color"));
        hexField.setBordered(false);
        hexField.setMaxLength(9);
        hexField.setTextColor(TEXT);
        hexField.setValue(draft);
        hexField.setResponder(this::onHexChanged);
        addRenderableWidget(hexField);
        back = addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(left + sheetWidth - 48, (HEADER_HEIGHT - 18) / 2, 48, 18).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta)
    {
        c.fill(0, 0, width, height, FLOOR);
        BoardMenuChrome.header(c, font, width, "Color picker");

        c.fill(left, top, left + sheetWidth, top + sheetHeight, SURFACE);
        c.fill(left, top, left + 20, top + 2, ROSE);
        MmmUi.drawTextWithin(c, font, config.getPrettyName(), planeX, top + 10, planeWidth, TEXT, false);
        c.centeredText(font, "Hue", hueX + HUE_WIDTH / 2, top + 10, MUTED);

        // One strip per GUI pixel: saturation changes across X, while each strip fades
        // continuously to black across Y. No scaled bitmap or coarse tile boundaries.
        for (int x = 0; x < planeWidth; x++) {
            int color = Color.HSBtoRGB(hue, x / (float)(planeWidth - 1), 1f);
            c.fillGradient(planeX + x, planeY, planeX + x + 1, planeY + planeHeight, color, 0xff000000);
        }
        c.outline(planeX - 1, planeY - 1, planeWidth + 2, planeHeight + 2, LINE);
        for (int band = 0; band < 6; band++) {
            c.fillGradient(hueX, planeY + band * planeHeight / 6, hueX + HUE_WIDTH, planeY + (band + 1) * planeHeight / 6,
                    Color.HSBtoRGB(band / 6f, 1, 1), Color.HSBtoRGB((band + 1) / 6f, 1, 1));
        }
        c.outline(hueX - 1, planeY - 1, HUE_WIDTH + 2, planeHeight + 2, LINE);
        int markerX = planeX + Math.round(saturation * (planeWidth - 1));
        int markerY = planeY + Math.round((1 - brightness) * (planeHeight - 1));
        c.outline(markerX - 3, markerY - 3, 7, 7, FLOOR);
        c.outline(markerX - 2, markerY - 2, 5, 5, 0xffffffff);
        int hueY = planeY + Math.round(hue * (planeHeight - 1));
        c.outline(hueX - 2, hueY - 2, HUE_WIDTH + 4, 5, FLOOR);
        c.outline(hueX - 1, hueY - 1, HUE_WIDTH + 2, 3, 0xffffffff);

        int fieldY = top + sheetHeight - 25;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
            c.fill(planeX + 4*x, fieldY + 1 + 4*y, planeX + 4*x + 4, fieldY + 5 + 4*y,
                    (x + y) % 2 == 0 ? 0xffb0b0b0 : 0xff555555);
        }
        c.fill(planeX, fieldY + 1, planeX + 16, fieldY + 17, currentArgb());
        c.outline(planeX - 1, fieldY, 18, 18, LINE);
        c.text(font, "Hex", planeX + 24, fieldY + 5, MUTED, false);
        int fieldX = hexField.getX() - 5, fieldWidth = hexField.getWidth() + 10;
        c.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + 18, FLOOR);
        c.outline(fieldX, fieldY, fieldWidth, 18, hexField.isFocused() ? ROSE : LINE);
        super.extractRenderState(c, mx, my, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor c, int mx, int my, float delta) {}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && !back.isMouseOver(mouseX, mouseY) && !inside(mouseX, mouseY, left, top, sheetWidth, sheetHeight))
        {
            onClose();
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, planeX, planeY, planeWidth, planeHeight))
        {
            this.dragTarget = DragTarget.COLOR;
            updateFromMouse(mouseX, mouseY);
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, hueX, planeY, HUE_WIDTH, planeHeight))
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
    public void onClose()
    {
        applyColor();
        this.onClose.run();
        if (this.minecraft != null)
        {
            BoardPlatform.open(this.parent);
        }
    }

    private void updateFromMouse(double mouseX, double mouseY)
    {
        if (this.dragTarget == DragTarget.COLOR)
        {
            this.saturation = clamp((float) ((mouseX - planeX) / (planeWidth - 1)));
            this.brightness = 1.0F - clamp((float) ((mouseY - planeY) / (planeHeight - 1)));
        }
        else if (this.dragTarget == DragTarget.HUE)
        {
            this.hue = clamp((float) ((mouseY - planeY) / (planeHeight - 1)));
        }
        applyColor();
    }

    private void applyColor()
    {
        this.config.setValueFromString(formatColor());
        Configs.saveToFile();
        if (this.hexField != null && !this.hexField.getValue().equalsIgnoreCase(formatColor()))
        {
            this.syncingField = true;
            this.hexField.setValue(formatColor());
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
