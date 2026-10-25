package com.mmm.legacy.command;

import com.mmm.config.Configs;
import com.mmm.legacy.LegacySessionScreen;
import com.mmm.legacy.collection.CollectionBookScreen;
import com.mmm.legacy.collection.LeaderboardScreen;
import com.mmm.scoreboard.ScoreboardService;
import com.mmm.scoreboard.ScoreboardState;
import com.mmm.social.MmmChatIgnoreList;
import com.mmm.timer.MmmTimerState;
import net.minecraft.client.Minecraft;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;

public final class LegacyCommands {
    private static Runnable pendingScreen;
    private LegacyCommands() {}

    public static void tick(Minecraft client) {
        Runnable action = pendingScreen;
        pendingScreen = null;
        if (action != null && client.world != null && client.player != null) action.run();
    }

    /** True means this is a local command and must never be sent to the server or MMM chat. */
    public static boolean execute(String input) {
        var command = ClientCommandParser.parse(input);
        if (command == null) return false;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return true;
        String argument = command.argument();
        switch (command.action()) {
            // Vanilla closes the chat after send. Queue screens for after that close.
            case LEADERBOARD -> pendingScreen = () -> client.openScreen(new LeaderboardScreen(null));
            case COLLECTIONS -> pendingScreen = () -> client.openScreen(new CollectionBookScreen(null));
            case EFFICIENCY -> pendingScreen = () -> client.openScreen(new LegacySessionScreen(null, null));
            case TIMER_START -> startTimer(argument);
            case TIMER_SET -> setTimer(argument);
            case TIMER_PAUSE -> feedback(MmmTimerState.pause() ? Formatting.YELLOW : Formatting.RED,
                    MmmTimerState.isPaused() ? "Timer paused. Time and run stats are frozen." : "Timer is not running.");
            case TIMER_STOP -> {
                MmmTimerState.stop();
                feedback(Formatting.RED, "Timer stopped. MMM session paused.");
            }
            case TIMER_RESET -> {
                MmmTimerState.reset();
                feedback(Formatting.GREEN, "Timer reset.");
            }
            case TIMER_STATUS -> {
                String state = MmmTimerState.isRunning() ? "running" : MmmTimerState.isPaused() ? "paused"
                        : MmmTimerState.isExpired() ? "expired" : "stopped";
                feedback(Formatting.YELLOW, "Timer " + state + " | " + MmmTimerState.formatTime(MmmTimerState.getRemainingMs())
                        + " left | " + MmmTimerState.getBlocksBroken() + " blocks");
            }
            case CHAT_IGNORE -> {
                if (!MmmChatIgnoreList.isValidUsername(argument)) feedback(Formatting.RED, "Enter a valid Minecraft username.");
                else if (!MmmChatIgnoreList.add(argument)) feedback(Formatting.YELLOW, argument + " is already ignored.");
                else feedback(Formatting.GREEN, "Ignoring MMM messages from " + argument + ".");
            }
            case CHAT_UNIGNORE -> {
                if (!MmmChatIgnoreList.remove(argument)) feedback(Formatting.YELLOW, argument + " is not ignored.");
                else feedback(Formatting.GREEN, "Showing MMM messages from " + argument + " again.");
            }
            case CHAT_IGNORED -> {
                var ignored = MmmChatIgnoreList.entries();
                feedback(Formatting.YELLOW, ignored.isEmpty() ? "No MMM players are ignored."
                        : "Ignored MMM players: " + String.join(", ", ignored));
            }
            case SCOREBOARD_MAX -> setScoreboardRows(argument);
            case SCOREBOARD_UP -> feedback(Formatting.YELLOW, ScoreboardService.pageUp()
                    ? "Showing the previous scoreboard page." : "Already on the first page.");
            case SCOREBOARD_DOWN -> feedback(Formatting.YELLOW, ScoreboardService.pageDown()
                    ? "Showing the next scoreboard page." : "Already on the last page.");
            case HELP -> feedback(Formatting.YELLOW, "/mmm leaderboard | collections | efficiency | timer start [duration], pause, stop, reset, set <duration>, status"
                    + " | chat ignore <player>, unignore <player>, ignored | scoreboard max <0-100>, pageUp, pageDown");
        }
        return true;
    }

    private static void startTimer(String text) {
        try {
            Long duration = text.isEmpty() ? null : MmmTimerState.parseDurationMs(text);
            boolean resuming = duration == null && MmmTimerState.isPaused();
            MmmTimerState.start(duration);
            feedback(Formatting.GREEN, (resuming ? "Timer resumed: " : "Timer started: ")
                    + MmmTimerState.formatTime(MmmTimerState.getRemainingMs()) + ".");
        } catch (IllegalArgumentException exception) {
            feedback(Formatting.RED, "Use a duration like 30m, 2h, or 90s. Max is 24h.");
        }
    }

    private static void setTimer(String text) {
        try {
            MmmTimerState.setDuration(MmmTimerState.parseDurationMs(text));
            feedback(Formatting.GREEN, "Timer duration set to " + MmmTimerState.formatTime(MmmTimerState.getDurationMs()) + ".");
        } catch (IllegalArgumentException exception) {
            feedback(Formatting.RED, "Use 1h through 24h, or a duration like 30m / 90s. Max is 24h.");
        }
    }

    private static void setScoreboardRows(String text) {
        try {
            int count = Integer.parseInt(text);
            if (count < 0 || count > 100) throw new NumberFormatException();
            Configs.Generic.SCOREBOARD_MAX_ENTRIES.setIntegerValue(count);
            ScoreboardState.resetPage();
            Configs.saveToFile();
            feedback(Formatting.GREEN, "Scoreboard rows per page set to " + count + ".");
        } catch (NumberFormatException exception) {
            feedback(Formatting.RED, "Choose a scoreboard row count from 0 to 100.");
        }
    }

    private static void feedback(Formatting color, String text) {
        Minecraft.getInstance().player.addMessage(new LiteralText(color + "[MMM] " + Formatting.WHITE + text), false);
    }
}
