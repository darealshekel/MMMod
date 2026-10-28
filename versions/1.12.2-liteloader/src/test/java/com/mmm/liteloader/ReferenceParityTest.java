package com.mmm.liteloader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceParityTest {
    @Test void sharedRulesMatchCurrentModernReference() throws Exception {
        // LiteLoader HTTP compatibility differs; ApiClientTest and WebsiteAccessFailureTest cover it.
        for (String file : new String[] {
                "config/Configs.java", "config/Hotkeys.java", "config/FeatureToggle.java",
                "storage/SessionData.java", "tracker/SessionEfficiency.java",
                "tracker/CollectionProgress.java", "tracker/CollectionLiveProgress.java",
                "tracker/GoalMilestonePolicy.java", "tracker/SourceTotalPolicy.java",
                "tracker/RollingMiningMetrics.java", "sync/RankLocalProgress.java",
                "sync/RankOvertakeEstimate.java", "sync/SyncSchedule.java",
                "util/DailyProgressPolicy.java", "util/WeeklyProgressPolicy.java"
        }) {
            assertEquals(fingerprint(Path.of("reference-1.21/src/main/java/com/mmm", file)),
                    fingerprint(Path.of(System.getProperty("mmm.sources", "src/main/java"), "com/mmm", file)), "Reference behavior drift: " + file);
        }
    }

    private static String fingerprint(Path path) throws Exception {
        String source = Files.readString(path)
                .replace("\r", "")
                .replace("import com.mmm.MMM;", "import com.mmm.util.MmmLog;")
                .replace("MMM.LOGGER", "MmmLog.LOGGER")
                .replace("import net.fabricmc.loader.api.FabricLoader;", "import com.mmm.liteloader.LiteLoaderEnvironment;")
                .replace("FabricLoader.getInstance().getConfigDir()", "LiteLoaderEnvironment.configDirectory()")
                .replace("net.minecraft.client.MinecraftClient", "net.minecraft.client.Minecraft")
                .replace("isOnThread()", "isOnSameThread()")
                .replace("client.execute(", "client.executeTask(")
                .trim();
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
    }
}
