package com.mmm.compat;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public class TextFieldWidget extends net.minecraft.client.gui.widget.TextFieldWidget
{
    private Text placeholder;

    public TextFieldWidget(TextRenderer renderer, int x, int y, int width, int height, Text message)
    {
        super(renderer, x, y, width, height, message);
    }

    public int getX()
    {
        return this.x;
    }

    public int getY()
    {
        return this.y;
    }

    public void setY(int y)
    {
        this.y = y;
    }

    public void setPlaceholder(Text placeholder)
    {
        this.placeholder = placeholder;
    }

    public void setFocused(boolean focused)
    {
        setTextFieldFocused(focused);
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta)
    {
        super.renderButton(matrices, mouseX, mouseY, delta);
        if (this.placeholder != null && getText().isEmpty() && !isFocused())
        {
            MinecraftText.drawPlaceholder(matrices, this.placeholder, this.x + 4, this.y + 6);
        }
    }
}
