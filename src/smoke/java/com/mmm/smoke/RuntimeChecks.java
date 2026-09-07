package com.mmm.smoke;

import com.mmm.storage.SessionHistory;
import com.mmm.config.Configs;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
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
    static void prepareRendering(MinecraftClient client)
    {
        if (RenderLayer.getDebugQuads().getDrawMode() != VertexFormat.DrawMode.QUADS)
            throw new AssertionError("Breaking indicator requires independent quad faces");
        client.setScreen(null);
        Configs.Generic.BREAKING_INDICATORS.setBooleanValue(true);
        Configs.Generic.SMALL_DIG_ITEMS.setBooleanValue(true);
        client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
        var pos = client.player.getBlockPos().offset(client.player.getHorizontalFacing(), 2);
        client.world.setBlockState(pos, Blocks.STONE.getDefaultState());
        client.worldRenderer.setBlockBreakingInfo(123456, pos, 5);
    }

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
    }
}
