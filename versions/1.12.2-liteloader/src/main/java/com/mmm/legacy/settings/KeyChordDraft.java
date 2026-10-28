package com.mmm.legacy.settings;

import com.mmm.legacy.LegacyInput;
import java.util.*;

/** A capture transaction never mutates the binding until Apply is pressed. */
public final class KeyChordDraft {
    private final List<String> keys = new ArrayList<>(2);
    public void clear() { keys.clear(); }
    public void set(String value) {
        clear();
        for (String token : value.split("[+,]")) add(token);
    }
    public void add(String token) {
        String key = LegacyInput.normalize(token);
        if (key.isEmpty() || keys.contains(key)) return;
        if (keys.size() == 2) keys.clear();
        keys.add(key);
    }
    public String storage() { return String.join(",", keys); }
    public String label() { return keys.isEmpty() ? "Unassigned" : String.join(" + ", keys); }
    public boolean conflicts(String value) {
        Set<String> other = new HashSet<>();
        for (String token : value.split("[+,]")) { String key = LegacyInput.normalize(token); if (!key.isEmpty()) other.add(key); }
        return !keys.isEmpty() && other.equals(new HashSet<>(keys));
    }
}
