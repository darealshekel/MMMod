package com.mmm.mixin;

import com.mmm.hotkey.MmmHotkeyManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin
{
    @Inject(method = "keyPress", at = @At("TAIL"))
    private void mmm$handleHotkeyChord(long window,
                                       int action,
                                       KeyEvent input,
                                       CallbackInfo ci)
    {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getWindow().handle() == window)
        {
            MmmHotkeyManager.onKeyEvent(client, input.key(), input.scancode(), action);
        }
    }
}