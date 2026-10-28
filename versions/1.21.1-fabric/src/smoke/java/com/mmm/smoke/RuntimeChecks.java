package com.mmm.smoke;

import com.mmm.storage.SessionHistory;
import com.mmm.sync.DigsSyncManager;
import com.mmm.sync.ScoreboardReader;
import com.mmm.sync.SyncScoreboardSelector;
import com.mmm.tracker.MiningStats;
import java.lang.reflect.Field;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.MixinEnvironment;

final class RuntimeChecks
{
    static void run(MinecraftClient client) throws Exception
    {
        MixinEnvironment.getCurrentEnvironment().audit();
        var scoreboard = client.world.getScoreboard();
        var objective = scoreboard.addObjective("mmm_test_digs", ScoreboardCriterion.DUMMY,
                Text.literal("Total Digs"), ScoreboardCriterion.RenderType.INTEGER, false, null);
        var holder = ScoreHolder.fromProfile(client.player.getGameProfile());
        var score = scoreboard.getOrCreateScore(holder, objective);
        score.setScore(100_000);
        SyncScoreboardSelector.selectObjective(objective.getName());
        long now = System.currentTimeMillis();
        DigsSyncManager.onClientTick(now);
        if (MiningStats.getCurrentSourceTotalMined() != 100_000)
            throw new AssertionError("Selected scoreboard total was not applied");
        score.setScore(100_001);
        DigsSyncManager.onClientTick(now + 50);
        if (MiningStats.getCurrentSourceTotalMined() != 100_001)
            throw new AssertionError("Live scoreboard total was delayed");
        var first = ScoreboardReader.readObjectives(client);
        score.setScore(100_002);
        var second = ScoreboardReader.readObjectives(client);
        if (first.getFirst().lines().getFirst().scoreValue() != 100_001
                || second.getFirst().lines().getFirst().scoreValue() != 100_002)
            throw new AssertionError("Scoreboard snapshot returned an old value");
        scoreboard.removeObjective(objective);
        if (!ScoreboardReader.readObjectives(client).isEmpty())
            throw new AssertionError("Removed scoreboard remained cached");
        SyncScoreboardSelector.selectObjective("");

        ChatScreen chat = new ChatScreen("");
        client.setScreen(chat);
        if (!(chat.getFocused() instanceof TextFieldWidget input))
            throw new AssertionError("Chat input is not focused");
        chat.charTyped('a', 0);
        if (!input.getText().equals("a")) throw new AssertionError("Vanilla chat typing failed");
        int tabX = input.getX() + client.textRenderer.getWidth("MINECRAFT") + 14;
        chat.mouseClicked(tabX, Math.max(1, input.getY() - 16) + 5, 0);
        Field channel = java.util.Arrays.stream(ChatScreen.class.getDeclaredFields())
                .filter(field -> field.getName().endsWith("mmm$publicChannelSelected")).findFirst().orElseThrow();
        channel.setAccessible(true);
        if (!channel.getBoolean(null)) throw new AssertionError("MMM tab did not select");
        chat.charTyped('b', 0);
        if (!input.getText().equals("ab")) throw new AssertionError("MMM chat typing failed");
        client.setScreen(null);
        client.setScreen(new ChatScreen(""));
        if (channel.getBoolean(null)) throw new AssertionError("Chat channel did not reset");
        client.setScreen(new AdvancementsScreen(client.getNetworkHandler().getAdvancementHandler()));
        SessionHistory.getLifetimeSummary();
        checkRankOvertakeHud(client);
    }

    private static void checkRankOvertakeHud(MinecraftClient client) throws Exception
    {
        var type = com.mmm.sync.RankOvertakeClient.class;
        Field name = type.getDeclaredField("username");
        Field refresh = type.getDeclaredField("nextRefresh");
        Field estimate = type.getDeclaredField("websitePlayer");
        Field progress = type.getDeclaredField("progress");
        Field rows = type.getDeclaredField("rows");
        Field hudUpdate = type.getDeclaredField("nextHudUpdate");
        name.setAccessible(true);
        refresh.setAccessible(true);
        estimate.setAccessible(true);
        progress.setAccessible(true);
        rows.setAccessible(true);
        hudUpdate.setAccessible(true);
        com.mmm.config.Configs.Generic.HUD_RANK_OVERTAKE_VISIBLE.setBooleanValue(true);
        try
        {
            name.set(null, client.getSession().getUsername());
            refresh.setLong(null, Long.MAX_VALUE);
            estimate.set(null, new com.mmm.sync.RankOvertakeEstimate.Player(name.get(null).toString(), 48, 100));
            progress.set(null, new com.mmm.sync.RankLocalProgress(100, 0));
            @SuppressWarnings("unchecked")
            var targets = (java.util.Map<Integer, com.mmm.sync.RankOvertakeEstimate.Player>) rows.get(null);
            targets.put(47, new com.mmm.sync.RankOvertakeEstimate.Player("Target47", 47, 100));
            targets.put(46, new com.mmm.sync.RankOvertakeEstimate.Player("Target46", 46, 200));
            hudUpdate.setLong(null, 0);
            com.mmm.sync.RankOvertakeClient.onClientTick(client);
            var lines = com.mmm.sync.RankOvertakeClient.hudLines();
            if (lines.size() != 4 || !lines.get(1).contains("#47 Target47"))
                throw new AssertionError("Rank overtake HUD missing target");
            com.mmm.sync.RankOvertakeClient.onAcceptedBlock();
            hudUpdate.setLong(null, 0);
            com.mmm.sync.RankOvertakeClient.onClientTick(client);
            if (!com.mmm.sync.RankOvertakeClient.hudLines().get(1).contains("#46 Target46"))
                throw new AssertionError("Rank estimate did not advance after local mining");
            var build = com.mmm.hud.MiningHudRenderer.class.getDeclaredMethod("buildHudLines", boolean.class, boolean.class);
            build.setAccessible(true);
            if (build.invoke(null, false, false).toString().contains("Estimated Rank") == false)
                throw new AssertionError("Rank estimate not included in HUD");
        }
        finally
        {
            com.mmm.config.Configs.Generic.HUD_RANK_OVERTAKE_VISIBLE.setBooleanValue(false);
            com.mmm.sync.RankOvertakeClient.onClientTick(client);
        }
    }
}
