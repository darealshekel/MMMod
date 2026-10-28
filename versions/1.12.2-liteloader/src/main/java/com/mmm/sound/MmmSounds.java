package com.mmm.sound;

import net.minecraft.resource.Identifier;
import net.minecraft.sound.SoundEvent;

public final class MmmSounds {
    public static final Identifier GOAL_SUCCESS_ID = new Identifier("mmm", "goal_success");
    public static final Identifier GOAL_COMPLETE_ID = new Identifier("mmm", "goal_complete");
    // Client-only events resolve from the bundled sounds.json, without altering the server registry.
    public static final SoundEvent GOAL_SUCCESS = new SoundEvent(GOAL_SUCCESS_ID);
    public static final SoundEvent GOAL_COMPLETE = new SoundEvent(GOAL_COMPLETE_ID);
    private MmmSounds() {}
}
