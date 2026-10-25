package com.mmm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mmm.config.value.ConfigBoolean;
import com.mmm.config.value.ConfigDouble;
import com.mmm.config.value.ConfigInteger;
import com.mmm.hotkey.HotkeyChordCapture;
import com.mmm.hotkey.MmmHotkey;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MmmOwnedConfigTest
{
    @Test
    void roundTripsOwnedConfigValuesWithoutExternalConfigLibrary()
    {
        ConfigBoolean enabled = new ConfigBoolean("enabled", false, "Enabled");
        ConfigInteger count = new ConfigInteger("count", 4, 0, 10, "Count");
        ConfigDouble scale = new ConfigDouble("scale", 1.0D, 0.25D, 2.0D, "Scale");
        enabled.setBooleanValue(true);
        count.setIntegerValue(9);
        scale.setDoubleValue(1.5D);

        JsonObject root = new JsonObject();
        MmmConfigIO.writeConfigBase(root, "Generic", List.of(enabled, count, scale));

        ConfigBoolean loadedEnabled = new ConfigBoolean("enabled", false, "Enabled");
        ConfigInteger loadedCount = new ConfigInteger("count", 4, 0, 10, "Count");
        ConfigDouble loadedScale = new ConfigDouble("scale", 1.0D, 0.25D, 2.0D, "Scale");
        MmmConfigIO.readConfigBase(root, "Generic", List.of(loadedEnabled, loadedCount, loadedScale));

        assertTrue(loadedEnabled.getBooleanValue());
        assertEquals(9, loadedCount.getIntegerValue());
        assertEquals(1.5D, loadedScale.getDoubleValue());
    }

    @Test
    void clampsMalformedNumericValuesAndKeepsOtherSettingsReadable()
    {
        JsonObject root = new JsonObject();
        JsonObject generic = new JsonObject();
        generic.addProperty("count", 500);
        generic.addProperty("enabled", true);
        root.add("Generic", generic);
        ConfigInteger count = new ConfigInteger("count", 4, 0, 10, "Count");
        ConfigBoolean enabled = new ConfigBoolean("enabled", false, "Enabled");

        MmmConfigIO.readConfigBase(root, "Generic", List.of(count, enabled));

        assertEquals(10, count.getIntegerValue());
        assertTrue(enabled.getBooleanValue());
    }

    @Test
    void roundTripsHotkeysAndFeatureToggleState()
    {
        MmmHotkey hotkey = new MmmHotkey("testHotkey", "X,V", "Test");
        hotkey.setStorageString("LEFT_ALT,H");
        FeatureToggle toggle = FeatureToggle.MMM_BLOCK_ESP;
        boolean originalValue = toggle.getBooleanValue();
        String originalHotkey = toggle.getHotkey().getStorageString();

        try
        {
            toggle.setBooleanValue(true);
            toggle.getHotkey().setStorageString("B");
            JsonObject root = new JsonObject();
            MmmConfigIO.writeHotkeys(root, "GenericHotkeys", List.of(hotkey));
            MmmConfigIO.writeHotkeyToggleOptions(root, "FeatureHotkeys", "FeatureToggles", List.of(toggle));

            hotkey.setStorageString("");
            toggle.setBooleanValue(false);
            toggle.getHotkey().setStorageString("");
            MmmConfigIO.readHotkeys(root, "GenericHotkeys", List.of(hotkey));
            MmmConfigIO.readHotkeyToggleOptions(root, "FeatureHotkeys", "FeatureToggles", List.of(toggle));

            assertEquals("LEFT_ALT,H", hotkey.getStorageString());
            assertTrue(toggle.getBooleanValue());
            assertEquals("B", toggle.getHotkey().getStorageString());
        }
        finally
        {
            toggle.setBooleanValue(originalValue);
            toggle.getHotkey().setStorageString(originalHotkey);
        }
    }

    @Test
    void capturesTwoKeyHotkeyChordsInPressOrder()
    {
        HotkeyChordCapture capture = new HotkeyChordCapture();
        capture.press("X");
        capture.press("V");
        capture.press("V");

        assertEquals("X,V", capture.storageString());
        HotkeyChordCapture reverseCapture = new HotkeyChordCapture();
        reverseCapture.press("V");
        reverseCapture.press("X");
        assertEquals("V,X", reverseCapture.storageString());
        assertEquals("X,V", Hotkeys.OPEN_CONFIG_GUI.getDefaultStorageString());
    }

    @Test
    void callbacksOnlyRunWhenAValueActuallyChanges()
    {
        ConfigBoolean enabled = new ConfigBoolean("enabled", false, "Enabled");
        AtomicInteger changes = new AtomicInteger();
        enabled.setValueChangeCallback(config -> changes.incrementAndGet());

        enabled.setBooleanValue(false);
        enabled.setBooleanValue(true);
        enabled.setBooleanValue(true);
        enabled.resetToDefault();

        assertEquals(2, changes.get());
        assertFalse(enabled.getBooleanValue());
    }
}