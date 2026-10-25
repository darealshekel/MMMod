package com.mmm.mixin;

import com.mmm.advancement.MmmAdvancementTree;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementTab;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenMixin
{
    @Shadow private AdvancementTab selectedTab;

    @Inject(method = "init", at = @At("TAIL"))
    private void mmm$installAdvancementTab(CallbackInfo ci)
    {
        mmm$installAndSelectIfEmpty();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void mmm$refreshAdvancementProgress(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci)
    {
        mmm$installAndSelectIfEmpty();
    }

    private void mmm$installAndSelectIfEmpty()
    {
        AdvancementsScreen screen = (AdvancementsScreen) (Object) this;
        MmmAdvancementTree.install(screen);
        if (this.selectedTab == null)
        {
            screen.selectTab(MmmAdvancementTree.rootEntry());
        }
    }
}
