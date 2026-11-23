package com.mmm.liteloader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceParityTest {
    @Test void sharedRulesMatchSelectedReference() throws Exception {
        Path root = Path.of("../..");
        JsonObject legacy = new JsonParser().parse(Files.readString(Path.of("source-set.json"))).getAsJsonObject().getAsJsonObject("main");
        JsonObject modern = new JsonParser().parse(Files.readString(root.resolve("versions/1.21-fabric/source-set.json"))).getAsJsonObject().getAsJsonObject("main");
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
            String logical = "com/mmm/" + file;
            String owner = legacy.get(logical).getAsString();
            // Shared owners evolve together; compatibility variants keep their original parity baseline.
            Path reference = owner.equals(modern.get(logical).getAsString())
                    ? root.resolve(owner)
                    : Path.of("reference-1.21/src/main/java/com/mmm", file);
            String baseline = Files.readString(reference);
            if (file.equals("config/Configs.java")) {
                // Only the four collection preferences are adopted from the newer config model.
                String latest = Files.readString(root.resolve(modern.get(logical).getAsString()));
                var additions = latest.lines().filter(line -> line.matches(".*public static final Config(Integer|Boolean) COLLECTION_(FILTER|SORT|CATEGORY|GLOBAL) =.*")).toList();
                assertEquals(4, additions.size());
                baseline = baseline.replace("        public static final ConfigBoolean SHARE_COLLECTION_MILESTONES",
                        String.join("\n", additions) + "\n        public static final ConfigBoolean SHARE_COLLECTION_MILESTONES");
                int persisted = baseline.indexOf("PERSISTED_OPTIONS = ImmutableList.of(");
                baseline = baseline.substring(0, persisted) + baseline.substring(persisted).replace("                SHARE_COLLECTION_MILESTONES,",
                        "                SHARE_COLLECTION_MILESTONES,\n                COLLECTION_FILTER, COLLECTION_SORT, COLLECTION_CATEGORY, COLLECTION_GLOBAL,");
            }
            assertEquals(fingerprint(baseline),
                    fingerprint(Files.readString(Path.of(System.getProperty("mmm.sources", "src/main/java"), "com/mmm", file))), "Reference behavior drift: " + file);
        }
    }

    private static String fingerprint(String input) throws Exception {
        String source = input
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
