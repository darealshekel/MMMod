package com.mmm.legacy.social.mixin;

import com.mmm.legacy.social.LegacySocialHooks;
import com.mmm.scoreboard.ScoreboardService;
import com.mmm.social.ActiveDiggerManager;
import com.mmm.tags.TierTagManager;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class SocialPlayerTabMixin {
    @Shadow private Text footer;
    @Unique private Text mmm$renderFooter;

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void mmm$name(PlayerInfo player, CallbackInfoReturnable<String> cir) {
        String username = player.getProfile().getName();
        Text original = new LiteralText(cir.getReturnValue());
        Text tier = TierTagManager.decorateName(username, original);
        Text active = ActiveDiggerManager.decorateName(username, tier == null ? original : tier);
        if (active != null) cir.setReturnValue(active.getFormattedString());
        else if (tier != null) cir.setReturnValue(tier.getFormattedString());
    }

    // These are the two numeric-only builders: width (leading space) and drawing (yellow).
    // The HEARTS branch uses textures/append(float), so its layout and fallback stay vanilla.
    @Redirect(method = {"render", "renderDisplayScore"}, at = @At(value = "INVOKE",
            target = "Ljava/lang/StringBuilder;append(I)Ljava/lang/StringBuilder;", remap = false), require = 2, expect = 2)
    private StringBuilder mmm$formatTabScore(StringBuilder builder, int score) {
        if (!ScoreboardService.shouldShowScores()) builder.setLength(0);
        return builder.append(ScoreboardService.formatTabScore(score));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void mmm$prepareFooter(int width, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        mmm$renderFooter = LegacySocialHooks.tabFooter(footer);
    }

    // Read substitution leaves the server-owned footer and every live score completely untouched.
    @Redirect(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/overlay/PlayerTabOverlay;footer:Lnet/minecraft/text/Text;", opcode = 180))
    private Text mmm$footer(PlayerTabOverlay overlay) { return mmm$renderFooter; }

    @Inject(method = "render", at = @At("RETURN"))
    private void mmm$releaseFooter(int width, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) { mmm$renderFooter = null; }
}
