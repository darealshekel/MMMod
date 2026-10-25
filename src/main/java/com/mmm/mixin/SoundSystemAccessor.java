package com.mmm.mixin;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundSystem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundSystem.class)
public interface SoundSystemAccessor
{
    @Accessor("channel")
    Channel mmm$getChannel();
}
