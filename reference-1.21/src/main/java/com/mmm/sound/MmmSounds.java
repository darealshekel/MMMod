package com.mmm.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class MmmSounds
{
    public static final Identifier GOAL_SUCCESS_ID = Identifier.of("mmm", "goal_success");
    public static final Identifier GOAL_COMPLETE_ID = Identifier.of("mmm", "goal_complete");

    public static final SoundEvent GOAL_SUCCESS = SoundEvent.of(GOAL_SUCCESS_ID);
    public static final SoundEvent GOAL_COMPLETE = SoundEvent.of(GOAL_COMPLETE_ID);

    private MmmSounds()
    {
    }

    public static void register()
    {
        Registry.register(Registries.SOUND_EVENT, GOAL_SUCCESS_ID, GOAL_SUCCESS);
        Registry.register(Registries.SOUND_EVENT, GOAL_COMPLETE_ID, GOAL_COMPLETE);
    }
}
