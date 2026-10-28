package com.mmm.sync;

import com.google.gson.JsonObject;

/** Website ranks stay authoritative; local progress and HUD pace produce estimates only. */
public record RankOvertakeEstimate(Player player, Player target)
{
    public record Player(String name, int rank, long blocks) {}

    public static java.util.List<Player> players(JsonObject response)
    {
        requireMainLeaderboard(response);
        java.util.List<Player> result = new java.util.ArrayList<>();
        for (var element : response.getAsJsonArray("rows")) result.add(parsePlayer(element.getAsJsonObject()));
        return java.util.List.copyOf(result);
    }

    /** Null means another page is needed, not that the player reached first place. */
    public static RankOvertakeEstimate project(Player websitePlayer, java.util.Map<Integer, Player> rows, long total)
    {
        int rank = websitePlayer.rank();
        while (rank > 1)
        {
            Player next = rows.get(rank - 1);
            if (next == null || next.name().equalsIgnoreCase(websitePlayer.name())) return null;
            if (total <= next.blocks()) return new RankOvertakeEstimate(new Player(websitePlayer.name(), rank, total), next);
            rank--;
        }
        return new RankOvertakeEstimate(new Player(websitePlayer.name(), 1, total), null);
    }

    static java.net.URI leaderboardUri(String query)
    {
        // Unversioned search may return the old spreadsheet while current ranks rebuild.
        return java.net.URI.create("https://www.mmmaniacs.com/api/leaderboard?dataVersion=profile-aligned-ranks-v1&" + query);
    }

    public static Player findPlayer(JsonObject response, String username)
    {
        requireMainLeaderboard(response);
        for (var element : response.getAsJsonArray("rows"))
        {
            Player row = parsePlayer(element.getAsJsonObject());
            if (row.name().equalsIgnoreCase(username)) return row;
        }
        return null;
    }

    public static RankOvertakeEstimate fromTarget(Player player, JsonObject response)
    {
        requireMainLeaderboard(response);
        for (var element : response.getAsJsonArray("rows"))
        {
            Player target = parsePlayer(element.getAsJsonObject());
            if (target.rank() == player.rank() - 1 && !target.name().equalsIgnoreCase(player.name())
                    && target.blocks() >= player.blocks())
                return new RankOvertakeEstimate(player, target);
        }
        throw new IllegalArgumentException("Leaderboard changed while loading");
    }

    private static void requireMainLeaderboard(JsonObject response)
    {
        if (!response.has("scope") || !"main".equals(response.get("scope").getAsString())
                || !response.has("rows") || !response.get("rows").isJsonArray()
                || (response.has("fastLeftClick") && response.get("fastLeftClick").getAsBoolean()))
            throw new IllegalArgumentException("Manual player leaderboard required");
    }

    private static Player parsePlayer(JsonObject row)
    {
        if (!row.has("canonicalSourceRowTotal") || !row.get("canonicalSourceRowTotal").getAsBoolean())
            throw new IllegalArgumentException("Current leaderboard totals are not ready");
        String name = row.get("username").getAsString();
        int rank = row.get("rank").getAsInt();
        long blocks = row.get("blocksMined").getAsLong();
        if (name.isBlank() || name.length() > 32 || rank < 1 || blocks < 0 || blocks == Long.MAX_VALUE)
            throw new IllegalArgumentException("Invalid leaderboard player");
        return new Player(name, rank, blocks);
    }

    public long blocksNeeded()
    {
        return target == null ? 0 : Math.max(1L, target.blocks() - player.blocks() + 1L);
    }

    public long estimatedSeconds(long blocksPerHour)
    {
        if (blocksPerHour <= 0 || target == null) return -1;
        double seconds = Math.ceil(blocksNeeded() * 3600D / blocksPerHour);
        return Double.isFinite(seconds) && seconds < Long.MAX_VALUE ? Math.max(1L, (long) seconds) : -1L;
    }

    public static String formatActiveTime(long seconds)
    {
        if (seconds < 0) return "Waiting for mining pace";
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60 + (seconds % 60 == 0 ? 0 : 1);
        return minutes < 60 ? minutes + "m" : minutes / 60 + "h " + minutes % 60 + "m";
    }
}
