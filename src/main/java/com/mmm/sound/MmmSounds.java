package com.mmm.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class MmmSounds
{
    public static final Identifier GOAL_SUCCESS_ID = Identifier.fromNamespaceAndPath("mmm", "goal_success");
    public static final Identifier GOAL_COMPLETE_ID = Identifier.fromNamespaceAndPath("mmm", "goal_complete");

    public static final SoundEvent GOAL_SUCCESS = SoundEvent.createVariableRangeEvent(GOAL_SUCCESS_ID);
    public static final SoundEvent GOAL_COMPLETE = SoundEvent.createVariableRangeEvent(GOAL_COMPLETE_ID);

    private MmmSounds()
    {
    }

    public static void register()
    {
        Registry.register(BuiltInRegistries.SOUND_EVENT, GOAL_SUCCESS_ID, GOAL_SUCCESS);
        Registry.register(BuiltInRegistries.SOUND_EVENT, GOAL_COMPLETE_ID, GOAL_COMPLETE);
    }
}
