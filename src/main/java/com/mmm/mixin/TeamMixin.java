package com.mmm.mixin;

import com.mmm.tags.TierTagManager;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Team.class)
public abstract class TeamMixin
{
    @Inject(
            method = "decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;",
            at = @At("HEAD"),
            cancellable = true)
    private static void mmm$replaceTeamName(AbstractTeam team, Text name, CallbackInfoReturnable<MutableText> cir)
    {
        MutableText decorated = TierTagManager.decorateName(name.getString(), name);
        if (decorated != null)
        {
            cir.setReturnValue(decorated);
        }
    }
}
