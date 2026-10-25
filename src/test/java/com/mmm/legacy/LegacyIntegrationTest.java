package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.storage.AsyncPersistence;
import com.mmm.storage.SessionData;
import com.mmm.storage.SessionHistory;
import com.mmm.timer.MmmTimerState;
import com.mmm.tracker.MiningStats;
import java.time.Duration;
import java.lang.reflect.Field;
import net.minecraft.Bootstrap;
import net.minecraft.block.Blocks;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyIntegrationTest {
    private net.minecraft.client.Minecraft client;
    private Object previousClient;
    private Field singleton;

    private static final class HeadlessPlayer extends net.minecraft.client.entity.living.player.LocalClientPlayerEntity {
        private boolean creative;
        private boolean spectator;
        private HeadlessPlayer() { super(null, null, null, null, null); }
        @Override public boolean isCreative() { return creative; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public String getUuidString() { return "11111111-1111-4111-8111-111111111111"; }
    }

    private static sun.misc.Unsafe allocator() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
    @BeforeAll static void bootstrap() throws Exception {
        // JUnit has no Ornithe loader/access fixes. Initialize only real block/item registries;
        // these tests do not create a world, run biome generation, or claim a client smoke test.
        Field initialized = Bootstrap.class.getDeclaredField("initialized");
        initialized.setAccessible(true);
        if (initialized.getBoolean(null)) return;
        initialized.setBoolean(null, true);
        net.minecraft.sound.SoundEvent.init();
        net.minecraft.block.Block.init();
        net.minecraft.entity.living.effect.StatusEffect.init();
        net.minecraft.enchantment.Enchantment.init();
        net.minecraft.item.Item.init();
    }
    @AfterAll static void restoreLoader() throws Exception {
        AsyncPersistence.flush(Duration.ofSeconds(3));
    }

    @BeforeEach void reset() throws Exception {
        singleton = net.minecraft.client.Minecraft.class.getDeclaredField("INSTANCE");
        singleton.setAccessible(true);
        previousClient = singleton.get(null);
        client = (net.minecraft.client.Minecraft) allocator().allocateInstance(net.minecraft.client.Minecraft.class);
        client.player = (HeadlessPlayer) allocator().allocateInstance(HeadlessPlayer.class);
        singleton.set(null, client);
        Configs.WORLD_STATS.clear();
        Configs.Generic.WEBSITE_SYNC_ENABLED.setBooleanValue(false);
        Configs.Generic.GOAL_PICKAXE_ANIMATION.setBooleanValue(false);
        FeatureToggle.MMM_NOTIFICATIONS.setBooleanValue(false);
        FeatureToggle.MMM_SOUND_ALERTS.setBooleanValue(false);
        SessionHistory.loadForWorld("legacy-integration-test");
        MmmTimerState.reset();
        MiningStats.startWorldSession("legacy-integration-test");
        MiningStats.startNewSession();
    }
    @AfterEach void flush() throws Exception {
        try {
            MiningStats.finaliseSession();
            AsyncPersistence.flush(Duration.ofSeconds(3));
            Configs.WORLD_STATS.clear();
        } finally { singleton.set(null, previousClient); }
    }

    @Test void creativeAndSpectatorMiningCannotAdvanceCounters() {
        long before = Configs.totalBlocksMined;
        HeadlessPlayer player = (HeadlessPlayer) client.player;
        player.creative = true;
        MiningStats.recordBlockMined(Blocks.STONE, null, Blocks.STONE.defaultState());
        player.creative = false;
        player.spectator = true;
        MiningStats.recordBlockMined(Blocks.STONE, null, Blocks.STONE.defaultState());
        assertEquals(before, Configs.totalBlocksMined);
        assertEquals(0, MiningStats.getSessionTotal());
        assertEquals(0, MmmTimerState.getBestHourBlocks());
    }

    @Test void confirmedVariantMinesReachSessionTimerAndHudCounters() {
        long before = Configs.totalBlocksMined;
        for (int i = 0; i < 12; i++) MiningStats.recordBlockMined(Blocks.STONE, null, Blocks.STONE.getStateFromMetadata(3));
        assertEquals(12, MiningStats.getSessionTotal());
        assertEquals(12L, MiningStats.getCurrentSession().blockBreakdown.get("minecraft:diorite"));
        assertEquals(before + 12, Configs.totalBlocksMined);
        assertEquals(12, MmmTimerState.getCurrentHourBlocks());
        assertEquals(12, MmmTimerState.getBestHourBlocks());
        assertEquals(MmmTimerState.getBestHourBlocks(), MiningStats.getCurrentSession().getBestHourBlocks());
    }

    @Test void pausedMiningDoesNotIncreaseSessionOrBestHour() {
        MiningStats.recordBlockMined(Blocks.STONE, null, Blocks.STONE.defaultState());
        MiningStats.togglePauseSession();
        long active = MiningStats.getSessionDurationMs();
        for (int i = 0; i < 7; i++) MiningStats.recordBlockMined(Blocks.STONE, null, Blocks.STONE.defaultState());
        assertEquals(1, MiningStats.getSessionTotal());
        assertEquals(1, MmmTimerState.getBestHourBlocks());
        assertEquals(active, MiningStats.getSessionDurationMs());
    }

    @Test void endingSessionPersistsBestHourAndBothDurationsWithoutDuplicating() throws Exception {
        SessionData session = MiningStats.getCurrentSession();
        session.totalBlocks = 10_123;
        session.blockBreakdown.put("minecraft:stone", 10_123L);
        session.captureBestHourBlocks(9_876);
        Field accumulated = MiningStats.class.getDeclaredField("pausedAccumulatedMs");
        accumulated.setAccessible(true);
        // Move the start backwards using a complete session snapshot, not a real-time wait.
        SessionData elapsed = new SessionData(System.currentTimeMillis() - 4_800_000L);
        elapsed.totalBlocks = session.totalBlocks;
        elapsed.blockBreakdown.putAll(session.blockBreakdown);
        elapsed.captureBestHourBlocks(9_876);
        Field current = MiningStats.class.getDeclaredField("currentSession");
        current.setAccessible(true); current.set(null, elapsed);
        accumulated.setLong(null, 1_200_000L);
        SessionData saved = MiningStats.finaliseSession();
        assertEquals(9_876, saved.getBestHourBlocks());
        assertTrue(Math.abs(saved.getActiveDurationMs() - 3_600_000) < 1_000);
        assertEquals(1_200_000, saved.getWallDurationMs() - saved.getActiveDurationMs());
        int count = SessionHistory.getHistory().size();
        MiningStats.finaliseSession();
        assertEquals(count, SessionHistory.getHistory().size());
        SessionHistory.loadForWorld("legacy-integration-test");
        assertTrue(SessionHistory.getHistory().stream().anyMatch(s -> s.startTimeMs == saved.startTimeMs && s.getBestHourBlocks() == 9_876));
    }

    @Test void blockIconsRetainFlattenedVariantIdentity() {
        assertEquals(3, LegacyBlockItems.icon("minecraft:diorite").getDamage());
        assertEquals(1, LegacyBlockItems.icon("minecraft:red_sand").getDamage());
        assertEquals(0, LegacyBlockItems.icon("minecraft:oak_log").getDamage());
        assertEquals(1, LegacyBlockItems.icon("minecraft:dark_oak_log").getDamage());
        assertEquals(0, LegacyBlockItems.icon("minecraft:oak_leaves").getDamage());
        assertEquals(1, LegacyBlockItems.icon("minecraft:dark_oak_leaves").getDamage());
        assertEquals(0, LegacyBlockItems.icon("minecraft:brown_mushroom_block").getDamage());
        assertEquals(0, LegacyBlockItems.icon("minecraft:mushroom_stem").getDamage());
        assertTrue(LegacyBlockItems.icon("minecraft:ancient_debris").isEmpty());
    }

    @Test void scoreboardReconciliationDoesNotBecomeSessionMining() {
        MiningStats.recordBlockMined(Blocks.STONE, null, Blocks.STONE.defaultState());
        MiningStats.applyScoreboardTotalMined(1_000_000, "Total Blocks Mined", true, System.currentTimeMillis());
        assertEquals(1_000_000, MiningStats.getCurrentSourceTotalMined());
        assertEquals(1, MiningStats.getSessionTotal());
        assertEquals(1, MmmTimerState.getBestHourBlocks());
    }

    @Test void legacyPartialStatsKeepObservedVariants() {
        MiningStats.recordBlockMined(Blocks.STONE, null, Blocks.STONE.getStateFromMetadata(3));
        MiningStats.mergeLegacyStatsBlockBreakdown(java.util.Map.of("minecraft:obsidian", 123L), System.currentTimeMillis());
        String worldId = com.mmm.storage.WorldSessionContext.getCurrentWorldInfo().id();
        var stats = Configs.WORLD_STATS.stream().filter(s -> s.worldId.equals(worldId)).findFirst().orElseThrow();
        assertEquals(1L, stats.blockBreakdown.get("minecraft:diorite"));
        assertEquals(123L, stats.blockBreakdown.get("minecraft:obsidian"));
        assertTrue(LegacyBlockIds.hasAmbiguousMinedStat("minecraft:stone"));
        assertFalse(LegacyBlockIds.hasAmbiguousMinedStat("minecraft:obsidian"));
    }

    @Test void escapeMenuPausesOnlySingleplayerAndPreservesManualPause() throws Exception {
        // Allocate a headless context for the real pause method, not a replacement session engine.
        sun.misc.Unsafe allocator = allocator();
        client.world = (net.minecraft.client.world.ClientWorld) allocator.allocateInstance(net.minecraft.client.world.ClientWorld.class);
        client.screen = new net.minecraft.client.gui.screen.menu.GameMenuScreen();
        var update = MiningStats.class.getDeclaredMethod("updateMenuPauseState", net.minecraft.client.Minecraft.class, long.class);
        update.setAccessible(true);
        update.invoke(null, client, System.currentTimeMillis());
        assertFalse(MiningStats.isSessionPaused(), "A multiplayer Escape menu must not pause the session");
        Field integrated = net.minecraft.client.Minecraft.class.getDeclaredField("isIntegratedServerRunning");
        integrated.setAccessible(true); integrated.setBoolean(client, true);
        Field server = net.minecraft.client.Minecraft.class.getDeclaredField("server");
        server.setAccessible(true); server.set(client, allocator.allocateInstance(net.minecraft.server.integrated.IntegratedServer.class));
        update.invoke(null, client, System.currentTimeMillis());
        assertTrue(MiningStats.isSessionPaused());
        client.screen = null;
        update.invoke(null, client, System.currentTimeMillis());
        assertFalse(MiningStats.isSessionPaused());
        MiningStats.togglePauseSession();
        client.screen = new net.minecraft.client.gui.screen.menu.GameMenuScreen();
        update.invoke(null, client, System.currentTimeMillis());
        client.screen = null;
        update.invoke(null, client, System.currentTimeMillis());
        assertTrue(MiningStats.isSessionPaused(), "Closing the menu must not undo an explicit manual pause");
    }
}
