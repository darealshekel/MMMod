package com.mmm.legacy.social.mixin;

import com.mmm.legacy.LegacyDrawContext;
import com.mmm.legacy.social.ChatChannelAccess;
import com.mmm.legacy.social.ChatChannelState;
import com.mmm.legacy.social.LocalChatCompletion;
import com.mmm.legacy.social.MmmChatIgnoreListScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class SocialChatScreenMixin extends Screen implements ChatChannelAccess {
    @Shadow protected TextFieldWidget chatField;
    @Unique private final ChatChannelState mmm$channel = new ChatChannelState();
    @Unique private final LocalChatCompletion mmm$completion = new LocalChatCompletion();
    @Unique private static final int MMM_TAB_HEIGHT = 14;

    @Override public boolean mmm$isPublicChannel() { return mmm$channel.isPublicChannel(); }

    @Inject(method = "init", at = @At("TAIL"))
    private void mmm$init(CallbackInfo ci) { mmm$channel.initialize(chatField.getText()); }

    @Inject(method = "removed", at = @At("TAIL"))
    private void mmm$closed(CallbackInfo ci) { mmm$channel.close(); mmm$completion.reset(); }

    @Inject(method = "render", at = @At("TAIL"))
    private void mmm$tabs(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int x = chatField.x;
        int y = Math.max(1, chatField.y - MMM_TAB_HEIGHT - 2);
        mmm$tab("MINECRAFT", x, y, !mmm$isPublicChannel(), mouseX, mouseY);
        x += mmm$width("MINECRAFT");
        mmm$tab("MMM", x, y, mmm$isPublicChannel(), mouseX, mouseY);
        x += mmm$width("MMM") + 2;
        mmm$tab("...", x, y, false, mouseX, mouseY);
        if (mmm$inside(mouseX, mouseY, x, y, mmm$width("..."))) renderTooltip("MMM Chat settings", mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mmm$select(int x, int y, int button, CallbackInfo ci) {
        if (button != 0 || chatField == null) return;
        int left = chatField.x;
        int top = Math.max(1, chatField.y - MMM_TAB_HEIGHT - 2);
        if (mmm$inside(x, y, left, top, mmm$width("MINECRAFT"))) mmm$channel.select(false);
        else if (mmm$inside(x, y, left + mmm$width("MINECRAFT"), top, mmm$width("MMM"))) mmm$channel.select(true);
        else if (mmm$inside(x, y, left + mmm$width("MINECRAFT") + mmm$width("MMM") + 2, top, mmm$width("..."))) {
            minecraft.openScreen(new MmmChatIgnoreListScreen((Screen) (Object) this));
            ci.cancel();
            return;
        } else return;
        // Cancel before vanilla handles the click as a chat link or blurs the field.
        chatField.setFocused(true);
        mmm$completion.reset();
        ci.cancel();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void mmm$noServerCompletion(char character, int key, CallbackInfo ci) {
        if (key != Keyboard.KEY_TAB) { mmm$completion.reset(); return; }
        String completed = mmm$completion.complete(chatField.getText(), chatField.getCursor(), Screen.isShiftDown());
        if (completed != null) {
            chatField.setText(completed);
            chatField.setCursor(mmm$completion.cursor());
            ci.cancel();
        } else if (mmm$isPublicChannel() || LocalChatCompletion.isLocal(chatField.getText())) ci.cancel();
    }

    @Inject(method = "accept", at = @At("HEAD"), cancellable = true)
    private void mmm$noStaleCompletion(String[] suggestions, CallbackInfo ci) {
        if (mmm$isPublicChannel() || LocalChatCompletion.isLocal(chatField.getText())) ci.cancel();
    }

    @Unique private void mmm$tab(String label, int x, int y, boolean selected, int mouseX, int mouseY) {
        int width = mmm$width(label);
        boolean hovered = mmm$inside(mouseX, mouseY, x, y, width);
        LegacyDrawContext context = new LegacyDrawContext();
        context.fill(x, y, x + width, y + MMM_TAB_HEIGHT, selected ? 0xEE151515 : hovered ? 0xDD101010 : 0xCC070707);
        context.drawBorder(x, y, width, MMM_TAB_HEIGHT, selected ? 0xFFE00000 : hovered ? 0xFF5A5A5A : 0xFF292929);
        textRenderer.draw(label, x + 6, y + 3, selected ? 0xFFF5F5F5 : 0xFF949494, true);
    }

    @Unique private int mmm$width(String label) { return textRenderer.getWidth(label) + 12; }
    @Unique private static boolean mmm$inside(int mouseX, int mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + MMM_TAB_HEIGHT;
    }
}
