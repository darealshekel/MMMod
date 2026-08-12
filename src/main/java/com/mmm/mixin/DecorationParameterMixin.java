package com.mmm.mixin;

import com.mmm.tags.TierTagManager;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ChatTypeDecoration;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatTypeDecoration.Parameter.class)
public abstract class DecorationParameterMixin
{
    @Inject(method = "select", at = @At("RETURN"), cancellable = true)
    private void mmm$decorateChatSender(Component content, ChatType.Bound parameters, CallbackInfoReturnable<Component> cir)
    {
        if ((Object) this != ChatTypeDecoration.Parameter.SENDER)
        {
            return;
        }
        MutableComponent decorated = TierTagManager.decorateDisplayedName(cir.getReturnValue());
        if (decorated != null)
        {
            cir.setReturnValue(decorated);
        }
    }
}
