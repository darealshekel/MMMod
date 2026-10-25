package com.mmm.mixin;

import com.mmm.tags.TierTagManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTeam.class)
public abstract class TeamMixin
{
    @Inject(
            method = "formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;",
            at = @At("RETURN"),
            cancellable = true)
    private static void mmm$replaceTeamName(Team team, Component name, CallbackInfoReturnable<MutableComponent> cir)
    {
        MutableComponent decorated = TierTagManager.decorateName(name.getString(), cir.getReturnValue());
        if (decorated != null)
        {
            cir.setReturnValue(decorated);
        }
    }
}
