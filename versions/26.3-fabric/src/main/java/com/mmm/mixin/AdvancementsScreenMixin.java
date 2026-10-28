package com.mmm.mixin;

import com.mmm.advancement.MmmAdvancementTree;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenMixin implements com.mmm.advancement.MmmAdvancementScreenAccess
{
    @Shadow private AdvancementTab selectedTab;
    @Shadow @org.spongepowered.asm.mixin.Final private java.util.Map<net.minecraft.advancements.AdvancementHolder, AdvancementTab> tabs;

    @Override public java.util.Map<net.minecraft.advancements.AdvancementHolder, AdvancementTab> mmm$tabs() { return tabs; }

    @Inject(method = "init", at = @At("TAIL"))
    private void mmm$installAdvancementTab(CallbackInfo ci)
    {
        mmm$installAndSelectIfEmpty();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void mmm$refreshAdvancementProgress(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci)
    {
        mmm$installAndSelectIfEmpty();
    }

    private void mmm$installAndSelectIfEmpty()
    {
        AdvancementsScreen screen = (AdvancementsScreen) (Object) this;
        MmmAdvancementTree.install(screen);
        if (this.selectedTab == null || this.selectedTab.getRootAdvancement().equals(MmmAdvancementTree.rootEntry()))
        {
            screen.onSelectedTabChanged(MmmAdvancementTree.rootEntry());
        }
    }
}
