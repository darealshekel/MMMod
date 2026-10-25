package com.mmm.legacy.achievement.mixin;

import com.mmm.sound.CustomGoalSoundPlayer;
import java.net.URL;
import net.minecraft.client.sound.system.SoundEngine;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class CustomGoalSoundMixin {
    @Inject(method = "getSoundUrl", at = @At("HEAD"), cancellable = true)
    private static void mmm$customSound(Identifier location, CallbackInfoReturnable<URL> cir) {
        URL url = CustomGoalSoundPlayer.resolve(location);
        if (url != null) cir.setReturnValue(url);
    }
}
