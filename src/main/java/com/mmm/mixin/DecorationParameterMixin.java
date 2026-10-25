package com.mmm.mixin;

import com.mmm.tags.TierTagManager;
import net.minecraft.network.message.MessageType;
import net.minecraft.text.Decoration;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Decoration.Parameter.class)
public abstract class DecorationParameterMixin
{
    @Inject(method = "apply", at = @At("RETURN"), cancellable = true)
    private void mmm$decorateChatSender(Text content, MessageType.Parameters parameters, CallbackInfoReturnable<Text> cir)
    {
        if ((Object) this != Decoration.Parameter.SENDER)
        {
            return;
        }
        MutableText decorated = TierTagManager.decorateDisplayedName(cir.getReturnValue());
        if (decorated != null)
        {
            cir.setReturnValue(decorated);
        }
    }
}
