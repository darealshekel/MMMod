package com.mmm.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mmm.storage.WorldSessionContext;
import com.mmm.util.BlockBreakdownCatalog;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.resource.Identifier;
import com.mmm.legacy.LegacyBlockIds;

public final class ServerPlayerBlockBreakdownScanner
{
    private static final String MINED_CRITERION_PREFIX = "minecraft.mined:";
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private ServerPlayerBlockBreakdownScanner()
    {
    }

    public static JsonObject scan(Minecraft client, WorldSessionContext.WorldInfo worldInfo)
    {
        if (client == null || client.world == null || worldInfo == null)
        {
            return null;
        }

        Map<String, Map<String, Long>> byPlayer = new LinkedHashMap<>();
        for (ScoreboardReader.ObjectiveSnapshot snapshot : ScoreboardReader.readObjectives(client))
        {
            String blockId = extractMinedBlockId(snapshot.criterionName());
            if (BlockBreakdownCatalog.isValid(blockId) == false)
            {
                continue;
            }

            for (ScoreboardReader.ScoreboardLine line : snapshot.lines())
            {
                if (isUsername(line.owner()) == false || line.scoreValue() <= 0)
                {
                    continue;
                }

                byPlayer.computeIfAbsent(line.owner(), ignored -> new LinkedHashMap<>())
                        .merge(blockId, (long) line.scoreValue(), Math::max);
            }
        }

        if (byPlayer.isEmpty())
        {
            return null;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("source_key", ScoreboardSourceResolver.sourceKey(worldInfo.displayName(), worldInfo));
        payload.addProperty("source_name", ScoreboardSourceResolver.displayName(worldInfo.displayName(), worldInfo));
        payload.addProperty("captured_at", Instant.ofEpochMilli(System.currentTimeMillis()).toString());
        payload.addProperty("scope", "server_players");
        payload.addProperty("source", "scoreboard_objectives");

        JsonArray players = new JsonArray();
        byPlayer.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(playerEntry -> {
                    JsonObject player = new JsonObject();
                    player.addProperty("username", playerEntry.getKey());
                    long total = playerEntry.getValue().values().stream().mapToLong(Long::longValue).sum();
                    player.addProperty("total_count", total);

                    JsonArray blocks = new JsonArray();
                    playerEntry.getValue().entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                            .forEach(blockEntry -> {
                                JsonObject block = new JsonObject();
                                block.addProperty("block_id", blockEntry.getKey());
                                block.addProperty("count", blockEntry.getValue());
                                blocks.add(block);
                            });
                    player.add("items", blocks);
                    players.add(player);
                });
        payload.add("players", players);
        return payload;
    }

    public static String fingerprint(JsonObject payload)
    {
        if (payload == null)
        {
            return "";
        }

        JsonObject minimal = new JsonObject();
        copyIfPresent(payload, minimal, "source_key");
        copyIfPresent(payload, minimal, "source_name");
        copyIfPresent(payload, minimal, "scope");
        copyIfPresent(payload, minimal, "source");
        if (payload.has("players"))
        {
            minimal.add("players", payload.get("players"));
        }
        return GSON.toJson(minimal);
    }

    private static String extractMinedBlockId(String criterionName)
    {
        if (criterionName == null)
        {
            return null;
        }

        String normalized = criterionName.toLowerCase(Locale.ROOT);
        String prefix = normalized.startsWith("stat.mineblock.") ? "stat.mineblock." : MINED_CRITERION_PREFIX;
        if (normalized.startsWith(prefix) == false)
        {
            return null;
        }

        String statBlockName = normalized.substring(prefix.length());
        Identifier identifier = resolveStatIdentifier(statBlockName);
        if (identifier == null || !Block.REGISTRY.keySet().contains(identifier))
        {
            return null;
        }
        if (LegacyBlockIds.hasAmbiguousMinedStat(identifier.toString())) return null;
        return LegacyBlockIds.canonicalId(Block.REGISTRY.get(identifier).defaultState());
    }

    private static Identifier resolveStatIdentifier(String statBlockName)
    {
        Identifier direct = parseIdentifier(statBlockName);
        if (direct != null && Block.REGISTRY.keySet().contains(direct))
        {
            return direct;
        }

        int namespaceSeparator = statBlockName.indexOf('.');
        if (namespaceSeparator <= 0 || namespaceSeparator >= statBlockName.length() - 1)
        {
            return null;
        }

        String blockId = statBlockName.substring(0, namespaceSeparator) + ":" + statBlockName.substring(namespaceSeparator + 1);
        return parseIdentifier(blockId);
    }

    private static Identifier parseIdentifier(String value)
    {
        return value != null && value.matches("[a-z0-9_.-]+(?::[a-z0-9_./-]+)?") ? new Identifier(value) : null;
    }

    private static boolean isUsername(String owner)
    {
        return owner != null && USERNAME.matcher(owner).matches();
    }

    private static void copyIfPresent(JsonObject from, JsonObject to, String key)
    {
        if (from.has(key))
        {
            to.add(key, from.get(key));
        }
    }
}
