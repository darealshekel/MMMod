package com.mmm.timer;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import com.mmm.config.Configs;
import com.mmm.scoreboard.ScoreboardService;
import com.mmm.scoreboard.ScoreboardState;
import com.mmm.social.MmmChatIgnoreList;

public final class MmmClientCommands
{
    private MmmClientCommands()
    {
    }

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("mmm")
                    .then(buildTimerCommand())
                    .then(buildChatCommand())
                    .then(buildScoreboardCommand()));
            dispatcher.register(ClientCommandManager.literal("sbhelper")
                    .then(ClientCommandManager.literal("maxDisplayCount")
                            .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(0, 100))
                                    .executes(context -> setScoreboardRows(context,
                                            IntegerArgumentType.getInteger(context, "count"))))));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildChatCommand()
    {
        return ClientCommandManager.literal("chat")
                .then(ClientCommandManager.literal("ignore")
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                .executes(context -> ignorePlayer(context, StringArgumentType.getString(context, "player")))))
                .then(ClientCommandManager.literal("unignore")
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                .executes(context -> unignorePlayer(context, StringArgumentType.getString(context, "player")))))
                .then(ClientCommandManager.literal("ignored")
                        .executes(MmmClientCommands::showIgnoredPlayers));
    }

    private static int ignorePlayer(CommandContext<FabricClientCommandSource> context, String username)
    {
        if (!MmmChatIgnoreList.isValidUsername(username))
        {
            feedback(context, Formatting.RED, "Enter a valid Minecraft username.");
            return 0;
        }
        if (!MmmChatIgnoreList.add(username))
        {
            feedback(context, Formatting.YELLOW, username + " is already ignored.");
            return 0;
        }
        feedback(context, Formatting.GREEN, "Ignoring MMM messages from " + username + ".");
        return 1;
    }

    private static int unignorePlayer(CommandContext<FabricClientCommandSource> context, String username)
    {
        if (!MmmChatIgnoreList.remove(username))
        {
            feedback(context, Formatting.YELLOW, username + " is not ignored.");
            return 0;
        }
        feedback(context, Formatting.GREEN, "Showing MMM messages from " + username + " again.");
        return 1;
    }

    private static int showIgnoredPlayers(CommandContext<FabricClientCommandSource> context)
    {
        var ignored = MmmChatIgnoreList.entries();
        feedback(context, Formatting.YELLOW, ignored.isEmpty()
                ? "No MMM players are ignored."
                : "Ignored MMM players: " + String.join(", ", ignored));
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildScoreboardCommand()
    {
        return ClientCommandManager.literal("scoreboard")
                .then(ClientCommandManager.literal("max")
                        .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(0, 100))
                                .executes(context -> setScoreboardRows(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(ClientCommandManager.literal("pageUp")
                        .executes(context -> {
                            feedback(context, Formatting.YELLOW,
                                    ScoreboardService.pageUp() ? "Showing the previous scoreboard page." : "Already on the first page.");
                            return 1;
                        }))
                .then(ClientCommandManager.literal("pageDown")
                        .executes(context -> {
                            feedback(context, Formatting.YELLOW,
                                    ScoreboardService.pageDown() ? "Showing the next scoreboard page." : "Already on the last page.");
                            return 1;
                        }));
    }

    private static int setScoreboardRows(CommandContext<FabricClientCommandSource> context, int count)
    {
        Configs.Generic.SCOREBOARD_MAX_ENTRIES.setIntegerValue(count);
        ScoreboardState.resetPage();
        Configs.saveToFile();
        feedback(context, Formatting.GREEN, "Scoreboard rows per page set to " + count + ".");
        return count;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildTimerCommand()
    {
        LiteralArgumentBuilder<FabricClientCommandSource> timer = ClientCommandManager.literal("timer");
        LiteralArgumentBuilder<FabricClientCommandSource> startCommand = ClientCommandManager.literal("start")
                .executes(context -> start(context, null))
                .then(ClientCommandManager.argument("duration", StringArgumentType.word())
                        .executes(context -> start(context, StringArgumentType.getString(context, "duration"))));
        LiteralArgumentBuilder<FabricClientCommandSource> setCommand = ClientCommandManager.literal("set")
                .then(ClientCommandManager.argument("duration", StringArgumentType.word())
                        .executes(context -> setDuration(context, StringArgumentType.getString(context, "duration"))));

        for (int hour = 1; hour <= 24; hour++)
        {
            int capturedHour = hour;
            String literal = hour + "h";
            timer.then(ClientCommandManager.literal(literal)
                    .executes(context -> startHour(context, capturedHour)));
            startCommand.then(ClientCommandManager.literal(literal)
                    .executes(context -> startHour(context, capturedHour)));
            setCommand.then(ClientCommandManager.literal(literal)
                    .executes(context -> setHour(context, capturedHour)));
        }

        return timer
                .then(startCommand)
                .then(ClientCommandManager.literal("pause")
                        .executes(context -> {
                            if (MmmTimerState.pause())
                            {
                                feedback(context, Formatting.YELLOW, "Timer paused. Time and run stats are frozen.");
                                return 1;
                            }
                            feedback(context, Formatting.RED, "Timer is not running.");
                            return 0;
                        }))
                .then(ClientCommandManager.literal("stop")
                        .executes(context -> {
                            MmmTimerState.stop();
                            feedback(context, Formatting.RED, "Timer stopped. MMM session paused.");
                            return 1;
                        }))
                .then(ClientCommandManager.literal("reset")
                        .executes(context -> {
                            MmmTimerState.reset();
                            feedback(context, Formatting.GREEN, "Timer reset.");
                            return 1;
                        }))
                .then(setCommand)
                .then(ClientCommandManager.literal("status")
                        .executes(context -> {
                            String state = MmmTimerState.isRunning()
                                    ? "running"
                                    : MmmTimerState.isPaused() ? "paused" : MmmTimerState.isExpired() ? "expired" : "stopped";
                            feedback(context, Formatting.YELLOW,
                                    "Timer " + state
                                            + " | " + MmmTimerState.formatTime(MmmTimerState.getRemainingMs())
                                            + " left | " + MmmTimerState.getBlocksBroken() + " blocks");
                            return 1;
                        }));
    }

    private static int start(CommandContext<FabricClientCommandSource> context, String durationText)
    {
        try
        {
            Long duration = durationText == null ? null : MmmTimerState.parseDurationMs(durationText);
            boolean resuming = duration == null && MmmTimerState.isPaused();
            MmmTimerState.start(duration);
            feedback(context, Formatting.GREEN,
                    (resuming ? "Timer resumed: " : "Timer started: ")
                            + MmmTimerState.formatTime(MmmTimerState.getRemainingMs()) + ".");
            return 1;
        }
        catch (RuntimeException exception)
        {
            feedback(context, Formatting.RED, "Use a duration like 30m, 2h, or 90s. Max is 24h.");
            return 0;
        }
    }

    private static int startHour(CommandContext<FabricClientCommandSource> context, int hour)
    {
        long duration = hour * 60L * 60L * 1000L;
        MmmTimerState.start(duration);
        feedback(context, Formatting.GREEN, "Timer started for " + hour + "h.");
        return 1;
    }

    private static int setHour(CommandContext<FabricClientCommandSource> context, int hour)
    {
        long duration = hour * 60L * 60L * 1000L;
        MmmTimerState.setDuration(duration);
        feedback(context, Formatting.GREEN, "Timer duration set to " + hour + "h.");
        return 1;
    }

    private static int setDuration(CommandContext<FabricClientCommandSource> context, String durationText)
    {
        try
        {
            long duration = MmmTimerState.parseDurationMs(durationText);
            MmmTimerState.setDuration(duration);
            feedback(context, Formatting.GREEN, "Timer duration set to " + MmmTimerState.formatTime(MmmTimerState.getDurationMs()) + ".");
            return 1;
        }
        catch (RuntimeException exception)
        {
            feedback(context, Formatting.RED, "Use 1h through 24h, or a duration like 30m / 90s. Max is 24h.");
            return 0;
        }
    }

    private static void feedback(CommandContext<FabricClientCommandSource> context, Formatting color, String message)
    {
        context.getSource().sendFeedback(Text.literal("[MMM] ").formatted(color).append(Text.literal(message).formatted(Formatting.WHITE)));
    }
}
