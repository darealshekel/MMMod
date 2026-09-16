package com.mmm.leaderboard;

import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LeaderboardData
{
    public enum Kind {
        PLAYERS("Players", ""), SERVERS("Servers", ""), WORLD("Single World", "single-world"),
        TMC("TMC Digs", "player-tmc-digs"), SSP("SSP", "ssp"), HSP("HSP", "hsp"),
        COUNTRIES("Countries", "countries"), BLOCKS("Blocks", "");
        public final String label, apiKind;
        Kind(String label, String apiKind) { this.label = label; this.apiKind = apiKind; }
    }
    public record Query(Kind kind, String block, String search, int page) {
        public Query {
            Objects.requireNonNull(kind);
            block = Objects.requireNonNullElse(block, "minecraft:stone");
            search = Objects.requireNonNullElse(search, "").trim();
            if (search.length() > 64 || page < 1) throw new IllegalArgumentException("Invalid ranking query");
        }
        public URI uri() {
            String base = "https://www.mmmaniacs.com/api/";
            String params = "dataVersion=latest-sync-v14&pageSize=25&page=" + page + "&query=" + encode(search);
            return URI.create(base + switch (kind) {
                case PLAYERS -> "leaderboard?" + params;
                case SERVERS -> "leaderboard-sources?dataVersion=latest-sync-v14";
                case BLOCKS -> "leaderboard-blocks?" + params + "&block=" + encode(block);
                default -> "leaderboard-special?" + params + "&kind=" + kind.apiKind;
            });
        }
    }
    public record Row(int rank, String name, long blocks, String detail) {}
    public record Page(List<Row> rows, int page, int pages, long total, long blocks) {
        public Page { rows = List.copyOf(rows); }
    }
    private LeaderboardData() {}
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String text(JsonObject object, String key) {
        var value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
    private static long number(JsonObject object, String key) {
        var value = object.get(key);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Missing " + key);
        long result = value.getAsBigDecimal().longValueExact();
        if (result < 0) throw new IllegalArgumentException("Negative " + key);
        return result;
    }
    public static Page parse(Query query, String body) {
        JsonElement json = JsonParser.parseString(body);
        if (query.kind == Kind.SERVERS) return servers(query, json.getAsJsonArray());
        JsonObject object = json.getAsJsonObject();
        boolean block = query.kind == Kind.BLOCKS;
        if (block && !text(object, "blockId").equals(query.block)) throw new IllegalArgumentException("Wrong block ranking");
        if (query.kind == Kind.PLAYERS && !text(object, "scope").equals("main")) throw new IllegalArgumentException("Wrong ranking scope");
        List<Row> rows = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray("rows")) {
            JsonObject row = element.getAsJsonObject();
            String name = text(row, "username");
            if (name.isBlank()) throw new IllegalArgumentException("Missing ranking name");
            long worlds = block ? number(row, "worlds") : 0;
            String detail = block ? worlds + (worlds == 1 ? " world tracked" : " worlds tracked") : text(row, "sourceServer");
            rows.add(new Row(Math.toIntExact(number(row, "rank")), name, number(row, block ? "blocks" : "blocksMined"), detail));
        }
        long total = number(object, block ? "matchingPlayers" : "totalRows");
        int size = Math.max(1, Math.toIntExact(number(object, "pageSize")));
        int pages = block ? (int) Math.max(1, (total + size - 1) / size) : Math.max(1, Math.toIntExact(number(object, "totalPages")));
        return new Page(rows, Math.toIntExact(number(object, "page")), pages, total, number(object, "totalBlocks"));
    }
    private static Page servers(Query query, JsonArray array) {
        List<Row> all = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject source = element.getAsJsonObject();
            if (!serverSource(source)) continue;
            all.add(new Row(0, text(source, "displayName"), number(source, "totalBlocks"), text(source, "playerCount") + " players"));
        }
        all.sort(Comparator.comparingLong(Row::blocks).reversed().thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER));
        List<Row> matched = new ArrayList<>();
        long blocks = 0;
        for (int i = 0; i < all.size(); i++) {
            Row row = all.get(i);
            blocks += row.blocks;
            if (row.name.toLowerCase(Locale.ROOT).contains(query.search.toLowerCase(Locale.ROOT)))
                matched.add(new Row(i + 1, row.name, row.blocks, row.detail));
        }
        int pages = Math.max(1, (matched.size() + 24) / 25);
        int page = Math.min(query.page, pages), from = Math.min(matched.size(), (page - 1) * 25);
        return new Page(matched.subList(from, Math.min(from + 25, matched.size())), page, pages, matched.size(), blocks);
    }
    // Mirrors the website's server-directory exclusions, including explicit server overrides.
    static boolean serverSource(JsonObject source) {
        String name = text(source, "displayName").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (name.matches("individual world digs(?:\\s*(?:\\(\\d+\\)|\\d+))?")) return false;
        String type = text(source, "sourceType").toLowerCase(Locale.ROOT), category = text(source, "sourceCategory").toLowerCase(Locale.ROOT);
        String scope = text(source, "sourceScope").toLowerCase(Locale.ROOT);
        Set<String> servers = Set.of("server", "private-server", "private_server", "private server", "multiplayer");
        if (servers.contains(type) || servers.contains(category) || Set.of("public_server", "private_server_digs").contains(scope)
                || name.replaceAll("[^a-z0-9]", "").equals("narutakusmp")) return true;
        String logo = text(source, "logoUrl") + text(source, "sourceSymbolHash");
        return !Set.of("ssp", "hsp", "singleplayer", "hardcore").contains(type)
                && !Set.of("ssp", "hsp", "singleplayer", "hardcore", "ssp-hsp").contains(category)
                && !Set.of("ssp_hsp", "private_singleplayer").contains(scope)
                && !name.matches("(?:ssp world|hsp world|unlabeled world)(?:\\s*(?:\\(\\d+\\)|\\d+))?")
                && !name.matches("hardcore(?:\\s+(?:season|world|singleplayer|single-player|single player|survival)\\b.*|\\s*\\d+)")
                && !logo.contains("53af69d6f765a123be8e19bb6486fca6") && !logo.contains("3f71b13fd1b931f6387851f2bf31db02");
    }
}
