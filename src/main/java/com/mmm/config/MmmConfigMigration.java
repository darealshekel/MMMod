package com.mmm.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class MmmConfigMigration
{
    static final String HOTKEYS_SECTION = "MmmFeatureHotkeys";
    static final String TOGGLES_SECTION = "MmmFeatureToggles";

    private static final String LEGACY_HOTKEYS_SECTION = "TweakHotkeys";
    private static final String LEGACY_TOGGLES_SECTION = "TweakToggles";
    private static final String[] SETTINGS_SECTIONS = {
            "Generic",
            "GenericHotkeys",
            HOTKEYS_SECTION,
            TOGGLES_SECTION
    };

    private MmmConfigMigration()
    {
    }

    static JsonObject parseJsonObject(String content)
    {
        if (content == null || content.isBlank())
        {
            return null;
        }
        try
        {
            JsonElement parsed = JsonParser.parseString(content);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    static void migrateLegacyFeatureToggleSections(JsonObject root)
    {
        migrateSection(root, LEGACY_HOTKEYS_SECTION, HOTKEYS_SECTION);
        migrateSection(root, LEGACY_TOGGLES_SECTION, TOGGLES_SECTION);
    }

    static boolean containsSettings(JsonObject root)
    {
        return hasObject(root, "Generic")
                || hasObject(root, "GenericHotkeys")
                || hasCurrentFeatureToggleSections(root)
                || hasLegacyFeatureToggleSections(root);
    }

    static boolean hasCurrentFeatureToggleSections(JsonObject root)
    {
        return hasObject(root, HOTKEYS_SECTION) || hasObject(root, TOGGLES_SECTION);
    }

    static boolean hasLegacyFeatureToggleSections(JsonObject root)
    {
        return hasObject(root, LEGACY_HOTKEYS_SECTION) || hasObject(root, LEGACY_TOGGLES_SECTION);
    }

    static JsonObject mergeMissingValues(JsonObject currentRoot, JsonObject legacyRoot)
    {
        JsonObject merged = currentRoot == null ? new JsonObject() : currentRoot.deepCopy();
        JsonObject legacy = legacyRoot == null ? new JsonObject() : legacyRoot.deepCopy();
        migrateLegacyFeatureToggleSections(merged);
        migrateLegacyFeatureToggleSections(legacy);

        for (String sectionName : SETTINGS_SECTIONS)
        {
            mergeObjectSection(merged, legacy, sectionName);
        }
        mergeObjectSection(merged, legacy, "State");
        return merged;
    }

    private static boolean hasObject(JsonObject root, String name)
    {
        return root != null
                && root.has(name)
                && root.get(name).isJsonObject()
                && root.getAsJsonObject(name).size() > 0;
    }

    private static void mergeObjectSection(JsonObject targetRoot, JsonObject fallbackRoot, String sectionName)
    {
        if (fallbackRoot.has(sectionName) == false || fallbackRoot.get(sectionName).isJsonObject() == false)
        {
            return;
        }

        JsonObject target = targetRoot.has(sectionName) && targetRoot.get(sectionName).isJsonObject()
                ? targetRoot.getAsJsonObject(sectionName)
                : new JsonObject();
        JsonObject fallback = fallbackRoot.getAsJsonObject(sectionName);
        for (var entry : fallback.entrySet())
        {
            if (target.has(entry.getKey()) == false)
            {
                target.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        targetRoot.add(sectionName, target);
    }

    private static void migrateSection(JsonObject root, String legacySectionName, String sectionName)
    {
        JsonObject migrated = root.has(sectionName) && root.get(sectionName).isJsonObject()
                ? root.getAsJsonObject(sectionName)
                : new JsonObject();

        if (root.has(legacySectionName) && root.get(legacySectionName).isJsonObject())
        {
            JsonObject legacy = root.getAsJsonObject(legacySectionName);
            for (FeatureToggle toggle : FeatureToggle.VALUES)
            {
                if (migrated.has(toggle.getName()) == false)
                {
                    JsonElement value = legacy.get(toggle.getLegacyConfigName());
                    if (value != null)
                    {
                        migrated.add(toggle.getName(), value.deepCopy());
                    }
                }
            }
        }

        root.add(sectionName, migrated);
    }
}