package com.mmm.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Bridges typed SDL input to MMM's version-neutral screen callbacks. */
public abstract class CompatScreen extends Screen
{
    protected CompatScreen(Component title)
    {
        super(title);
    }

    protected boolean acceptsAutomaticSearchInput()
    {
        return false;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!acceptsAutomaticSearchInput())
        {
            minecraft.textInputManager().stopTextInput(this);
            return;
        }
        for (var child : children())
        {
            if (child instanceof EditBox field && field.isFocused()) return;
        }
        // SDL only emits character events while text input is active.
        minecraft.textInputManager().startTextInput(this);
    }

    @Override
    public void removed()
    {
        minecraft.textInputManager().stopTextInput(this);
        super.removed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick)
    {
        return this.mouseClicked(click.x(), click.y(), legacyMouseButton(click.button())) || super.mouseClicked(click, doubleClick);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY)
    {
        return this.mouseDragged(click.x(), click.y(), legacyMouseButton(click.button()), deltaX, deltaY)
                || super.mouseDragged(click, deltaX, deltaY);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click)
    {
        return this.mouseReleased(click.x(), click.y(), legacyMouseButton(click.button())) || super.mouseReleased(click);
    }

    static int legacyMouseButton(int button)
    {
        return switch (button)
        {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> button - 1;
        };
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent input)
    {
        return this.keyPressed(input.key(), input.keycode(), input.modifiers()) || super.keyPressed(input);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent input)
    {
        return this.keyReleased(input.key(), input.keycode(), input.modifiers()) || super.keyReleased(input);
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
