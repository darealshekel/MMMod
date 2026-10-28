package com.mmm.legacy.social.mixin;

import com.mmm.tags.TierTagManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class SocialPlayerNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void mmm$name(CallbackInfoReturnable<Text> cir) {
        Text decorated = TierTagManager.decorateName(((PlayerEntity) (Object) this).getName(), cir.getReturnValue());
        if (decorated != null) cir.setReturnValue(decorated);
    }
}
