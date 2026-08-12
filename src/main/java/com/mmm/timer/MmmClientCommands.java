package com.mmm.timer;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.mmm.config.Configs;
import com.mmm.scoreboard.ScoreboardService;
import com.mmm.scoreboard.ScoreboardState;

public final class MmmClientCommands
{
    private MmmClientCommands()
    {
    }

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("mmm")
                    .then(buildTimerCommand())
                    .then(buildScoreboardCommand()));
            dispatcher.register(ClientCommands.literal("sbhelper")
                    .then(ClientCommands.literal("maxDisplayCount")
                            .then(ClientCommands.argument("count", IntegerArgumentType.integer(0, 100))
                                    .executes(context -> setScoreboardRows(context,
                                            IntegerArgumentType.getInteger(context, "count"))))));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildScoreboardCommand()
    {
        return ClientCommands.literal("scoreboard")
                .then(ClientCommands.literal("max")
                        .then(ClientCommands.argument("count", IntegerArgumentType.integer(0, 100))
                                .executes(context -> setScoreboardRows(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(ClientCommands.literal("pageUp")
                        .executes(context -> {
                            feedback(context, ChatFormatting.YELLOW,
                                    ScoreboardService.pageUp() ? "Showing the previous scoreboard page." : "Already on the first page.");
                            return 1;
                        }))
                .then(ClientCommands.literal("pageDown")
                        .executes(context -> {
                            feedback(context, ChatFormatting.YELLOW,
                                    ScoreboardService.pageDown() ? "Showing the next scoreboard page." : "Already on the last page.");
                            return 1;
                        }));
    }

    private static int setScoreboardRows(CommandContext<FabricClientCommandSource> context, int count)
    {
        Configs.Generic.SCOREBOARD_MAX_ENTRIES.setIntegerValue(count);
        ScoreboardState.resetPage();
        Configs.saveToFile();
        feedback(context, ChatFormatting.GREEN, "Scoreboard rows per page set to " + count + ".");
        return count;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildTimerCommand()
    {
        LiteralArgumentBuilder<FabricClientCommandSource> timer = ClientCommands.literal("timer");
        LiteralArgumentBuilder<FabricClientCommandSource> startCommand = ClientCommands.literal("start")
                .executes(context -> start(context, null))
                .then(ClientCommands.argument("duration", StringArgumentType.word())
                        .executes(context -> start(context, StringArgumentType.getString(context, "duration"))));
        LiteralArgumentBuilder<FabricClientCommandSource> setCommand = ClientCommands.literal("set")
                .then(ClientCommands.argument("duration", StringArgumentType.word())
                        .executes(context -> setDuration(context, StringArgumentType.getString(context, "duration"))));

        for (int hour = 1; hour <= 24; hour++)
        {
            int capturedHour = hour;
            String literal = hour + "h";
            timer.then(ClientCommands.literal(literal)
                    .executes(context -> startHour(context, capturedHour)));
            startCommand.then(ClientCommands.literal(literal)
                    .executes(context -> startHour(context, capturedHour)));
            setCommand.then(ClientCommands.literal(literal)
                    .executes(context -> setHour(context, capturedHour)));
        }

        return timer
                .then(startCommand)
                .then(ClientCommands.literal("pause")
                        .executes(context -> {
                            if (MmmTimerState.pause())
                            {
                                feedback(context, ChatFormatting.YELLOW, "Timer paused. Time and run stats are frozen.");
                                return 1;
                            }
                            feedback(context, ChatFormatting.RED, "Timer is not running.");
                            return 0;
                        }))
                .then(ClientCommands.literal("stop")
                        .executes(context -> {
                            MmmTimerState.stop();
                            feedback(context, ChatFormatting.RED, "Timer stopped. MMM session paused.");
                            return 1;
                        }))
                .then(ClientCommands.literal("reset")
                        .executes(context -> {
                            MmmTimerState.reset();
                            feedback(context, ChatFormatting.GREEN, "Timer reset.");
                            return 1;
                        }))
                .then(setCommand)
                .then(ClientCommands.literal("status")
                        .executes(context -> {
                            String state = MmmTimerState.isRunning()
                                    ? "running"
                                    : MmmTimerState.isPaused() ? "paused" : MmmTimerState.isExpired() ? "expired" : "stopped";
                            feedback(context, ChatFormatting.YELLOW,
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
            feedback(context, ChatFormatting.GREEN,
                    (resuming ? "Timer resumed: " : "Timer started: ")
                            + MmmTimerState.formatTime(MmmTimerState.getRemainingMs()) + ".");
            return 1;
        }
        catch (RuntimeException exception)
        {
            feedback(context, ChatFormatting.RED, "Use a duration like 30m, 2h, or 90s. Max is 24h.");
            return 0;
        }
    }

    private static int startHour(CommandContext<FabricClientCommandSource> context, int hour)
    {
        long duration = hour * 60L * 60L * 1000L;
        MmmTimerState.start(duration);
        feedback(context, ChatFormatting.GREEN, "Timer started for " + hour + "h.");
        return 1;
    }

    private static int setHour(CommandContext<FabricClientCommandSource> context, int hour)
    {
        long duration = hour * 60L * 60L * 1000L;
        MmmTimerState.setDuration(duration);
        feedback(context, ChatFormatting.GREEN, "Timer duration set to " + hour + "h.");
        return 1;
    }

    private static int setDuration(CommandContext<FabricClientCommandSource> context, String durationText)
    {
        try
        {
            long duration = MmmTimerState.parseDurationMs(durationText);
            MmmTimerState.setDuration(duration);
            feedback(context, ChatFormatting.GREEN, "Timer duration set to " + MmmTimerState.formatTime(MmmTimerState.getDurationMs()) + ".");
            return 1;
        }
        catch (RuntimeException exception)
        {
            feedback(context, ChatFormatting.RED, "Use 1h through 24h, or a duration like 30m / 90s. Max is 24h.");
            return 0;
        }
    }

    private static void feedback(CommandContext<FabricClientCommandSource> context, ChatFormatting color, String message)
    {
        context.getSource().sendFeedback(Component.literal("[MMM] ").withStyle(color).append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
    }
}
