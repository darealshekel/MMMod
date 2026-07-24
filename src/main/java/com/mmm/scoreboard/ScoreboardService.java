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

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class ScoreboardService
{
    private static final java.text.NumberFormat COMMA_FORMAT = java.text.NumberFormat.getIntegerInstance(Locale.US);
    private static final DecimalFormat ABBREVIATION_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final String[] ABBREVIATION_SUFFIXES = { "", "k", "M", "B" };

    private ScoreboardService()
    {
    }

    public static Optional<ScoreboardObjective> getSidebarObjective(MinecraftClient client)
    {
        if (client == null || client.world == null || client.player == null)
        {
            return Optional.empty();
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = null;
        Team team = scoreboard.getScoreHolderTeam(client.player.getNameForScoreboard());
        if (team != null)
        {
            ScoreboardDisplaySlot teamSlot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
            if (teamSlot != null)
            {
                objective = scoreboard.getObjectiveForSlot(teamSlot);
            }
        }
        if (objective == null)
        {
            objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        }
        return Optional.ofNullable(objective);
    }

    public static List<ScoreboardEntry> getSortedEntries(ScoreboardObjective objective)
    {
        if (objective == null)
        {
            return List.of();
        }
        Comparator<ScoreboardEntry> comparator = comparator();
        return objective.getScoreboard().getScoreboardEntries(objective).stream()
                .filter(entry -> !entry.hidden())
                .sorted(comparator)
                .toList();
    }

    public static List<RenderEntry> getRenderEntries(ScoreboardObjective objective)
    {
        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);
        List<RenderEntry> rows = new ArrayList<>();
        for (ScoreboardEntry entry : getSortedEntries(objective))
        {
            Team team = scoreboard.getScoreHolderTeam(entry.owner());
            MutableText name = Team.decorateName(team, entry.name());
            Text score = formatScore(entry, numberFormat);
            rows.add(new RenderEntry(entry.owner(), name, score, entry.value()));
        }
        return rows;
    }

    public static Text formatScore(ScoreboardEntry entry, NumberFormat numberFormat)
    {
        if (!Configs.Generic.SCOREBOARD_SCORES_VISIBLE.getBooleanValue())
        {
            return Text.empty();
        }
        MutableText vanilla = entry.formatted(numberFormat);
        if (!vanilla.getString().equals(Integer.toString(entry.value())))
        {
            return vanilla;
        }
        if (Configs.Generic.SCOREBOARD_SCORE_ABBREVIATED.getBooleanValue())
        {
            return Text.literal(formatAbbreviated(entry.value())).setStyle(vanilla.getStyle());
        }
        if (Configs.Generic.SCOREBOARD_SCORE_COMMAS.getBooleanValue())
        {
            return Text.literal(COMMA_FORMAT.format(entry.value())).setStyle(vanilla.getStyle());
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
        Optional<ScoreboardObjective> objective = getSidebarObjective(MinecraftClient.getInstance());
        int count = objective.map(value -> getSortedEntries(value).size()).orElse(0);
        return ScoreboardState.pageDown(count, Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue());
    }

    public static ScoreboardState.Snapshot recordCurrent() throws IOException
    {
        ScoreboardObjective objective = getSidebarObjective(MinecraftClient.getInstance())
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
        ScoreboardObjective objective = getSidebarObjective(MinecraftClient.getInstance())
                .orElseThrow(() -> new IOException("No sidebar scoreboard is visible"));
        Path target = exportDirectory().resolve(safeFileName(objective.getName()) + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
        {
            writer.write("Player,Score");
            writer.newLine();
            for (ScoreboardEntry entry : getSortedEntries(objective))
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

    private static Comparator<ScoreboardEntry> comparator()
    {
        ScoreboardSorting sorting = (ScoreboardSorting) Configs.Generic.SCOREBOARD_SORTING.getOptionListValue();
        Comparator<ScoreboardEntry> byName = Comparator.comparing(
                entry -> entry.name().getString(), String.CASE_INSENSITIVE_ORDER);
        return switch (sorting)
        {
            case SCORE_DESCENDING -> Comparator.comparingInt(ScoreboardEntry::value).reversed()
                    .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);
            case SCORE_ASCENDING -> Comparator.comparingInt(ScoreboardEntry::value)
                    .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);
            case NAME_DESCENDING -> byName.reversed().thenComparingInt(ScoreboardEntry::value);
            case NAME_ASCENDING -> byName.thenComparingInt(ScoreboardEntry::value);
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

    public record RenderEntry(String owner, Text name, Text score, int value)
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
