package com.mmm.sync;

import com.mmm.MMM;
import com.mmm.util.MmmDebugLogger;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

final class PersonalTotalDetector
{
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?i)(?<![A-Za-z0-9_])(\\d[\\d,._ ]*(?:\\.\\d+)?)\\s*([kmbt])?(?![A-Za-z0-9_])");
    private static final Pattern RANK_PREFIX_PATTERN = Pattern.compile("(?i)^\\s*(?:#|\\[)?(\\d{1,3})(?:\\]|[.):-])\\s+([A-Za-z0-9_]{3,16})\\b");
    private static final long PARSE_DEBUG_LOG_INTERVAL_MS = 30_000L;
    private static final long CANDIDATE_DEBUG_LOG_INTERVAL_MS = 5_000L;
    private static final List<String> PERSONAL_MARKERS = List.of("your", "you", "my", "personal", "self", "player");
    private static final List<String> DIG_MARKERS = List.of("dig", "dug");

    private PersonalTotalDetector()
    {
    }

    static Detection detect(Minecraft client)
    {
        return detect(client, ScoreboardReader.readObjectives(client));
    }

    static Detection detect(Minecraft client, List<ScoreboardReader.ObjectiveSnapshot> objectiveSnapshots)
    {
        if (client == null || client.player == null)
        {
            return Detection.empty("client-or-player-missing");
        }

        List<ScoreboardReader.ObjectiveSnapshot> snapshots = objectiveSnapshots == null ? List.of() : objectiveSnapshots;
        String username = client.player.getGameProfile().name();
        String usernameLower = username == null ? "" : username.toLowerCase(Locale.ROOT);
        SidebarResult sidebar = detectFromSidebar(client, username, usernameLower, snapshots);
        TabResult tab = detectFromTabList(client, username, usernameLower);
        ToolUsageResult toolUsage = detectToolUsageTotal(client, username);

        long chosen = Math.max(Math.max(sidebar.total(), tab.total()), toolUsage.total());
        String chosenSource = chosen <= 0L
                ? "none"
                : toolUsage.total() >= sidebar.total() && toolUsage.total() >= tab.total()
                    ? "tool-uses"
                    : sidebar.total() >= tab.total() ? "sidebar" : "tab";
        String skipReason = chosen <= 0L ? "no-valid-personal-total" : "";

        return new Detection(
                sidebar.total(),
                tab.total(),
                chosen,
                chosenSource,
                sidebar.objectiveTitle(),
                sidebar.matchedUsername(),
                sidebar.rawScore(),
                sidebar.renderedText(),
                tab.objectiveTitle(),
                tab.matchedUsername(),
                tab.rawScore(),
                tab.renderedText(),
                toolUsage.total(),
                toolUsage.objectiveTitle(),
                skipReason
        );
    }

    private static ToolUsageResult detectToolUsageTotal(Minecraft client, String username)
    {
        if (client == null || client.level == null || client.player == null || username == null || username.isBlank())
        {
            return new ToolUsageResult(0L, "");
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        long pickaxeUses = 0L;
        long shovelUses = 0L;
        long axeUses = 0L;
        long hoeUses = 0L;
        long shearsUses = 0L;

        for (Objective objective : scoreboard.getObjectives())
        {
            String context = clean(objective.getName()) + " " + clean(objective.getDisplayName().getString());
            boolean pickaxe = ScoreboardParser.isPickUsesObjective(context);
            boolean shovel = ScoreboardParser.isShovelUsesObjective(context);
            boolean axe = ScoreboardParser.isAxeUsesObjective(context);
            boolean hoe = ScoreboardParser.isHoeUsesObjective(context);
            boolean shears = ScoreboardParser.isShearsUsesObjective(context);
            int matchedKinds = (pickaxe ? 1 : 0)
                    + (shovel ? 1 : 0)
                    + (axe ? 1 : 0)
                    + (hoe ? 1 : 0)
                    + (shears ? 1 : 0);
            if (matchedKinds != 1)
            {
                continue;
            }

            long value = readDirectScore(scoreboard, objective, client.player.getGameProfile(), username);
            if (pickaxe)
            {
                pickaxeUses = Math.max(pickaxeUses, value);
            }
            else if (shovel)
            {
                shovelUses = Math.max(shovelUses, value);
            }
            else if (axe)
            {
                axeUses = Math.max(axeUses, value);
            }
            else if (hoe)
            {
                hoeUses = Math.max(hoeUses, value);
            }
            else
            {
                shearsUses = Math.max(shearsUses, value);
            }
        }

        return new ToolUsageResult(
                combineToolUsageTotals(pickaxeUses, shovelUses, axeUses, hoeUses, shearsUses),
                toolUsageObjectiveTitle(pickaxeUses, shovelUses, axeUses, hoeUses, shearsUses));
    }

    static FastTotalPlan buildFastTotalPlan(Minecraft client, String sourceType, String objectiveTitle)
    {
        if (client == null || client.level == null || client.player == null || sourceType == null)
        {
            return FastTotalPlan.empty();
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        return switch (sourceType)
        {
            case "tab" -> FastTotalPlan.single(
                    scoreboard,
                    sourceType,
                    matchingObjective(scoreboard.getDisplayObjective(DisplaySlot.LIST), objectiveTitle));
            case "sidebar" -> FastTotalPlan.single(
                    scoreboard,
                    sourceType,
                    matchingObjective(scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR), objectiveTitle));
            case "parser" -> buildParserPlan(client, scoreboard, sourceType, objectiveTitle);
            case "tool-uses" -> buildToolUsagePlan(client, scoreboard, sourceType);
            default -> FastTotalPlan.empty();
        };
    }

    static long readValidatedTotal(Minecraft client, FastTotalPlan plan)
    {
        if (client == null || client.level == null || client.player == null || plan == null || plan.isUsable() == false)
        {
            return 0L;
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        if (scoreboard != plan.scoreboard())
        {
            return 0L;
        }

        String username = client.player.getGameProfile().name();
        if ("tool-uses".equals(plan.sourceType()))
        {
            return combineToolUsageTotals(
                    readDirectScore(scoreboard, plan.pickaxe(), client.player.getGameProfile(), username),
                    readDirectScore(scoreboard, plan.shovel(), client.player.getGameProfile(), username),
                    readDirectScore(scoreboard, plan.axe(), client.player.getGameProfile(), username),
                    readDirectScore(scoreboard, plan.hoe(), client.player.getGameProfile(), username),
                    readDirectScore(scoreboard, plan.shears(), client.player.getGameProfile(), username));
        }

        return readDirectScore(scoreboard, plan.primary(), client.player.getGameProfile(), username);
    }

    private static FastTotalPlan buildParserPlan(
            Minecraft client,
            Scoreboard scoreboard,
            String sourceType,
            String expectedTitle)
    {
        Objective bestObjective = null;
        long best = 0L;
        for (Objective objective : scoreboard.getObjectives())
        {
            if (objectiveMatches(objective, expectedTitle))
            {
                long value = readDirectScore(
                        scoreboard,
                        objective,
                        client.player.getGameProfile(),
                        client.player.getGameProfile().name());
                if (bestObjective == null || value > best)
                {
                    bestObjective = objective;
                    best = value;
                }
            }
        }
        return FastTotalPlan.single(scoreboard, sourceType, bestObjective);
    }

    private static FastTotalPlan buildToolUsagePlan(
            Minecraft client,
            Scoreboard scoreboard,
            String sourceType)
    {
        Objective pickaxeObjective = null;
        Objective shovelObjective = null;
        Objective axeObjective = null;
        Objective hoeObjective = null;
        Objective shearsObjective = null;
        long pickaxeUses = 0L;
        long shovelUses = 0L;
        long axeUses = 0L;
        long hoeUses = 0L;
        long shearsUses = 0L;
        String username = client.player.getGameProfile().name();

        for (Objective objective : scoreboard.getObjectives())
        {
            String context = clean(objective.getName()) + " " + clean(objective.getDisplayName().getString());
            boolean pickaxe = ScoreboardParser.isPickUsesObjective(context);
            boolean shovel = ScoreboardParser.isShovelUsesObjective(context);
            boolean axe = ScoreboardParser.isAxeUsesObjective(context);
            boolean hoe = ScoreboardParser.isHoeUsesObjective(context);
            boolean shears = ScoreboardParser.isShearsUsesObjective(context);
            int matchedKinds = (pickaxe ? 1 : 0)
                    + (shovel ? 1 : 0)
                    + (axe ? 1 : 0)
                    + (hoe ? 1 : 0)
                    + (shears ? 1 : 0);
            if (matchedKinds != 1)
            {
                continue;
            }

            long value = readDirectScore(scoreboard, objective, client.player.getGameProfile(), username);
            if (pickaxe && (pickaxeObjective == null || value > pickaxeUses))
            {
                pickaxeObjective = objective;
                pickaxeUses = value;
            }
            else if (shovel && (shovelObjective == null || value > shovelUses))
            {
                shovelObjective = objective;
                shovelUses = value;
            }
            else if (axe && (axeObjective == null || value > axeUses))
            {
                axeObjective = objective;
                axeUses = value;
            }
            else if (hoe && (hoeObjective == null || value > hoeUses))
            {
                hoeObjective = objective;
                hoeUses = value;
            }
            else if (shears && (shearsObjective == null || value > shearsUses))
            {
                shearsObjective = objective;
                shearsUses = value;
            }
        }

        return new FastTotalPlan(
                scoreboard,
                sourceType,
                null,
                pickaxeObjective,
                shovelObjective,
                axeObjective,
                hoeObjective,
                shearsObjective);
    }

    private static Objective matchingObjective(Objective objective, String expectedTitle)
    {
        return objectiveMatches(objective, expectedTitle) ? objective : null;
    }

    private static boolean objectiveMatches(Objective objective, String expectedTitle)
    {
        if (objective == null || expectedTitle == null || expectedTitle.isBlank())
        {
            return false;
        }

        String expected = clean(expectedTitle);
        return expected.equalsIgnoreCase(clean(objective.getName()))
                || expected.equalsIgnoreCase(clean(objective.getDisplayName().getString()));
    }

    static long combineToolUsageTotals(long pickaxeUses,
                                       long shovelUses,
                                       long axeUses,
                                       long hoeUses,
                                       long shearsUses)
    {
        return Math.max(0L, pickaxeUses)
                + Math.max(0L, shovelUses)
                + Math.max(0L, axeUses)
                + Math.max(0L, hoeUses)
                + Math.max(0L, shearsUses);
    }

    static String toolUsageObjectiveTitle(long pickaxeUses,
                                          long shovelUses,
                                          long axeUses,
                                          long hoeUses,
                                          long shearsUses)
    {
        List<String> labels = new ArrayList<>();
        if (pickaxeUses > 0L) labels.add("Pickaxe Uses");
        if (shovelUses > 0L) labels.add("Shovel Uses");
        if (axeUses > 0L) labels.add("Axe Uses");
        if (hoeUses > 0L) labels.add("Hoe Uses");
        if (shearsUses > 0L) labels.add("Shears Uses");
        return labels.isEmpty() ? "" : "Combined Tool Uses: " + String.join(" + ", labels);
    }

    private static SidebarResult detectFromSidebar(
            Minecraft client,
            String username,
            String usernameLower,
            List<ScoreboardReader.ObjectiveSnapshot> objectiveSnapshots)
    {
        if (client.level == null)
        {
            return new SidebarResult(0L, "no-world", "", 0L, "");
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null)
        {
            return new SidebarResult(0L, "no-sidebar-objective", "", 0L, "");
        }

        long rawScore = readDirectScore(scoreboard, objective, client.player.getGameProfile(), username);
        String objectiveTitle = clean(objective.getDisplayName().getString());
        String rendered = findRenderedSidebarLineForUser(objectiveSnapshots, usernameLower);
        long parsedRendered = renderedMiningTotal(rendered);
        long acceptedRawScore = sanitizeDirectScore(rawScore, rendered, objectiveTitle, usernameLower, "sidebar");
        long total = Math.max(rawScore, parsedRendered);
        total = Math.max(acceptedRawScore, parsedRendered);
        if (total <= 0L)
        {
            total = Math.max(0L, fallbackFromSidebarEntries(objectiveSnapshots, usernameLower, objectiveTitle));
        }

        return new SidebarResult(
                total,
                objectiveTitle,
                username,
                acceptedRawScore,
                rendered
        );
    }

    private static TabResult detectFromTabList(Minecraft client, String username, String usernameLower)
    {
        if (client.getConnection() == null || client.level == null)
        {
            return new TabResult(0L, "no-tab-objective", "", 0L, "");
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
        long rawScore = objective == null ? 0L : readDirectScore(scoreboard, objective, client.player.getGameProfile(), username);
        String objectiveTitle = objective == null ? "no-tab-objective" : clean(objective.getDisplayName().getString());

        Collection<PlayerInfo> playerList = client.getConnection().getOnlinePlayers();
        if (playerList == null || playerList.isEmpty())
        {
            long acceptedRawScore = sanitizeDirectScore(rawScore, "", objectiveTitle, usernameLower, "tab");
            return new TabResult(acceptedRawScore, objectiveTitle, username, acceptedRawScore, "empty-tab-list");
        }

        String rendered = "";
        long parsedRendered = 0L;
        for (PlayerInfo entry : playerList)
        {
            if (entry == null || entry.getProfile() == null || entry.getProfile().name() == null)
            {
                continue;
            }

            String profileName = entry.getProfile().name();
            String display = entry.getTabListDisplayName() != null ? entry.getTabListDisplayName().getString() : profileName;
            if (display == null)
            {
                display = profileName;
            }

            if (profileName.toLowerCase(Locale.ROOT).equals(usernameLower))
            {
                rendered = clean(display);
                parsedRendered = renderedMiningTotal(rendered);
                break;
            }
        }

        long acceptedRawScore = sanitizeDirectScore(rawScore, rendered, objectiveTitle, usernameLower, "tab");
        long total = Math.max(acceptedRawScore, parsedRendered);
        return new TabResult(
                total,
                objectiveTitle,
                username,
                acceptedRawScore,
                rendered.isBlank() ? "no-tab-match" : rendered
        );
    }

    static long renderedMiningTotal(String rendered)
    {
        // Tier/name tags can contain a website total. Never treat that decoration
        // as the server's mining score unless the rendered line labels it as mining.
        return ScoreboardParser.hasMiningLabel(rendered) ? parseNumber(rendered) : 0L;
    }

    private static long parseNumber(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return 0L;
        }

        String cleaned = raw
                .replaceAll("§.", "")
                .replace('\u00A0', ' ')
                .trim();
        Matcher matcher = NUMBER_PATTERN.matcher(cleaned);
        double best = 0D;
        while (matcher.find())
        {
            String numberPart = matcher.group(1) == null ? "" : matcher.group(1);
            String suffixPart = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.ROOT);
            String normalized = numberPart.replaceAll("[,_ ]", "");
            if (normalized.isBlank())
            {
                continue;
            }

            try
            {
                double value = Double.parseDouble(normalized);
                value *= switch (suffixPart)
                {
                    case "k" -> 1_000D;
                    case "m" -> 1_000_000D;
                    case "b" -> 1_000_000_000D;
                    case "t" -> 1_000_000_000_000D;
                    default -> 1D;
                };
                if (value > best)
                {
                    best = value;
                }
            }
            catch (NumberFormatException e)
            {
                MmmDebugLogger.debug(
                        "personal-total-number-parse",
                        PARSE_DEBUG_LOG_INTERVAL_MS,
                        "[MMM_SYNC] failed to parse personal total number from '{}': {}",
                        matcher.group(),
                        e.getMessage());
            }
        }

        return best <= 0D ? 0L : (long) Math.floor(best);
    }

    private static long readDirectScore(Scoreboard scoreboard, Objective objective, GameProfile profile, String username)
    {
        if (scoreboard == null || objective == null || profile == null || username == null || username.isBlank())
        {
            return 0L;
        }
        long fromProfile = readScore(scoreboard, objective, ScoreHolder.fromGameProfile(profile));
        long fromName = readScore(scoreboard, objective, ScoreHolder.forNameOnly(username));
        return Math.max(fromProfile, fromName);
    }

    private static long readScore(Scoreboard scoreboard, Objective objective, ScoreHolder holder)
    {
        if (holder == null)
        {
            return 0L;
        }
        ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(holder, objective);
        return score == null ? 0L : Math.max(0L, score.value());
    }

    private static String findRenderedSidebarLineForUser(
            List<ScoreboardReader.ObjectiveSnapshot> objectiveSnapshots,
            String usernameLower)
    {
        for (ScoreboardReader.ObjectiveSnapshot snapshot : objectiveSnapshots)
        {
            if (snapshot.sidebar() == false)
            {
                continue;
            }
            for (ScoreboardReader.ScoreboardLine line : snapshot.lines())
            {
                String lower = line.cleaned().toLowerCase(Locale.ROOT);
                if (lower.contains(usernameLower) || line.owner().toLowerCase(Locale.ROOT).equals(usernameLower))
                {
                    return line.cleaned();
                }
            }
        }
        return "";
    }

    private static long fallbackFromSidebarEntries(
            List<ScoreboardReader.ObjectiveSnapshot> objectiveSnapshots,
            String usernameLower,
            String objectiveTitle)
    {
        long best = 0L;
        for (ScoreboardReader.ObjectiveSnapshot snapshot : objectiveSnapshots)
        {
            if (snapshot.sidebar() == false)
            {
                continue;
            }
            for (ScoreboardReader.ScoreboardLine line : snapshot.lines())
            {
                String lower = line.cleaned().toLowerCase(Locale.ROOT);
                boolean mentionsUser = lower.contains(usernameLower) || line.owner().toLowerCase(Locale.ROOT).equals(usernameLower);
                boolean personalLine = PERSONAL_MARKERS.stream().anyMatch(lower::contains) && DIG_MARKERS.stream().anyMatch(lower::contains);
                if (mentionsUser == false && personalLine == false)
                {
                    continue;
                }
                if (ScoreboardParser.isMiningEvidence(objectiveTitle) == false
                        && ScoreboardParser.hasMiningLabel(line.cleaned()) == false)
                {
                    continue;
                }

                long parsed = parseNumber(line.cleaned());
                boolean accepted = false;
                String reason = "inline-number";
                if (parsed <= 0L)
                {
                    long scoreFallback = Math.max(0L, line.scoreValue());
                    if (isLikelyRankOnlyLine(line.cleaned(), usernameLower, scoreFallback))
                    {
                        parsed = 0L;
                        reason = "rejected-rank-like-fallback";
                    }
                    else if (scoreFallback >= 1_000L || hasDigMarkers(line.cleaned()) || hasDigMarkers(objectiveTitle))
                    {
                        parsed = scoreFallback;
                        reason = "score-fallback";
                        accepted = true;
                    }
                    else
                    {
                        parsed = 0L;
                        reason = "rejected-ambiguous-score-fallback";
                    }
                }
                else
                {
                    accepted = true;
                }
                debugCandidate("sidebar-fallback", line.cleaned(), objectiveTitle, line.scoreValue(), parsed, accepted, reason);
                if (parsed > best)
                {
                    best = parsed;
                }
            }
        }
        return best;
    }

    private static long sanitizeDirectScore(long rawScore, String rendered, String objectiveTitle, String usernameLower, String detector)
    {
        if (rawScore <= 0L)
        {
            return 0L;
        }

        long parsedRendered = ScoreboardParser.isMiningEvidence(objectiveTitle) || ScoreboardParser.hasMiningLabel(rendered)
                ? parseNumber(rendered)
                : 0L;
        boolean hasDigitsInRendered = parsedRendered > 0L;
        boolean hasDigContext = ScoreboardParser.isMiningEvidence(objectiveTitle) || ScoreboardParser.hasMiningLabel(rendered);
        boolean rankLike = isLikelyRankOnlyLine(rendered, usernameLower, rawScore);
        boolean accepted = false;
        String reason = "rejected-ambiguous-direct-score";

        if (rankLike)
        {
            accepted = false;
            reason = "rejected-rank-like-line";
        }
        else if (hasDigitsInRendered && (ScoreboardParser.isMiningEvidence(objectiveTitle) || ScoreboardParser.hasMiningLabel(rendered)))
        {
            accepted = true;
            reason = "accepted-rendered-number";
        }
        else if (hasDigContext && rawScore >= 1_000L)
        {
            accepted = true;
            reason = "accepted-high-direct-score";
        }

        debugCandidate(detector + "-direct", rendered, objectiveTitle, rawScore, accepted ? rawScore : 0L, accepted, reason);
        return accepted ? rawScore : 0L;
    }

    private static boolean hasDigMarkers(String value)
    {
        String lower = clean(value).toLowerCase(Locale.ROOT);
        return DIG_MARKERS.stream().anyMatch(lower::contains);
    }

    private static boolean isLikelyRankOnlyLine(String rendered, String usernameLower, long numericValue)
    {
        String cleaned = clean(rendered);
        if (cleaned.isBlank())
        {
            return false;
        }

        String cleanedLower = cleaned.toLowerCase(Locale.ROOT);
        if (cleanedLower.equals(usernameLower) && numericValue > 0L && numericValue <= 100L)
        {
            return true;
        }

        Matcher rankMatcher = RANK_PREFIX_PATTERN.matcher(cleaned);
        if (rankMatcher.find())
        {
            String rankedUser = rankMatcher.group(2) == null ? "" : rankMatcher.group(2).toLowerCase(Locale.ROOT);
            if (rankedUser.equals(usernameLower))
            {
                return true;
            }
        }

        return false;
    }

    private static void debugCandidate(String detector, String rendered, String objectiveTitle, long rawScore, long parsedValue, boolean accepted, String reason)
    {
        if (MmmDebugLogger.shouldLog("personal-total-candidate", CANDIDATE_DEBUG_LOG_INTERVAL_MS) == false)
        {
            return;
        }

        MMM.LOGGER.info(
                "[MMM_DEBUG] personal-total-candidate detector={} objective={} rendered={} rawScore={} parsed={} decision={} reason={}",
                detector,
                clean(objectiveTitle),
                clean(rendered),
                rawScore,
                parsedValue,
                accepted ? "accepted" : "rejected",
                reason
        );
    }

    private static String clean(String value)
    {
        return value == null
                ? ""
                : value
                        .replaceAll("§.", "")
                        .replace('\u00A0', ' ')
                        .replaceAll("\\s+", " ")
                        .trim();
    }

    record Detection(
            long sidebarTotal,
            long tabTotal,
            long chosenTotal,
            String chosenSource,
            String sidebarObjectiveTitle,
            String sidebarMatchedUsername,
            long sidebarRawScore,
            String sidebarRenderedText,
            String tabObjectiveTitle,
            String tabMatchedUsername,
            long tabRawScore,
            String tabRenderedText,
            long toolUsageTotal,
            String toolUsageObjectiveTitle,
            String skipReason
    )
    {
        static Detection empty(String reason)
        {
            return new Detection(0L, 0L, 0L, "none", "", "", 0L, "", "", "", 0L, "", 0L, "", reason == null ? "" : reason);
        }
    }

    private record SidebarResult(
            long total,
            String objectiveTitle,
            String matchedUsername,
            long rawScore,
            String renderedText
    )
    {
    }

    private record TabResult(
            long total,
            String objectiveTitle,
            String matchedUsername,
            long rawScore,
            String renderedText
    )
    {
    }

    private record ToolUsageResult(long total, String objectiveTitle)
    {
    }

    record FastTotalPlan(
            Scoreboard scoreboard,
            String sourceType,
            Objective primary,
            Objective pickaxe,
            Objective shovel,
            Objective axe,
            Objective hoe,
            Objective shears)
    {
        static FastTotalPlan empty()
        {
            return new FastTotalPlan(null, "none", null, null, null, null, null, null);
        }

        static FastTotalPlan single(Scoreboard scoreboard, String sourceType, Objective primary)
        {
            return new FastTotalPlan(scoreboard, sourceType, primary, null, null, null, null, null);
        }

        boolean isUsable()
        {
            if (scoreboard == null || sourceType == null || "none".equals(sourceType))
            {
                return false;
            }
            if ("tool-uses".equals(sourceType))
            {
                return pickaxe != null || shovel != null || axe != null || hoe != null || shears != null;
            }
            return primary != null;
        }
    }
}
