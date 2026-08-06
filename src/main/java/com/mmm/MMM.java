package com.mmm;

import com.mmm.storage.AsyncPersistence;
import java.time.Duration;
import com.mmm.sound.MmmSounds;
import com.mmm.sync.SyncQueueManager;
import com.mmm.timer.MmmTimerState;
import com.mmm.tracker.MiningStats;
import com.mmm.feature.BreakingIndicatorRenderer;
import com.mmm.feature.TranslucentLavaRenderer;
import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MMM implements ClientModInitializer
{
    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    @Override
    public void onInitializeClient()
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
