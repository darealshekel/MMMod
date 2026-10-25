package com.mmm.legacy.social.mixin;

import com.mmm.tags.TierTagManager;
import net.minecraft.client.gui.GameGui;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.scoreboard.team.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GameGui.class, priority = 900)
public abstract class SocialSidebarNameMixin {
    @Redirect(method = "renderScoreboardObjective", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/team/Team;getMemberDisplayName(Lnet/minecraft/scoreboard/team/AbstractTeam;Ljava/lang/String;)Ljava/lang/String;"), require = 0)
    private String mmm$name(AbstractTeam team, String owner) {
        return TierTagManager.decorateNameString(owner, Team.getMemberDisplayName(team, owner));
    }
}
