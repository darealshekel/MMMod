package com.mmm.hotkey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class MmmHotkey
{
    private final String name;
    private final String prettyName;
    private final String defaultStorageString;
    private final String comment;
    private String storageString;
    private Runnable callback = () -> {};
    private boolean wasDown;
    private final Map<String, Boolean> previousKeyStates = new HashMap<>();

    public MmmHotkey(String name, String defaultStorageString, String comment)
    {
        this(name, null, defaultStorageString, comment);
    }

    public MmmHotkey(String name, String prettyName, String defaultStorageString, String comment)
    {
        this.name = Objects.requireNonNull(name);
        this.prettyName = prettyName == null || prettyName.isBlank() ? splitCamelCase(this.name) : prettyName;
        this.defaultStorageString = defaultStorageString == null ? "" : defaultStorageString;
        this.storageString = this.defaultStorageString;
        this.comment = comment == null ? "" : comment;
    }

    public String getName() { return this.name; }
    public String getPrettyName() { return this.prettyName; }
    public String getComment() { return this.comment; }
    public String getStorageString() { return this.storageString; }
    public String getDefaultStorageString() { return this.defaultStorageString; }

    public void setStorageString(String value)
    {
        this.storageString = value == null ? "" : value.trim();
        resetRuntimeState();
    }

    public void resetToDefault()
    {
        setStorageString(this.defaultStorageString);
    }

    public boolean isModified()
    {
        return !this.storageString.equalsIgnoreCase(this.defaultStorageString);
    }

    public void setCallback(Runnable callback)
    {
        this.callback = callback == null ? () -> {} : callback;
    }

    public void tick(MinecraftClient client)
    {
        if (this.storageString.isBlank() || client == null || client.getWindow() == null)
        {
            resetRuntimeState();
            return;
        }

        List<String> tokens = getTokens();
        if (tokens.isEmpty())
        {
            resetRuntimeState();
            return;
        }

        boolean allRequiredKeysDown = true;
        String finalActionKey = null;
        boolean finalActionNewlyPressed = false;
        for (String token : tokens)
        {
            InputKey key = parseKey(token);
            boolean pressed = key != null && key.pressed().getAsBoolean();
            allRequiredKeysDown &= pressed;
            if (!isModifier(token))
            {
                finalActionKey = token;
                finalActionNewlyPressed = pressed && !this.previousKeyStates.getOrDefault(token, false);
            }
            this.previousKeyStates.put(token, pressed);
        }

        boolean triggerEdge = finalActionKey == null
                ? allRequiredKeysDown && !this.wasDown
                : finalActionNewlyPressed;
        if (client.currentScreen == null && allRequiredKeysDown && triggerEdge)
        {
            this.callback.run();
        }

        this.wasDown = allRequiredKeysDown;
        this.previousKeyStates.keySet().retainAll(tokens);
    }

    public void onKeyEvent(MinecraftClient client, int keyCode, int scanCode, int action)
    {
        if (this.storageString.isBlank() || client == null || client.getWindow() == null)
        {
            return;
        }

        List<String> tokens = getTokens();
        if (tokens.isEmpty())
        {
            return;
        }

        String eventToken = keyName(keyCode, scanCode);
        if (eventToken == null || tokens.contains(eventToken) == false)
        {
            return;
        }

        if (action == GLFW.GLFW_RELEASE)
        {
            this.wasDown = false;
            this.previousKeyStates.put(eventToken, false);
            return;
        }
        if (action != GLFW.GLFW_PRESS || client.currentScreen != null)
        {
            return;
        }

        String finalActionKey = finalActionKey(tokens);
        if (finalActionKey != null && finalActionKey.equals(eventToken) == false)
        {
            return;
        }

        boolean allRequiredKeysDown = true;
        for (String token : tokens)
        {
            InputKey key = parseKey(token);
            boolean pressed = token.equals(eventToken) || key != null && key.pressed().getAsBoolean();
            this.previousKeyStates.put(token, pressed);
            allRequiredKeysDown &= pressed;
        }

        if (allRequiredKeysDown && this.wasDown == false)
        {
            this.wasDown = true;
            this.callback.run();
        }
    }

    private List<String> getTokens()    {
        List<String> tokens = new ArrayList<>();
        for (String raw : this.storageString.split("[+,]"))
        {
            String token = normalizeToken(raw);
            if (!token.isEmpty())
            {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private void resetRuntimeState()
    {
        this.wasDown = false;
        this.previousKeyStates.clear();
    }

    private static String normalizeToken(String raw)
    {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return value.startsWith("KEY_") ? value.substring(4) : value;
    }

    private static boolean isModifier(String token)
    {
        return switch (normalizeToken(token))
        {
            case "LEFT_ALT", "LALT", "RIGHT_ALT", "RALT",
                 "LEFT_CONTROL", "LEFT_CTRL", "LCTRL",
                 "RIGHT_CONTROL", "RIGHT_CTRL", "RCTRL",
                 "LEFT_SHIFT", "LSHIFT", "RIGHT_SHIFT", "RSHIFT" -> true;
            default -> false;
        };
    }

    private static String finalActionKey(List<String> tokens)
    {
        String actionKey = null;
        for (String token : tokens)
        {
            if (isModifier(token) == false)
            {
                actionKey = token;
            }
        }
        return actionKey;
    }

    private static String keyName(int keyCode, int scanCode)
    {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN)
        {
            return null;
        }
        String translation = InputUtil.fromKeyCode(keyCode, scanCode).getTranslationKey();
        if (translation.startsWith("key.keyboard."))
        {
            translation = translation.substring("key.keyboard.".length());
        }
        return normalizeToken(translation.replace('.', '_'));
    }

    private InputKey parseKey(String raw)    {
        String value = normalizeToken(raw);
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        if (value.startsWith("MOUSE_"))
        {
            try
            {
                int button = Integer.parseInt(value.substring(6)) - 1;
                return new InputKey(() -> GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS);
            }
            catch (NumberFormatException ignored)
            {
                return null;
            }
        }

        String translation = switch (value)
        {
            case "LEFT_ALT", "LALT" -> "key.keyboard.left.alt";
            case "RIGHT_ALT", "RALT" -> "key.keyboard.right.alt";
            case "LEFT_CONTROL", "LEFT_CTRL", "LCTRL" -> "key.keyboard.left.control";
            case "RIGHT_CONTROL", "RIGHT_CTRL", "RCTRL" -> "key.keyboard.right.control";
            case "LEFT_SHIFT", "LSHIFT" -> "key.keyboard.left.shift";
            case "RIGHT_SHIFT", "RSHIFT" -> "key.keyboard.right.shift";
            case "PAGE_UP" -> "key.keyboard.page.up";
            case "PAGE_DOWN" -> "key.keyboard.page.down";
            case "BACKSLASH" -> "key.keyboard.backslash";
            case "SPACE" -> "key.keyboard.space";
            case "TAB" -> "key.keyboard.tab";
            case "ESCAPE", "ESC" -> "key.keyboard.escape";
            case "ENTER" -> "key.keyboard.enter";
            case "DELETE" -> "key.keyboard.delete";
            case "HOME" -> "key.keyboard.home";
            case "END" -> "key.keyboard.end";
            case "UP" -> "key.keyboard.up";
            case "DOWN" -> "key.keyboard.down";
            case "LEFT" -> "key.keyboard.left";
            case "RIGHT" -> "key.keyboard.right";
            default -> value.length() == 1
                    ? "key.keyboard." + value.toLowerCase(Locale.ROOT)
                    : "key.keyboard." + value.toLowerCase(Locale.ROOT).replace('_', '.');
        };

        try
        {
            int code = InputUtil.fromTranslationKey(translation).getCode();
            return new InputKey(() -> InputUtil.isKeyPressed(window, code));
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static String splitCamelCase(String value)
    {
        String spaced = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private record InputKey(BooleanSupplier pressed)
    {
    }
}