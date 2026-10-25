package com.mmm.legacy;

import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Keeps stored key names stable across GLFW and LWJGL 2 clients. */
public final class LegacyInput {
    private static final Map<String, Integer> KEY_CODES = buildKeyCodes();
    private LegacyInput() {}

    public static String normalize(String token) {
        String value = token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
        if (value.startsWith("KEY_")) value = value.substring(4);
        return switch (value) {
            case "LALT", "LMENU" -> "LEFT_ALT";
            case "RALT", "RMENU" -> "RIGHT_ALT";
            case "LCTRL", "LCONTROL", "LEFT_CTRL" -> "LEFT_CONTROL";
            case "RCTRL", "RCONTROL", "RIGHT_CTRL" -> "RIGHT_CONTROL";
            case "LSHIFT" -> "LEFT_SHIFT";
            case "RSHIFT" -> "RIGHT_SHIFT";
            case "LMETA", "LWIN" -> "LEFT_SUPER";
            case "RMETA", "RWIN" -> "RIGHT_SUPER";
            case "ESC" -> "ESCAPE";
            case "RETURN" -> "ENTER";
            case "BACK" -> "BACKSPACE";
            case "PRIOR" -> "PAGE_UP";
            case "NEXT" -> "PAGE_DOWN";
            case "CAPITAL" -> "CAPS_LOCK";
            case "NUMLOCK" -> "NUM_LOCK";
            case "SCROLL" -> "SCROLL_LOCK";
            case "SYSRQ" -> "PRINT_SCREEN";
            case "LBRACKET" -> "LEFT_BRACKET";
            case "RBRACKET" -> "RIGHT_BRACKET";
            case "GRAVE" -> "GRAVE_ACCENT";
            case "APPS" -> "MENU";
            case "NUMPADENTER" -> "KP_ENTER";
            case "ADD" -> "KP_ADD";
            case "SUBTRACT" -> "KP_SUBTRACT";
            case "MULTIPLY" -> "KP_MULTIPLY";
            case "DIVIDE" -> "KP_DIVIDE";
            case "DECIMAL" -> "KP_DECIMAL";
            case "NUMPADEQUALS" -> "KP_EQUAL";
            default -> value.matches("NUMPAD[0-9]") ? "KP_" + value.charAt(6) : value;
        };
    }

    public static String keyName(int code) {
        return code <= Keyboard.KEY_NONE || code >= Keyboard.KEYBOARD_SIZE ? "" : normalize(Keyboard.getKeyName(code));
    }

    public static int keyCode(String token) {
        return KEY_CODES.getOrDefault(normalize(token), Keyboard.KEY_NONE);
    }

    private static Map<String, Integer> buildKeyCodes() {
        Map<String, Integer> codes = new HashMap<>();
        for (int code = 1; code < Keyboard.KEYBOARD_SIZE; code++) {
            String name = keyName(code);
            if (!name.isEmpty()) codes.putIfAbsent(name, code);
        }
        return Map.copyOf(codes);
    }

    public static boolean isDown(String token) {
        String value = normalize(token);
        if (value.startsWith("MOUSE_")) {
            try {
                int button = Integer.parseInt(value.substring(6)) - 1;
                return Mouse.isCreated() && button >= 0 && button < Mouse.getButtonCount() && Mouse.isButtonDown(button);
            } catch (NumberFormatException invalidButton) { return false; }
        }
        int code = keyCode(value);
        return Keyboard.isCreated() && code != Keyboard.KEY_NONE && Keyboard.isKeyDown(code);
    }
}
