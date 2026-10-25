package com.mmm.scoreboard;

import java.util.*;

/** Legacy command parsing has no quoted scoreboard tokens; reject selectors and whitespace. */
public final class ScoreboardCommands {
    private ScoreboardCommands() {}
    public static List<String> changes(String objective, Map<String, Integer> before, Map<String, Integer> after,
                                       Map<String, Integer> current) {
        Set<String> owners = new HashSet<>(before.keySet());
        owners.addAll(after.keySet());
        for (String owner : owners)
            if (!Objects.equals(before.get(owner), after.get(owner)) && !Objects.equals(before.get(owner), current.get(owner)))
                throw new IllegalArgumentException("Score changed on the server: " + owner);
        return changes(objective, before, after);
    }
    public static List<String> changes(String objective, Map<String, Integer> before, Map<String, Integer> after) {
        if (!validToken(objective)) throw new IllegalArgumentException("Objective name is not editable in 1.12");
        List<String> commands = new ArrayList<>();
        for (var entry : before.entrySet()) if (!after.containsKey(entry.getKey())) {
            requireOwner(entry.getKey());
            commands.add("/scoreboard players reset " + entry.getKey() + " " + objective);
        }
        for (var entry : after.entrySet()) if (!Objects.equals(before.get(entry.getKey()), entry.getValue())) {
            requireOwner(entry.getKey());
            if (entry.getValue() == null) throw new IllegalArgumentException("Missing score");
            commands.add("/scoreboard players set " + entry.getKey() + " " + objective + " " + entry.getValue());
        }
        return commands;
    }
    private static boolean validToken(String name) { return name != null && name.matches("[A-Za-z0-9_+.-]+"); }
    private static void requireOwner(String name) {
        if (!validToken(name) || name.length() > 40) throw new IllegalArgumentException("Invalid scoreboard owner: " + name);
    }
}
