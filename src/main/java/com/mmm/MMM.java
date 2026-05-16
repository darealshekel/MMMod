package com.mmm;

import fi.dy.masa.malilib.event.InitializationHandler;
import com.mmm.sync.SyncQueueManager;
import com.mmm.tracker.MiningStats;
import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MMM implements ClientModInitializer
{
    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    @Override
    public void onInitializeClient()
    {
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try
            {
                MiningStats.finaliseSession();
                SyncQueueManager.forceFlush("client shutdown");
            }
            catch (Exception e)
            {
                LOGGER.warn("[MMM] Failed to finalise session during client shutdown: {}", e.getMessage());
            }
        }, "MMM shutdown save"));
        LOGGER.info("{} {} initialized", Reference.MOD_NAME, Reference.MOD_VERSION);
    }
}
