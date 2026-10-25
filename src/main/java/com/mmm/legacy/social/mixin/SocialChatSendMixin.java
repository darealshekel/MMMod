package com.mmm.legacy.social.mixin;

import com.mmm.legacy.social.ChatChannelAccess;
import com.mmm.legacy.command.LegacyCommands;
import com.mmm.social.PublicChatClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class SocialChatSendMixin {
    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void mmm$route(String message, boolean history, CallbackInfo ci) {
        if (LegacyCommands.execute(message)) {
            ci.cancel();
            return;
        }
        if ((Object) this instanceof ChatChannelAccess && ((ChatChannelAccess) this).mmm$isPublicChannel()) {
            ci.cancel();
            PublicChatClient.sendMessage(message);
        }
    }
}
