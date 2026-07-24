package com.mmm.mixin;

import com.mmm.config.Configs;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Let the standalone Scoreboard Helper own its equivalent redirect when both mods are installed.
@Mixin(value = ChatScreen.class, priority = 900)
public abstract class ScoreboardChatScreenMixin
{
    @Redirect(
            method = "sendMessage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendChatMessage(Ljava/lang/String;)V"),
            require = 0)
    private void mmm$routeDefaultTeamChat(ClientPlayNetworkHandler networkHandler, String message)
    {
        if (!Configs.Generic.SCOREBOARD_DEFAULT_TEAM_CHAT.getBooleanValue())
        {
            networkHandler.sendChatMessage(message);
            return;
        }
        if (message.startsWith("#"))
        {
            networkHandler.sendChatMessage(message.length() == 1 ? message : message.substring(1));
            return;
        }
        networkHandler.sendChatCommand("teammsg " + message);
    }
}
