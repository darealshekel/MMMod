package com.mmm.sound;

import net.minecraft.util.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class MmmSounds
{
    public static final Identifier GOAL_SUCCESS_ID = new Identifier("mmm", "goal_success");
    public static final Identifier GOAL_COMPLETE_ID = new Identifier("mmm", "goal_complete");

    public static final SoundEvent GOAL_SUCCESS = new SoundEvent(GOAL_SUCCESS_ID);
    public static final SoundEvent GOAL_COMPLETE = new SoundEvent(GOAL_COMPLETE_ID);

    private MmmSounds()
    {
    }

    public static void register()
    {
        Registry.register(Registry.SOUND_EVENT, GOAL_SUCCESS_ID, GOAL_SUCCESS);
        Registry.register(Registry.SOUND_EVENT, GOAL_COMPLETE_ID, GOAL_COMPLETE);
    }
}
