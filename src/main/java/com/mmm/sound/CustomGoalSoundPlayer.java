package com.mmm.sound;

import com.mmm.util.MmmLog;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundPool;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.resource.Identifier;
import net.minecraft.sound.SoundCategory;

/** Uses Minecraft's streaming decoder, mixer, pause, volume and source cleanup. */
public final class CustomGoalSoundPlayer {
    private CustomGoalSoundPlayer() {}

    static boolean play(int threshold) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getSoundManager() == null || !GoalSoundLibrary.hasCustomSound(threshold)) return false;
        try {
            client.getSoundManager().play(new CustomInstance(threshold));
            return true;
        } catch (RuntimeException failure) {
            MmmLog.LOGGER.warn("[MMM] Custom goal sound unavailable", failure);
            return false;
        }
    }

    public static URL resolve(Identifier resource) {
        for (int threshold : GoalSoundLibrary.MILESTONES) {
            if (new Identifier("mmm", "sounds/custom_goal_" + threshold + ".ogg").equals(resource)
                    && Files.isRegularFile(GoalSoundLibrary.soundPath(threshold))) {
                try {
                    return GoalSoundLibrary.soundPath(threshold).toUri().toURL();
                } catch (MalformedURLException failure) {
                    MmmLog.LOGGER.warn("[MMM] Invalid custom sound path", failure);
                }
            }
        }
        return null;
    }

    private static final class CustomInstance extends SimpleSoundInstance {
        private final SoundPool pool;

        CustomInstance(int threshold) {
            super(new Identifier("mmm", "custom_goal_" + threshold), SoundCategory.MASTER,
                    1, 1, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0);
            pool = new SoundPool(getLocation(), null);
            sound = new Sound("mmm:custom_goal_" + threshold, 1, 1, 1, Sound.Type.FILE, true);
            pool.add(sound);
        }

        @Override public SoundPool resolve(SoundManager manager) { return pool; }
    }
}
