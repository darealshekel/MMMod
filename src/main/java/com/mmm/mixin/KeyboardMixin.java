package com.mmm.mixin;

import com.mmm.hotkey.MmmHotkeyManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin
{
    @Inject(method = "onKey", at = @At("TAIL"))
    private void mmm$handleHotkeyChord(long window,
                                       int action,
                                       KeyInput input,
                                       CallbackInfo ci)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getWindow().getHandle() == window)
        {
            MmmHotkeyManager.onKeyEvent(client, input.key(), input.scancode(), action);
        }
    }
}