package com.mmm.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x") int mmm$getInventoryX();
    @Accessor("y") int mmm$getInventoryY();
    @Accessor("backgroundWidth") int mmm$getInventoryWidth();
}
