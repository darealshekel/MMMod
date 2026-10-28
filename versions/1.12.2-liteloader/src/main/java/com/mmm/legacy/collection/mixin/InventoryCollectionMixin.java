package com.mmm.legacy.collection.mixin;

import com.mmm.legacy.collection.InventoryCollectionButton;
import net.minecraft.client.gui.screen.game.inventory.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.game.inventory.PlayerInventoryScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.inventory.menu.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SurvivalInventoryScreen.class, CreativeInventoryScreen.class})
public abstract class InventoryCollectionMixin extends PlayerInventoryScreen {
    @Unique private InventoryCollectionButton mmm$collectionBook;
    protected InventoryCollectionMixin(InventoryMenu menu) { super(menu); }
    @Inject(method = "init", at = @At("RETURN"))
    private void mmm$initBook(CallbackInfo ci) { mmm$collectionBook = new InventoryCollectionButton(this); }
    @Inject(method = "render", at = @At("RETURN"))
    private void mmm$renderBook(int mx, int my, float delta, CallbackInfo ci) {
        if (mmm$collectionBook == null) return;
        mmm$collectionBook.position(x, y, backgroundWidth);
        mmm$collectionBook.render(minecraft, mx, my, delta);
    }
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mmm$clickBook(int mx, int my, int button, CallbackInfo ci) {
        if (mmm$collectionBook == null) return;
        mmm$collectionBook.position(x, y, backgroundWidth);
        if (mmm$collectionBook.click(mx, my, button)) ci.cancel();
    }
}
