package com.mmm.scoreboard;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.mmm.config.Configs;
import com.mmm.config.Configs.ScoreboardSorting;
import com.mmm.tags.TierTagManager;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class ScoreboardService
{
    private static final java.text.NumberFormat COMMA_FORMAT = java.text.NumberFormat.getIntegerInstance(Locale.US);
    private static final DecimalFormat ABBREVIATION_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final String[] ABBREVIATION_SUFFIXES = { "", "k", "M", "B" };

    private ScoreboardService()
    {
    }

    public static Optional<Objective> getSidebarObjective(Minecraft client)
    {
        if (client == null || client.level == null || client.player == null)
        {
            return Optional.empty();
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = null;
        PlayerTeam team = scoreboard.getPlayersTeam(client.player.getScoreboardName());
        if (team != null)
        {
            DisplaySlot teamSlot = DisplaySlot.teamColorToSlot(team.getColor());
            if (teamSlot != null)
            {
                objective = scoreboard.getDisplayObjective(teamSlot);
            }
        }
        if (objective == null)
        {
            objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        }
        return Optional.ofNullable(objective);
    }

    public static List<PlayerScoreEntry> getSortedEntries(Objective objective)
    {
        if (objective == null)
        {
            return List.of();
        }

        List<PlayerScoreEntry> entries = new ArrayList<>(objective.getScoreboard().listPlayerScores(objective));
        entries.removeIf(PlayerScoreEntry::isHidden);
        entries.sort(comparator());
        return entries;
    }

    public static List<RenderEntry> getRenderEntries(Objective objective)
    {
        List<PlayerScoreEntry> entries = getSortedEntries(objective);
        return getRenderEntries(objective, entries, 0, entries.size());
    }

    public static List<RenderEntry> getRenderEntries(
            Objective objective,
            List<PlayerScoreEntry> sortedEntries,
            int fromIndex,
            int toIndex)
    {
        if (objective == null || sortedEntries == null || sortedEntries.isEmpty())
        {
            return List.of();
        }

        int from = Math.max(0, Math.min(fromIndex, sortedEntries.size()));
        int to = Math.max(from, Math.min(toIndex, sortedEntries.size()));
        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        List<RenderEntry> rows = new ArrayList<>(to - from);
        for (int index = from; index < to; index++)
        {
            PlayerScoreEntry entry = sortedEntries.get(index);
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            MutableComponent name = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            MutableComponent tierName = TierTagManager.decorateName(entry.owner(), name);
            if (tierName != null)
            {
                name = tierName;
            }
            Component score = formatScore(entry, numberFormat);
            rows.add(new RenderEntry(entry.owner(), name, score, entry.value()));
        }
        return rows;
    }

    public static Component formatScore(PlayerScoreEntry entry, NumberFormat numberFormat)
    {
        if (!Configs.Generic.SCOREBOARD_SCORES_VISIBLE.getBooleanValue())
        {
            return Component.empty();
        }
        MutableComponent vanilla = entry.formatValue(numberFormat);
        if (!vanilla.getString().equals(Integer.toString(entry.value())))
        {
            return vanilla;
        }
        if (Configs.Generic.SCOREBOARD_SCORE_ABBREVIATED.getBooleanValue())
        {
            return Component.literal(formatAbbreviated(entry.value())).setStyle(vanilla.getStyle());
        }
        if (Configs.Generic.SCOREBOARD_SCORE_COMMAS.getBooleanValue())
        {
            return Component.literal(COMMA_FORMAT.format(entry.value())).setStyle(vanilla.getStyle());
        }
        return vanilla;
    }

    public static boolean pageUp()
    {
        return ScoreboardState.pageUp(Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue());
    }

    public static boolean pageDown()
    {
        if (Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue() <= 0)
        {
            return false;
        }
        Optional<Objective> objective = getSidebarObjective(Minecraft.getInstance());
        int count = objective.map(value -> getSortedEntries(value).size()).orElse(0);
        return ScoreboardState.pageDown(count, Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue());
    }

    public static ScoreboardState.Snapshot recordCurrent() throws IOException
    {
        Objective objective = getSidebarObjective(Minecraft.getInstance())
                .orElseThrow(() -> new IOException("No sidebar scoreboard is visible"));
        List<ScoreboardState.SnapshotRow> rows = getSortedEntries(objective).stream()
                .map(entry -> new ScoreboardState.SnapshotRow(entry.owner(), entry.value()))
                .toList();
        ScoreboardState.Snapshot snapshot = new ScoreboardState.Snapshot(
                objective.getName(), objective.getDisplayName().getString(), Instant.now().toEpochMilli(), rows);
        ScoreboardState.addSnapshot(snapshot);
        return snapshot;
    }

    public static Path exportCurrent() throws IOException
    {
        Objective objective = getSidebarObjective(Minecraft.getInstance())
                .orElseThrow(() -> new IOException("No sidebar scoreboard is visible"));
        Path target = exportDirectory().resolve(safeFileName(objective.getName()) + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
        {
            writer.write("Player,Score");
            writer.newLine();
            for (PlayerScoreEntry entry : getSortedEntries(objective))
            {
                writer.write(csv(entry.owner()));
                writer.write(',');
                writer.write(Integer.toString(entry.value()));
                writer.newLine();
            }
        }
        return target;
    }

    public static Path exportRecorded() throws IOException
    {
        List<ScoreboardState.Snapshot> snapshots = ScoreboardState.getSnapshots();
        if (snapshots.isEmpty())
        {
            throw new IOException("No recorded scoreboards to export");
        }
        Path target = exportDirectory().resolve(UUID.randomUUID() + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW))
        {
            for (int i = 0; i < snapshots.size(); i++)
            {
                ScoreboardState.Snapshot snapshot = snapshots.get(i);
                writer.write(csv(snapshot.displayName()));
                writer.newLine();
                writer.write("Player,Score");
                writer.newLine();
                snapshot.rows().stream()
                        .sorted(Comparator.comparing(ScoreboardState.SnapshotRow::name, String.CASE_INSENSITIVE_ORDER))
                        .forEach(row -> {
                            try
                            {
                                writer.write(csv(row.name()));
                                writer.write(',');
                                writer.write(Integer.toString(row.score()));
                                writer.newLine();
                            }
                            catch (IOException exception)
                            {
                                throw new CsvWriteException(exception);
                            }
                        });
                if (i + 1 < snapshots.size())
                {
                    writer.newLine();
                }
            }
        }
        catch (CsvWriteException exception)
        {
            throw exception.ioException;
        }
        return target;
    }

    public static String formatAbbreviated(int score)
    {
        long value = score;
        long absolute = Math.abs(value);
        if (absolute < 1_000L)
        {
            return Long.toString(value);
        }
        int suffix = 0;
        double scaled = absolute;
        while (scaled >= 1_000.0D && suffix < ABBREVIATION_SUFFIXES.length - 1)
        {
            scaled /= 1_000.0D;
            suffix++;
        }
        if (scaled >= 999.5D && suffix < ABBREVIATION_SUFFIXES.length - 1)
        {
            scaled /= 1_000.0D;
            suffix++;
        }
        String formatted = ABBREVIATION_FORMAT.format(scaled);
        return (value < 0 ? "-" : "") + formatted + ABBREVIATION_SUFFIXES[suffix];
    }

    private static Comparator<PlayerScoreEntry> comparator()
    {
        ScoreboardSorting sorting = (ScoreboardSorting) Configs.Generic.SCOREBOARD_SORTING.getOptionListValue();
        Comparator<PlayerScoreEntry> byName = Comparator.comparing(
                entry -> entry.ownerName().getString(), String.CASE_INSENSITIVE_ORDER);
        return switch (sorting)
        {
            case SCORE_DESCENDING -> Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                    .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
            case SCORE_ASCENDING -> Comparator.comparingInt(PlayerScoreEntry::value)
                    .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
            case NAME_DESCENDING -> byName.reversed().thenComparingInt(PlayerScoreEntry::value);
            case NAME_ASCENDING -> byName.thenComparingInt(PlayerScoreEntry::value);
        };
    }

    private static Path exportDirectory() throws IOException
    {
        Path directory = FabricLoader.getInstance().getGameDir().resolve("scoreboard-exports");
        Files.createDirectories(directory);
        return directory;
    }

    private static String safeFileName(String value)
    {
        String safe = value == null ? "scoreboard" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "scoreboard" : safe;
    }

    private static String csv(String value)
    {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    public record RenderEntry(String owner, Component name, Component score, int value)
    {
    }

    private static final class CsvWriteException extends RuntimeException
    {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private final IOException ioException;

        private CsvWriteException(IOException ioException)
        {
            this.ioException = ioException;
        }
    }
}
