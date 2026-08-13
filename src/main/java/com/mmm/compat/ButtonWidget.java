package com.mmm.compat;

import net.minecraft.text.Text;

public class ButtonWidget extends net.minecraft.client.gui.widget.ButtonWidget
{
    public ButtonWidget(int x, int y, int width, int height, Text message, PressAction action)
    {
        super(x, y, width, height, message, action);
    }

    public static Builder builder(Text message, PressAction action)
    {
        return new Builder(message, action);
    }

    public int getX()
    {
        return this.x;
    }

    public int getY()
    {
        return this.y;
    }

    public void setX(int x)
    {
        this.x = x;
    }

    public void setY(int y)
    {
        this.y = y;
    }

    public static final class Builder
    {
        private final Text message;
        private final PressAction action;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;

        private Builder(Text message, PressAction action)
        {
            this.message = message;
            this.action = action;
        }

        public Builder dimensions(int x, int y, int width, int height)
        {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public ButtonWidget build()
        {
            return new ButtonWidget(this.x, this.y, this.width, this.height, this.message, this.action);
        }
    }
}
