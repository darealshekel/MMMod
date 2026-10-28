package com.mmm.legacy.social.mixin;

import com.mmm.legacy.social.LegacySocialHooks;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatGui.class)
public abstract class SocialChatTagMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;I)V", at = @At("HEAD"), argsOnly = true)
    private Text mmm$sender(Text message) { return LegacySocialHooks.decorateChat(message); }
}
