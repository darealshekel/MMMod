package com.mmm.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Bridges the typed 1.21.11 input API to MMM's version-neutral screen callbacks. */
public abstract class CompatScreen extends Screen
{
    protected CompatScreen(Component title)
    {
        super(title);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick)
    {
        return this.mouseClicked(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubleClick);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY)
    {
        return this.mouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)
                || super.mouseDragged(click, deltaX, deltaY);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click)
    {
        return this.mouseReleased(click.x(), click.y(), click.button()) || super.mouseReleased(click);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent input)
    {
        return this.keyPressed(input.key(), input.scancode(), input.modifiers()) || super.keyPressed(input);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent input)
    {
        return this.keyReleased(input.key(), input.scancode(), input.modifiers()) || super.keyReleased(input);
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers)
    {
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent input)
    {
        int codepoint = input.codepoint();
        boolean handled = codepoint <= Character.MAX_VALUE
                && this.charTyped((char)codepoint, 0);
        return handled || super.charTyped(input);
    }

    public boolean charTyped(char chr, int modifiers)
    {
        return false;
    }
}
