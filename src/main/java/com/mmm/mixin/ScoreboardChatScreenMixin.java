package com.mmm.mixin;

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
    private static Channel mmm$selectedChannel = Channel.MINECRAFT;

    private String mmm$minecraftDraft = "";
    private String mmm$publicDraft = "";

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

        this.mmm$minecraftDraft = this.input.getValue();
        if (this.mmm$minecraftDraft.isBlank() == false)
        {
            mmm$selectedChannel = Channel.MINECRAFT;
        }
        mmm$applySelectedChannel();
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
        mmm$drawTab(context, Channel.MINECRAFT, x, y, mouseX, mouseY);
        x += mmm$tabWidth(Channel.MINECRAFT);
        mmm$drawTab(context, Channel.MMM, x, y, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mmm$selectChannel(MouseButtonEvent input, boolean doubleClick,
                                   CallbackInfoReturnable<Boolean> cir)
    {
        if (input.button() != 0 || this.input == null)
        {
            return;
        }

        double mouseX = input.x();
        double mouseY = input.y();

        int x = this.input.getX();
        int y = Math.max(1, this.input.getY() - TAB_HEIGHT - 2);
        int minecraftWidth = mmm$tabWidth(Channel.MINECRAFT);
        if (mmm$isInside(mouseX, mouseY, x, y, minecraftWidth, TAB_HEIGHT))
        {
            mmm$switchChannel(Channel.MINECRAFT);
            cir.setReturnValue(true);
            return;
        }

        x += minecraftWidth;
        if (mmm$isInside(mouseX, mouseY, x, y, mmm$tabWidth(Channel.MMM), TAB_HEIGHT))
        {
            mmm$switchChannel(Channel.MMM);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void mmm$sendPublicMessage(String message, boolean addToHistory, CallbackInfo ci)
    {
        if (mmm$selectedChannel != Channel.MMM)
        {
            return;
        }

        ci.cancel();
        String normalized = message == null ? "" : message.trim();
        this.mmm$publicDraft = "";
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

    private void mmm$drawTab(GuiGraphicsExtractor context, Channel channel, int x, int y, int mouseX, int mouseY)
    {
        int width = mmm$tabWidth(channel);
        boolean selected = mmm$selectedChannel == channel;
        boolean hovered = mmm$isInside(mouseX, mouseY, x, y, width, TAB_HEIGHT);
        int background = selected ? 0xEE151515 : hovered ? 0xDD101010 : 0xCC070707;
        int border = selected ? MmmUi.accent() : hovered ? 0xFF5A5A5A : 0xFF292929;
        int color = selected ? 0xFFF5F5F5 : 0xFF949494;
        context.fill(x, y, x + width, y + TAB_HEIGHT, background);
        MmmUi.drawBorder(context, x, y, width, TAB_HEIGHT, border);
        context.text(this.font, channel.label, x + 6, y + 3, color);
    }

    private void mmm$switchChannel(Channel channel)
    {
        if (this.input == null)
        {
            return;
        }
        if (mmm$selectedChannel == channel)
        {
            this.setFocused(this.input);
            return;
        }

        if (mmm$selectedChannel == Channel.MMM)
        {
            this.mmm$publicDraft = this.input.getValue();
        }
        else
        {
            this.mmm$minecraftDraft = this.input.getValue();
        }
        mmm$selectedChannel = channel;
        mmm$applySelectedChannel();
    }

    private void mmm$applySelectedChannel()
    {
        if (this.input == null)
        {
            return;
        }

        boolean publicChannel = mmm$selectedChannel == Channel.MMM;
        this.input.setMaxLength(publicChannel ? PublicChatClient.MAX_MESSAGE_LENGTH : 256);
        this.input.setValue(publicChannel ? this.mmm$publicDraft : this.mmm$minecraftDraft);
        this.input.setHint(Component.literal(publicChannel
                ? "Message linked MMM players..."
                : "Minecraft chat..."));
        this.setFocused(this.input);
    }

    private int mmm$tabWidth(Channel channel)
    {
        return this.font.width(channel.label) + 12;
    }

    private static boolean mmm$isInside(double mouseX, double mouseY, int x, int y, int width, int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum Channel
    {
        MINECRAFT("MINECRAFT"),
        MMM("MMM");

        private final String label;

        Channel(String label)
        {
            this.label = label;
        }
    }
}
