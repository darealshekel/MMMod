package com.mmm.legacy.social;

import com.mmm.legacy.command.ClientCommandParser;
import java.util.List;

public final class LocalChatCompletion {
    private List<String> options = List.of();
    private String before = "", after = "", previous;
    private int index, cursor;

    public static boolean isLocal(String input) {
        return input != null && (input.equals("/mmm") || input.startsWith("/mmm ")
                || input.equals("/sbhelper") || input.startsWith("/sbhelper "));
    }

    public String complete(String input, int caret, boolean reverse) {
        if (!isLocal(input)) { reset(); return null; }
        if (!input.equals(previous) || caret != cursor) {
            caret = Math.max(0, Math.min(caret, input.length()));
            String prefix = input.substring(0, caret);
            options = ClientCommandParser.suggestions(prefix);
            before = prefix.substring(0, prefix.lastIndexOf(' ') + 1);
            after = input.substring(caret);
            index = reverse ? options.size() : -1;
        }
        if (options.isEmpty()) return null;
        index = Math.floorMod(index + (reverse ? -1 : 1), options.size());
        String completed = before + options.get(index);
        cursor = completed.length();
        previous = completed + after;
        return previous;
    }

    public int cursor() { return cursor; }
    public void reset() { previous = null; options = List.of(); }
}
