package com.mmm.scoreboard;

import com.mmm.config.Configs;
import com.mmm.config.Configs.ScoreboardSorting;
import com.mmm.liteloader.LiteLoaderEnvironment;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.Formatting;

public final class ScoreboardService {
    private static BiFunction<String, String, String> nameDecorator = (owner, name) -> name;
    private static SidebarProvider sidebarProvider;
    private ScoreboardService() {}

    /** Main integration can install tier tags without coupling the sidebar to the social implementation. */
    public static void setNameDecorator(BiFunction<String, String, String> decorator) {
        nameDecorator = decorator == null ? (owner, name) -> name : decorator;
    }
    public interface SidebarProvider {
        boolean enabled();
        String title();
        List<RenderEntry> entries();
        boolean changePage(boolean forward);
    }
    public static void setSidebarProvider(SidebarProvider provider) { sidebarProvider = provider; }
    public static SidebarProvider activeSidebar() {
        return sidebarProvider != null && sidebarProvider.enabled() ? sidebarProvider : null;
    }
    public static Optional<ScoreboardObjective> getSidebarObjective(Minecraft client) {
        if (client == null || client.world == null || client.player == null) return Optional.empty();
        return Optional.ofNullable(sidebarObjective(client.world.getScoreboard(), client.player.getName()));
    }
    public static ScoreboardObjective sidebarObjective(Scoreboard board, String player) {
        Team team = board.getTeamOfMember(player);
        ScoreboardObjective objective = null;
        if (team != null && team.getColor().getId() >= 0)
            objective = board.getDisplayObjective(3 + team.getColor().getId());
        return objective != null ? objective : board.getDisplayObjective(1);
    }
    public static List<ScoreboardScore> getSortedEntries(ScoreboardObjective objective) {
        if (objective == null) return List.of();
        List<ScoreboardScore> entries = new ArrayList<>(objective.getScoreboard().getScores(objective));
        entries.removeIf(entry -> entry.getOwner() == null || entry.getOwner().startsWith("#"));
        Comparator<ScoreboardScore> byName = Comparator.comparing(ScoreboardScore::getOwner, String.CASE_INSENSITIVE_ORDER);
        ScoreboardSorting sorting = (ScoreboardSorting) Configs.Generic.SCOREBOARD_SORTING.getOptionListValue();
        entries.sort(switch (sorting) {
            case SCORE_DESCENDING -> Comparator.comparingInt(ScoreboardScore::get).reversed().thenComparing(byName);
            case SCORE_ASCENDING -> Comparator.comparingInt(ScoreboardScore::get).thenComparing(byName);
            case NAME_DESCENDING -> byName.reversed().thenComparingInt(ScoreboardScore::get);
            case NAME_ASCENDING -> byName.thenComparingInt(ScoreboardScore::get);
        });
        return entries;
    }
    public static List<RenderEntry> getRenderEntries(ScoreboardObjective objective) {
        List<ScoreboardScore> entries = getSortedEntries(objective);
        return getRenderEntries(objective, entries, 0, entries.size());
    }
    public static List<RenderEntry> getRenderEntries(ScoreboardObjective objective, List<ScoreboardScore> entries, int from, int to) {
        if (objective == null || entries == null) return List.of();
        int start = Math.max(0, Math.min(from, entries.size()));
        int end = Math.max(start, Math.min(to, entries.size()));
        List<RenderEntry> result = new ArrayList<>();
        for (int i = start; i < end; i++) {
            var score = entries.get(i);
            String name = Team.getMemberDisplayName(objective.getScoreboard().getTeamOfMember(score.getOwner()), score.getOwner());
            String decorated = nameDecorator.apply(score.getOwner(), name);
            result.add(new RenderEntry(score.getOwner(), decorated == null ? name : decorated,
                    formatScore(score.get()), score.get()));
        }
        return result;
    }
    public static boolean shouldShowScores() { return Configs.Generic.SCOREBOARD_SCORES_VISIBLE.getBooleanValue(); }
    public static String formatScore(int score) {
        if (!shouldShowScores()) return "";
        String formatted = Configs.Generic.SCOREBOARD_SCORE_ABBREVIATED.getBooleanValue()
                ? formatAbbreviated(score) : formatNumber(score, Configs.Generic.SCOREBOARD_SCORE_COMMAS.getBooleanValue());
        return Formatting.RED + formatted;
    }
    /** Tab integration must call this for BOTH width measurement and rendering. Health hearts stay vanilla. */
    public static String formatTabScore(int score) {
        return shouldShowScores() ? formatNumber(score, Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS.getBooleanValue()) : "";
    }
    private static String formatNumber(int score, boolean commas) {
        return commas ? String.format(Locale.US, "%,d", score) : Integer.toString(score);
    }
    public static String formatAbbreviated(int score) {
        long absolute = Math.abs((long) score);
        if (absolute < 1000) return Integer.toString(score);
        String[] suffixes = {"", "k", "M", "B"};
        int suffix = 0;
        double value = absolute;
        while (value >= 1000 && suffix < 3) { value /= 1000; suffix++; }
        if (value >= 999.5 && suffix < 3) { value /= 1000; suffix++; }
        return (score < 0 ? "-" : "") + new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(value) + suffixes[suffix];
    }
    public static boolean pageUp() {
        SidebarProvider provider = activeSidebar();
        return provider != null ? provider.changePage(false)
                : Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue() > 0
                && ScoreboardState.pageUp(Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue());
    }
    public static boolean pageDown() {
        SidebarProvider provider = activeSidebar();
        if (provider != null) return provider.changePage(true);
        int size = Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue();
        return size > 0 && ScoreboardState.pageDown(getSidebarObjective(Minecraft.getInstance())
                .map(o -> getSortedEntries(o).size()).orElse(0), size);
    }
    public static ScoreboardState.Snapshot recordCurrent() throws IOException {
        ScoreboardObjective objective = requireObjective();
        var rows = getSortedEntries(objective).stream()
                .map(e -> new ScoreboardState.SnapshotRow(e.getOwner(), e.get())).toList();
        var snapshot = new ScoreboardState.Snapshot(objective.getName(), objective.getDisplayName(), System.currentTimeMillis(), rows);
        ScoreboardState.addSnapshot(snapshot);
        return snapshot;
    }
    public static Path exportCurrent() throws IOException {
        ScoreboardObjective objective = requireObjective();
        Path path = exportDirectory().resolve(safeFileName(objective.getName()) + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("Player,Score"); writer.newLine();
            for (var entry : getSortedEntries(objective)) writeRow(writer, entry.getOwner(), entry.get());
        }
        return path;
    }
    public static Path exportRecorded() throws IOException {
        var snapshots = ScoreboardState.getSnapshots();
        if (snapshots.isEmpty()) throw new IOException("No recorded scoreboards");
        Path path = exportDirectory().resolve(UUID.randomUUID() + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            for (var snapshot : snapshots) {
                writer.write(csv(snapshot.displayName())); writer.newLine();
                writer.write("Player,Score"); writer.newLine();
                var rows = new ArrayList<>(snapshot.rows());
                rows.sort(Comparator.comparing(ScoreboardState.SnapshotRow::name, String.CASE_INSENSITIVE_ORDER));
                for (var row : rows) writeRow(writer, row.name(), row.score());
                writer.newLine();
            }
        }
        return path;
    }
    private static void writeRow(BufferedWriter writer, String name, int value) throws IOException {
        writer.write(csv(name) + "," + value); writer.newLine();
    }
    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    private static String safeFileName(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() || safe.equals(".") || safe.equals("..") ? "scoreboard" : safe;
    }
    private static Path exportDirectory() throws IOException {
        Path path = LiteLoaderEnvironment.configDirectory().getParent().resolve("scoreboard-exports");
        Files.createDirectories(path);
        return path;
    }
    private static ScoreboardObjective requireObjective() throws IOException {
        return getSidebarObjective(Minecraft.getInstance()).orElseThrow(() -> new IOException("No sidebar scoreboard"));
    }
    public record RenderEntry(String owner, String name, String score, int value) {}
}

