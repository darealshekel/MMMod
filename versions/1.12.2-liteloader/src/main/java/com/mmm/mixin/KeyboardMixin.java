package com.mmm.mixin;

import com.mmm.hotkey.MmmHotkeyManager;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class KeyboardMixin
{
    // Run after an already-open screen has received the event. Opening the MMM
    // screen here must not also type the final chord key into its search field.
    @Inject(method = "handleKeyboardEvents", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/input/Keyboard;getEventKeyState()Z"))
    private void mmm$handleHotkeyChord(CallbackInfo ci)
    {
        int action = !Keyboard.getEventKeyState() ? 0 : Keyboard.isRepeatEvent() ? 2 : 1;
        MmmHotkeyManager.onKeyEvent((Minecraft) (Object) this, Keyboard.getEventKey(), 0, action);
    }
}
