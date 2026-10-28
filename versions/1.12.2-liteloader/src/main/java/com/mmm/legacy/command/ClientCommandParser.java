package com.mmm.legacy.command;

import java.util.ArrayList;
import java.util.List;

/** The 1.12 client has no Brigadier dispatcher; keep parsing independent of game state. */
public final class ClientCommandParser {
    public enum Action {
        LEADERBOARD, COLLECTIONS, EFFICIENCY, TIMER_START, TIMER_SET, TIMER_PAUSE,
        TIMER_STOP, TIMER_RESET, TIMER_STATUS, CHAT_IGNORE, CHAT_UNIGNORE, CHAT_IGNORED,
        SCOREBOARD_MAX, SCOREBOARD_UP, SCOREBOARD_DOWN, HELP
    }

    public record Command(Action action, String argument) {}

    private ClientCommandParser() {}

    public static Command parse(String input) {
        if (input == null) return null;
        String[] words = input.trim().split("\\s+");
        if (!words[0].equals("/mmm") && !words[0].equals("/sbhelper")) return null;
        if (words[0].equals("/sbhelper")) {
            return words.length == 3 && words[1].equals("maxDisplayCount")
                    ? new Command(Action.SCOREBOARD_MAX, words[2]) : help();
        }
        if (words.length < 2) return help();
        if (words.length == 2) {
            return switch (words[1]) {
                case "leaderboard" -> new Command(Action.LEADERBOARD, "");
                case "collections" -> new Command(Action.COLLECTIONS, "");
                case "efficiency" -> new Command(Action.EFFICIENCY, "");
                default -> help();
            };
        }
        String argument = words.length == 4 ? words[3] : "";
        if (words.length > 4) return help();
        return switch (words[1]) {
            case "timer" -> timer(words[2], argument, words.length);
            case "chat" -> switch (words[2]) {
                case "ignore" -> words.length == 4 ? new Command(Action.CHAT_IGNORE, argument) : help();
                case "unignore" -> words.length == 4 ? new Command(Action.CHAT_UNIGNORE, argument) : help();
                case "ignored" -> words.length == 3 ? new Command(Action.CHAT_IGNORED, "") : help();
                default -> help();
            };
            case "scoreboard" -> switch (words[2]) {
                case "max" -> words.length == 4 ? new Command(Action.SCOREBOARD_MAX, argument) : help();
                case "pageUp" -> words.length == 3 ? new Command(Action.SCOREBOARD_UP, "") : help();
                case "pageDown" -> words.length == 3 ? new Command(Action.SCOREBOARD_DOWN, "") : help();
                default -> help();
            };
            default -> help();
        };
    }

    private static Command timer(String verb, String argument, int length) {
        if (verb.equals("start")) return new Command(Action.TIMER_START, argument);
        if (verb.equals("set")) return length == 4 ? new Command(Action.TIMER_SET, argument) : help();
        if (length != 3) return help();
        return switch (verb) {
            case "pause" -> new Command(Action.TIMER_PAUSE, "");
            case "stop" -> new Command(Action.TIMER_STOP, "");
            case "reset" -> new Command(Action.TIMER_RESET, "");
            case "status" -> new Command(Action.TIMER_STATUS, "");
            default -> verb.matches("(?:[1-9]|1[0-9]|2[0-4])h")
                    ? new Command(Action.TIMER_START, verb) : help();
        };
    }

    private static Command help() { return new Command(Action.HELP, ""); }

    public static List<String> suggestions(String input) {
        if (input == null) return List.of();
        int lastSpace = input.lastIndexOf(' ');
        String prefix = lastSpace < 0 ? "" : input.substring(0, lastSpace + 1);
        String token = input.substring(lastSpace + 1);
        List<String> options = switch (prefix) {
            case "" -> List.of("/mmm", "/sbhelper");
            case "/mmm " -> List.of("leaderboard", "collections", "efficiency", "timer", "chat", "scoreboard");
            case "/mmm timer " -> List.of("start", "pause", "stop", "reset", "set", "status", "1h", "2h", "3h");
            case "/mmm timer start ", "/mmm timer set " -> hourSuggestions();
            case "/mmm chat " -> List.of("ignore", "unignore", "ignored");
            case "/mmm scoreboard " -> List.of("max", "pageUp", "pageDown");
            case "/sbhelper " -> List.of("maxDisplayCount");
            default -> List.of();
        };
        return options.stream().filter(option -> option.startsWith(token)).toList();
    }

    private static List<String> hourSuggestions() {
        List<String> hours = new ArrayList<>();
        for (int hour = 1; hour <= 24; hour++) hours.add(hour + "h");
        return hours;
    }
}
