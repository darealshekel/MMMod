package com.mmm.config.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ConfigColor extends AbstractConfig<ConfigColor>
{
    private final String defaultValue;
    private String value;

    public ConfigColor(String name, String defaultValue, String comment)
    {
        super(name, comment);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    @Override public String getStringValue() { return this.value; }
    @Override public String getDefaultStringValue() { return this.defaultValue; }

    @Override
    public void setValueFromString(String value)
    {
        String next = value == null ? this.defaultValue : value.trim();
        if (!this.value.equals(next))
        {
            this.value = next;
            notifyChanged();
        }
    }

    @Override public boolean isModified() { return !this.value.equals(this.defaultValue); }
    @Override public boolean isModified(String value) { return !this.defaultValue.equals(value); }
    @Override public void resetToDefault() { setValueFromString(this.defaultValue); }
    @Override public JsonElement getAsJsonElement() { return new JsonPrimitive(this.value); }

    @Override
    public void setValueFromJsonElement(JsonElement element)
    {
        if (element != null && element.isJsonPrimitive())
        {
            setValueFromString(element.getAsString());
        }
    }
}