package com.mmm.ui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public abstract class CompatScreen extends Screen
{
    protected CompatScreen(Text title)
    {
        super(title);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick)
    {
        return this.mouseClicked(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubleClick);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY)
    {
        return this.mouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY) || super.mouseDragged(click, deltaX, deltaY);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        return false;
    }

    @Override
    public boolean mouseReleased(Click click)
    {
        return this.mouseReleased(click.x(), click.y(), click.button()) || super.mouseReleased(click);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input)
    {
        return this.keyPressed(input.key(), input.scancode(), input.modifiers()) || super.keyPressed(input);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        return false;
    }
}
