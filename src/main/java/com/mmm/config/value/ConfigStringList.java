package com.mmm.config.value;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;

public final class ConfigStringList extends AbstractConfig<ConfigStringList>
{
    private final List<String> defaultValue;
    private List<String> value;

    public ConfigStringList(String name, List<String> defaultValue, String comment)
    {
        super(name, comment);
        this.defaultValue = List.copyOf(defaultValue);
        this.value = new ArrayList<>(defaultValue);
    }

    public List<String> getStrings()
    {
        return List.copyOf(this.value);
    }

    public void setStrings(List<String> values)
    {
        List<String> next = values == null ? List.of() : List.copyOf(values);
        if (!this.value.equals(next))
        {
            this.value = new ArrayList<>(next);
            notifyChanged();
        }
    }

    @Override public String getStringValue() { return String.join(",", this.value); }
    @Override public String getDefaultStringValue() { return String.join(",", this.defaultValue); }
    @Override public void setValueFromString(String value) { setStrings(value == null || value.isBlank() ? List.of() : List.of(value.split(","))); }
    @Override public boolean isModified() { return !this.value.equals(this.defaultValue); }
    @Override public boolean isModified(String value) { return !getDefaultStringValue().equals(value); }
    @Override public void resetToDefault() { setStrings(this.defaultValue); }

    @Override
    public JsonElement getAsJsonElement()
    {
        JsonArray array = new JsonArray();
        this.value.forEach(array::add);
        return array;
    }

    @Override
    public void setValueFromJsonElement(JsonElement element)
    {
        if (element == null || !element.isJsonArray())
        {
            return;
        }

        List<String> values = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray())
        {
            if (item.isJsonPrimitive())
            {
                values.add(item.getAsString());
            }
        }
        setStrings(values);
    }
}