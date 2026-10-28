package com.mmm.legacy.achievement.mixin;

import com.mmm.advancement.MmmAdvancementTree;
import com.mmm.legacy.LegacyDrawContext;
import com.mmm.legacy.achievement.AdvancementViewport;
import java.util.Map;
import net.minecraft.client.gui.screen.menu.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screen.menu.advancements.AdvancementsTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenMixin {
    @Shadow private AdvancementsTab currentTab;

    @Inject(method = "init", at = @At("TAIL"))
    private void mmm$init(CallbackInfo ci) { mmm$refresh(); }

    @Inject(method = "render", at = @At("HEAD"))
    private void mmm$render(int x, int y, float delta, CallbackInfo ci) { mmm$refresh(); }

    @Redirect(method = "renderTabContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/menu/advancements/AdvancementsTab;renderContents()V"))
    private void mmm$clipTree(AdvancementsTab tab, int mouseX, int mouseY, int frameX, int frameY) {
        if (!MmmAdvancementTree.isMmmRoot(tab.getAdvancement())) {
            tab.renderContents();
            return;
        }
        // The native depth mask alone is insufficient with the HUD/world depth buffer.
        // Use screen coordinates, not the translated tree coordinates or scroll offsets.
        AdvancementViewport viewport = AdvancementViewport.fromFrame(frameX, frameY);
        try (LegacyDrawContext.Clip ignored = new LegacyDrawContext().clip(
                viewport.left(), viewport.top(), viewport.right(), viewport.bottom())) {
            tab.renderContents();
        }
    }

    @Redirect(method = {"renderTabs", "renderTooltips"}, at = @At(value = "INVOKE",
            target = "Ljava/util/Map;size()I", remap = false))
    private int mmm$showStandaloneRootTab(Map<?, ?> tabs) {
        int count = tabs.size();
        // Vanilla hides the sole tab. Keep MMM's icon and tooltip discoverable even
        // before the player has received any vanilla root advancements.
        return count == 1 && tabs.containsKey(MmmAdvancementTree.rootEntry()) ? 2 : count;
    }

    @Unique private void mmm$refresh() {
        AdvancementsScreen screen = (AdvancementsScreen) (Object) this;
        MmmAdvancementTree.install(screen);
        if (currentTab == null) screen.onSelectedTabChanged(MmmAdvancementTree.rootEntry());
    }
}
