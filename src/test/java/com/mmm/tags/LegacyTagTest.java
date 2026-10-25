package com.mmm.tags;

import com.mmm.config.Configs;
import com.mmm.legacy.social.LegacySocialHooks;
import com.mmm.legacy.social.LegacySocialText;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyTagTest {
    private boolean enabled;
    @BeforeEach void setup() throws Exception {
        enabled = Configs.Generic.TIER_NAME_TAGS.getBooleanValue();
        Configs.Generic.TIER_NAME_TAGS.setBooleanValue(true);
        seed("tags", Map.of("offlineminer", new PlayerTagData("OfflineMiner", 123_456, 0xFFAA00)));
        seed("knownNames", Map.of("offlineminer", "OfflineMiner"));
    }
    @AfterEach void clear() { TierTagManager.clear(); Configs.Generic.TIER_NAME_TAGS.setBooleanValue(enabled); }

    @Test void discoversOfflineOwnersInSidebarWithoutChangingLiveScores() {
        Scoreboard board = new Scoreboard();
        var objective = board.createObjective("digs", ScoreboardCriterion.DUMMY);
        board.setDisplayObjective(1, objective);
        board.getScore("OfflineMiner", objective).set(10);
        board.getScore("#hidden", objective).set(999);
        Map<String, String> names = new LinkedHashMap<>();
        TierTagManager.addSidebarNames(names, board, "LocalPlayer");
        assertEquals(Map.of("offlineminer", "OfflineMiner"), names);
        assertEquals("123.5k | OfflineMiner", Formatting.strip(TierTagManager.decorateNameString("OfflineMiner", "OfflineMiner")));
        board.getScore("OfflineMiner", objective).set(11);
        assertEquals(11, board.getScore("OfflineMiner", objective).get());
        assertEquals("OfflineMiner", Team.getMemberDisplayName(null, "OfflineMiner"));
    }

    @Test void discoversTeamSpecificSidebarBeforeDefault() {
        Scoreboard board = new Scoreboard();
        var fallback = board.createObjective("fallback", ScoreboardCriterion.DUMMY);
        var colored = board.createObjective("red", ScoreboardCriterion.DUMMY);
        board.setDisplayObjective(1, fallback);
        board.setDisplayObjective(3 + Formatting.RED.getId(), colored);
        var team = board.addTeam("red");
        team.setColor(Formatting.RED);
        board.addMemberToTeam("LocalPlayer", "red");
        board.getScore("Fallback", fallback).set(1);
        board.getScore("OfflineMiner", colored).set(2);
        Map<String, String> names = new LinkedHashMap<>();
        TierTagManager.addSidebarNames(names, board, "LocalPlayer");
        assertEquals(Map.of("offlineminer", "OfflineMiner"), names);
    }

    @Test void copiesStylesAndDecoratesOnlyTheChatSender() {
        var sender = new LiteralText("[VIP] OfflineMiner");
        sender.getStyle().setColor(Formatting.BLUE).setItalic(true);
        var body = new LiteralText("OfflineMiner scored 19");
        var original = new TranslatableText("chat.type.text", sender, body);
        var result = (TranslatableText) LegacySocialHooks.decorateChat(original);
        assertEquals("123.5k | OfflineMiner", ((net.minecraft.text.Text) result.getArgs()[0]).getString());
        assertSame(body, result.getArgs()[1]);
        assertEquals(Formatting.BLUE, sender.getStyle().getColor());
        assertSame(sender, original.getArgs()[0]);
        assertSame(body, LegacySocialHooks.decorateChat(body));
    }

    @Test void mapsRgbToLegacyPaletteAndHonorsDisabledSetting() {
        assertEquals(Formatting.GOLD, LegacySocialText.nearestColor(0xFFB300));
        Configs.Generic.TIER_NAME_TAGS.setBooleanValue(false);
        assertNull(TierTagManager.decorateName("OfflineMiner", new LiteralText("OfflineMiner")));
    }

    private static void seed(String name, Object value) throws Exception {
        var field = TierTagManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
