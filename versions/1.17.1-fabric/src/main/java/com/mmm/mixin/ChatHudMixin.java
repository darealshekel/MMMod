package com.mmm.mixin;

import com.mmm.tags.TierTagManager;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin
{
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true)
    private Text mmm$decorateChatSender(Text message)
    {
        Text decorated = TierTagManager.decorateChatLine(message);
        return decorated == null ? message : decorated;
    }
}
