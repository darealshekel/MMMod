package com.mmm.hotkey;

import com.mmm.legacy.LegacyInput;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class MmmHotkey {
    private final String name;
    private final String prettyName;
    private final String defaultStorageString;
    private final String comment;
    private String storageString;
    private List<String> tokens;
    private Runnable callback = () -> {};
    private boolean wasDown;
    private final Map<String, Boolean> previousKeyStates = new HashMap<>();

    public MmmHotkey(String name, String defaults, String comment) {
        this(name, null, defaults, comment);
    }

    public MmmHotkey(String name, String prettyName, String defaults, String comment) {
        this.name = Objects.requireNonNull(name);
        this.prettyName = prettyName == null || prettyName.isBlank() ? splitCamelCase(name) : prettyName;
        this.defaultStorageString = defaults == null ? "" : defaults;
        this.comment = comment == null ? "" : comment;
        setStorageString(this.defaultStorageString);
    }

    public String getName() { return name; }
    public String getPrettyName() { return prettyName; }
    public String getComment() { return comment; }
    public String getStorageString() { return storageString; }
    public String getDefaultStorageString() { return defaultStorageString; }
    public void setStorageString(String value) {
        storageString = value == null ? "" : value.trim();
        tokens = Arrays.stream(storageString.split("[+,]")).map(LegacyInput::normalize)
                .filter(token -> !token.isEmpty()).distinct().toList();
        resetRuntimeState();
    }
    public void resetToDefault() { setStorageString(defaultStorageString); }
    public boolean isModified() { return !storageString.equalsIgnoreCase(defaultStorageString); }
    public void setCallback(Runnable value) { callback = value == null ? () -> {} : value; }

    public void tick(Minecraft client) {
        if (client == null) { resetRuntimeState(); return; }
        poll(client.screen != null, LegacyInput::isDown);
    }

    void poll(boolean screenOpen, Predicate<String> pressed) {
        if (tokens.isEmpty()) { resetRuntimeState(); return; }
        boolean allDown = true;
        String finalKey = finalActionKey();
        boolean actionEdge = false;
        for (String token : tokens) {
            boolean down = pressed.test(token);
            allDown &= down;
            if (token.equals(finalKey)) actionEdge = down && !previousKeyStates.getOrDefault(token, false);
            previousKeyStates.put(token, down);
        }
        boolean trigger = finalKey == null ? allDown && !wasDown : actionEdge;
        if (!screenOpen && allDown && trigger) callback.run();
        wasDown = allDown;
    }

    public void onKeyEvent(Minecraft client, int keyCode, int scanCode, int action) {
        if (client == null) return;
        onInput(LegacyInput.keyName(keyCode), action, client.screen != null, LegacyInput::isDown);
    }

    void onInput(String token, int action, boolean screenOpen, Predicate<String> pressed) {
        token = LegacyInput.normalize(token);
        if (!tokens.contains(token)) return;
        if (action == 0) { wasDown = false; previousKeyStates.put(token, false); return; }
        if (action != 1 || screenOpen) return;
        String finalKey = finalActionKey();
        if (finalKey != null && !finalKey.equals(token)) return;
        boolean allDown = true;
        for (String required : tokens) {
            boolean down = required.equals(token) || pressed.test(required);
            previousKeyStates.put(required, down);
            allDown &= down;
        }
        if (allDown && !wasDown) { wasDown = true; callback.run(); }
    }

    private String finalActionKey() {
        String result = null;
        for (String token : tokens) if (!isModifier(token)) result = token;
        return result;
    }
    private static boolean isModifier(String token) {
        return switch (token) {
            case "LEFT_ALT", "RIGHT_ALT", "LEFT_CONTROL", "RIGHT_CONTROL",
                 "LEFT_SHIFT", "RIGHT_SHIFT", "LEFT_SUPER", "RIGHT_SUPER" -> true;
            default -> false;
        };
    }
    private void resetRuntimeState() { wasDown = false; previousKeyStates.clear(); }
    private static String splitCamelCase(String value) {
        if (value.isEmpty()) return value;
        String spaced = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
