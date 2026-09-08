package com.mmm.mixin;

import com.mmm.config.Configs;
import com.mmm.social.PublicChatClient;
import com.mmm.sync.WebsiteLinkManager;
import com.mmm.ui.MmmUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
    protected TextFieldWidget chatField;

    protected ScoreboardChatScreenMixin(Text title)
    {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mmm$initializeChannel(CallbackInfo ci)
    {
        if (this.chatField == null)
        {
            return;
        }

        if (this.chatField.getText().isBlank() == false)
        {
            mmm$publicChannelSelected = false;
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void mmm$resetChannelOnClose(CallbackInfo ci)
    {
        mmm$publicChannelSelected = false;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mmm$renderChannelTabs(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci)
    {
        if (this.chatField == null)
        {
            return;
        }

        int x = this.chatField.getX();
        int y = Math.max(1, this.chatField.getY() - TAB_HEIGHT - 2);
        mmm$drawTab(context, false, MINECRAFT_CHANNEL_LABEL, x, y, mouseX, mouseY);
        x += mmm$tabWidth(MINECRAFT_CHANNEL_LABEL);
        mmm$drawTab(context, true, MMM_CHANNEL_LABEL, x, y, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mmm$selectChannel(double mouseX, double mouseY, int button,
                                   CallbackInfoReturnable<Boolean> cir)
    {
        if (button != 0 || this.chatField == null)
        {
            return;
        }

        int x = this.chatField.getX();
        int y = Math.max(1, this.chatField.getY() - TAB_HEIGHT - 2);
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

    @Inject(method = "sendMessage(Ljava/lang/String;Z)Z", at = @At("HEAD"), cancellable = true)
    private void mmm$sendPublicMessage(String message, boolean addToHistory, CallbackInfoReturnable<Boolean> cir)
    {
        if (mmm$publicChannelSelected == false)
        {
            return;
        }

        cir.setReturnValue(true);
        String normalized = message == null ? "" : message.trim();
        if (normalized.isBlank())
        {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null)
        {
            return;
        }
        if (WebsiteLinkManager.isCurrentPlayerLinked() == false)
        {
            client.player.sendMessage(Text.literal("[MMM] ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal("Website link required.").formatted(Formatting.RED)), false);
            return;
        }

        PublicChatClient.sendMessage(normalized);
    }

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

    private void mmm$drawTab(DrawContext context, boolean publicChannel, String label,
                             int x, int y, int mouseX, int mouseY)
    {
        int width = mmm$tabWidth(label);
        boolean selected = mmm$publicChannelSelected == publicChannel;
        boolean hovered = mmm$isInside(mouseX, mouseY, x, y, width, TAB_HEIGHT);
        int background = selected ? 0xEE151515 : hovered ? 0xDD101010 : 0xCC070707;
        int border = selected ? MmmUi.accent() : hovered ? 0xFF5A5A5A : 0xFF292929;
        int color = selected ? 0xFFF5F5F5 : 0xFF949494;
        context.fill(x, y, x + width, y + TAB_HEIGHT, background);
        context.drawBorder(x, y, width, TAB_HEIGHT, border);
        context.drawTextWithShadow(this.textRenderer, label, x + 6, y + 3, color);
    }

    private void mmm$switchChannel(boolean publicChannel)
    {
        if (mmm$publicChannelSelected == publicChannel || this.chatField == null)
        {
            return;
        }

        mmm$publicChannelSelected = publicChannel;
    }

    private int mmm$tabWidth(String label)
    {
        return this.textRenderer.getWidth(label) + 12;
    }

    private static boolean mmm$isInside(double mouseX, double mouseY, int x, int y, int width, int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
