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
                Text.literal("Total Digs"), ScoreboardCriterion.RenderType.INTEGER);
        var score = scoreboard.getPlayerScore(client.player.getGameProfile().getName(), objective);
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
        if (first.get(0).lines().get(0).scoreValue() != 100_001
                || second.get(0).lines().get(0).scoreValue() != 100_002)
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
        var advancements = (AdvancementsScreen) client.currentScreen;
        var root = com.mmm.advancement.MmmAdvancementTree.rootEntry();
        if (!net.minecraft.util.Identifier.of("minecraft", "textures/block/blackstone.png").equals(root.getDisplay().getBackground()))
            throw new AssertionError("MMM advancement background missing");
        for (var definition : com.mmm.advancement.MmmAdvancementDefinition.ORDERED)
        {
            var node = com.mmm.advancement.MmmAdvancementTree.entry(definition);
            if (advancements.getAdvancementWidget(node) == null || node.getCriteria().size() != 100)
                throw new AssertionError("Missing advancement or progress criteria: " + definition);
        }
        var context = new net.minecraft.client.gui.DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        advancements.render(context, 0, 0, 0);
        var screen = new com.mmm.ui.MmmSettingsScreen(null);
        client.setScreen(screen);
        screen.render(context, 0, 0, 0);
        var scores = new com.mmm.scoreboard.ScoreboardScreen(null);
        client.setScreen(scores);
        scores.render(context, 0, 0, 0);
        client.setScreen(advancements);
        SessionHistory.getLifetimeSummary();
    }

    static void prepareRendering(MinecraftClient client)
    {
        client.setScreen(null);
        com.mmm.config.Configs.Generic.BREAKING_INDICATORS.setBooleanValue(true);
        com.mmm.config.Configs.Generic.SMALL_DIG_ITEMS.setBooleanValue(true);
        client.player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.STONE));
        var pos = client.player.getBlockPos().offset(client.player.getHorizontalFacing(), 2);
        client.world.setBlockState(pos, net.minecraft.block.Blocks.STONE.getDefaultState());
        client.worldRenderer.setBlockBreakingInfo(123456, pos, 5);
        var objective = client.world.getScoreboard().addObjective("mmm_tab", ScoreboardCriterion.DUMMY,
                Text.literal("Tab Digs"), ScoreboardCriterion.RenderType.INTEGER);
        client.world.getScoreboard().getPlayerScore(client.player.getGameProfile().getName(), objective).setScore(123456);
        com.mmm.config.Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS.setBooleanValue(true);
        var context = new net.minecraft.client.gui.DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        client.inGameHud.getPlayerListHud().render(context, client.getWindow().getScaledWidth(), client.world.getScoreboard(), objective);
    }

    static void mineBlocks(MinecraftClient client)
    {
        var pos = client.player.getBlockPos().offset(client.player.getHorizontalFacing(), 2);
        for (var block : java.util.List.of(net.minecraft.block.Blocks.STONE, net.minecraft.block.Blocks.GLOWSTONE, net.minecraft.block.Blocks.OAK_LEAVES))
        {
            client.world.setBlockState(pos, block.getDefaultState());
            if (!client.interactionManager.breakBlock(pos))
                throw new AssertionError("Mining action failed for " + block);
        }
    }
}
