package com.mmm.sound;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GoalSoundLibraryTest {
    @TempDir Path directory;

    @AfterEach void cleanup() throws Exception {
        for (int threshold : GoalSoundLibrary.MILESTONES) GoalSoundLibrary.reset(threshold);
    }

    @Test void installsAtomicallyAndKeepsIndependentMilestones() throws Exception {
        Path file = directory.resolve("selected.OGG");
        byte[] content = {'O', 'g', 'g', 'S', 0, 1, 2, 3};
        Files.write(file, content);
        GoalSoundLibrary.install(25, file);
        assertTrue(GoalSoundLibrary.hasCustomSound(25));
        assertFalse(GoalSoundLibrary.hasCustomSound(50));
        assertEquals("selected.OGG", GoalSoundLibrary.getDisplayName(25));
        assertArrayEquals(content, Files.readAllBytes(GoalSoundLibrary.soundPath(25)));
        GoalSoundLibrary.reset(25);
        assertEquals("MMM default", GoalSoundLibrary.getDisplayName(25));
    }

    @Test void invalidImportsLeaveCurrentSoundUntouched() throws Exception {
        Path file = directory.resolve("good.ogg");
        Files.write(file, new byte[]{'O', 'g', 'g', 'S', 1});
        GoalSoundLibrary.install(25, file);
        Path bad = directory.resolve("bad.ogg");
        Files.write(bad, new byte[]{0, 1, 2, 3, 4});
        assertThrows(java.io.IOException.class, () -> GoalSoundLibrary.install(25, bad));
        assertEquals("good.ogg", GoalSoundLibrary.getDisplayName(25));
        assertThrows(java.io.IOException.class, () -> GoalSoundLibrary.install(25, directory.resolve("missing.ogg")));
        assertThrows(IllegalArgumentException.class, () -> GoalSoundLibrary.install(125, file));
    }

    @Test void customUrlResolverAcceptsOnlyTheFourInstalledSoundIds() throws Exception {
        Path file = directory.resolve("good.ogg");
        Files.write(file, new byte[]{'O', 'g', 'g', 'S', 1});
        GoalSoundLibrary.install(100, file);
        assertEquals(GoalSoundLibrary.soundPath(100).toUri().toURL(),
                CustomGoalSoundPlayer.resolve(new net.minecraft.resource.Identifier("mmm", "sounds/custom_goal_100.ogg")));
        assertNull(CustomGoalSoundPlayer.resolve(new net.minecraft.resource.Identifier("other", "sounds/custom_goal_100.ogg")));
        assertNull(CustomGoalSoundPlayer.resolve(new net.minecraft.resource.Identifier("mmm", "sounds/custom_goal_125.ogg")));
    }
}
