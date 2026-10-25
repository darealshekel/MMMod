package com.mmm;

import com.mmm.advancement.MmmAdvancementManager;
import com.mmm.storage.AsyncPersistence;
import java.time.Duration;
import com.mmm.sound.MmmSounds;
import com.mmm.sync.SyncQueueManager;
import com.mmm.timer.MmmTimerState;
import com.mmm.tracker.MiningStats;
import com.mmm.feature.BreakingIndicatorRenderer;
import com.mmm.feature.TranslucentLavaRenderer;
import net.ornithemc.osl.entrypoints.api.ModInitializer;
import org.apache.logging.log4j.Logger;

public class MMM implements ModInitializer
{
    public static final Logger LOGGER = com.mmm.util.MmmLog.LOGGER;

    @Override
    public void init()
    {
        MmmSounds.register();
        TranslucentLavaRenderer.initialize();
        BreakingIndicatorRenderer.initialize();
        new InitHandler().registerModHandlers();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try
            {
                MiningStats.finaliseSession();
                MmmTimerState.save();
                MmmAdvancementManager.flush();
                SyncQueueManager.forceFlush("client shutdown");
                AsyncPersistence.flush(Duration.ofSeconds(3L));
            }
            catch (Exception e)
            {
                LOGGER.warn("[MMM] Failed to finalise session during client shutdown: {}", e.getMessage());
            }
        }, "MMM shutdown save"));
        LOGGER.info("{} {} initialized", Reference.MOD_NAME, Reference.MOD_VERSION);
    }
}
