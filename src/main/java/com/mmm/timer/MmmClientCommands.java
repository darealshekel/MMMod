package com.mmm.timer;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MmmClientCommands
{
    private MmmClientCommands()
    {
    }

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("mmm")
                        .then(buildTimerCommand())));
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
                            String state = MmmTimerState.isRunning() ? "running" : MmmTimerState.isExpired() ? "expired" : "paused";
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
            MmmTimerState.start(duration);
            feedback(context, Formatting.GREEN, "Timer started: " + MmmTimerState.formatTime(MmmTimerState.getRemainingMs()) + ".");
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
