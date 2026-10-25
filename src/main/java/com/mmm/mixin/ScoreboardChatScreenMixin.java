package com.mmm.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mmm.config.Configs;
import com.mmm.social.PublicChatClient;
import com.mmm.sync.WebsiteLinkManager;
import com.mmm.ui.MmmUi;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Let Scoreboard Helper own its equivalent redirect when both mods are installed.
@Mixin(value = ChatScreen.class, priority = 900)
public abstract class ScoreboardChatScreenMixin extends Screen
{
    private static final int TAB_HEIGHT = 14;
    private static final String MINECRAFT_CHANNEL_LABEL = "MINECRAFT";
    private static final String MMM_CHANNEL_LABEL = "MMM";
    private static boolean mmm$publicChannelSelected;

    @Shadow
    protected EditBox input;

    protected ScoreboardChatScreenMixin(Component title)
    {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mmm$initializeChannel(CallbackInfo ci)
    {
        if (this.input == null)
        {
            return;
        }

        if (this.input.getValue().isBlank() == false)
        {
            mmm$publicChannelSelected = false;
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void mmm$resetChannelOnClose(CallbackInfo ci)
    {
        mmm$publicChannelSelected = false;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mmm$renderChannelTabs(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci)
    {
        if (this.input == null)
        {
            return;
        }

        int x = this.input.getX();
        int y = Math.max(1, this.input.getY() - TAB_HEIGHT - 2);
        mmm$drawTab(context, false, MINECRAFT_CHANNEL_LABEL, x, y, mouseX, mouseY);
        x += mmm$tabWidth(MINECRAFT_CHANNEL_LABEL);
        mmm$drawTab(context, true, MMM_CHANNEL_LABEL, x, y, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mmm$selectChannel(MouseButtonEvent input, boolean doubleClick,
                                   CallbackInfoReturnable<Boolean> cir)
    {
        if (input.button() != InputConstants.MOUSE_BUTTON_LEFT || this.input == null)
        {
            return;
        }

        double mouseX = input.x();
        double mouseY = input.y();

        int x = this.input.getX();
        int y = Math.max(1, this.input.getY() - TAB_HEIGHT - 2);
        int minecraftWidth = mmm$tabWidth(MINECRAFT_CHANNEL_LABEL);
        if (mmm$isInside(mouseX, mouseY, x, y, minecraftWidth, TAB_HEIGHT))
        {
            mmm$switchChannel(false);
            cir.setReturnValue(true);
            return;
        }

        x += minecraftWidth;
        if (mmm$isInside(mouseX, mouseY, x, y, mmm$tabWidth(MMM_CHANNEL_LABEL), TAB_HEIGHT))
        {
            mmm$switchChannel(true);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void mmm$sendPublicMessage(String message, boolean addToHistory, CallbackInfo ci)
    {
        if (mmm$publicChannelSelected == false)
        {
            return;
        }

        ci.cancel();
        String normalized = message == null ? "" : message.trim();
        if (normalized.isBlank())
        {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null)
        {
            return;
        }
        if (WebsiteLinkManager.isCurrentPlayerLinked() == false)
        {
            client.player.sendSystemMessage(Component.literal("[MMM] ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Website link required.").withStyle(ChatFormatting.RED)));
            return;
        }

        PublicChatClient.sendMessage(normalized);
    }

    @Redirect(
            method = "handleChatInput",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V"),
            require = 0)
    private void mmm$routeDefaultTeamChat(ClientPacketListener networkHandler, String message)
    {
        if (!Configs.Generic.SCOREBOARD_DEFAULT_TEAM_CHAT.getBooleanValue())
        {
            networkHandler.sendChat(message);
            return;
        }
        if (message.startsWith("#"))
        {
            networkHandler.sendChat(message.length() == 1 ? message : message.substring(1));
            return;
        }
        networkHandler.sendCommand("teammsg " + message);
    }

    private void mmm$drawTab(GuiGraphicsExtractor context, boolean publicChannel, String label,
                             int x, int y, int mouseX, int mouseY)
    {
        int width = mmm$tabWidth(label);
        boolean selected = mmm$publicChannelSelected == publicChannel;
        boolean hovered = mmm$isInside(mouseX, mouseY, x, y, width, TAB_HEIGHT);
        int background = selected ? 0xEE151515 : hovered ? 0xDD101010 : 0xCC070707;
        int border = selected ? MmmUi.accent() : hovered ? 0xFF5A5A5A : 0xFF292929;
        int color = selected ? 0xFFF5F5F5 : 0xFF949494;
        context.fill(x, y, x + width, y + TAB_HEIGHT, background);
        context.outline(x, y, width, TAB_HEIGHT, border);
        context.text(this.font, label, x + 6, y + 3, color);
    }

    private void mmm$switchChannel(boolean publicChannel)
    {
        if (mmm$publicChannelSelected == publicChannel || this.input == null)
        {
            return;
        }

        mmm$publicChannelSelected = publicChannel;
    }

    private int mmm$tabWidth(String label)
    {
        return this.font.width(label) + 12;
    }

    private static boolean mmm$isInside(double mouseX, double mouseY, int x, int y, int width, int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
