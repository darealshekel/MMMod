package com.mmm.config.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ConfigBoolean extends AbstractConfig<ConfigBoolean> implements IConfigBoolean
{
    private final boolean defaultValue;
    private boolean value;

    public ConfigBoolean(String name, boolean defaultValue, String comment)
    {
        super(name, comment);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    @Override public boolean getBooleanValue() { return this.value; }
    @Override public boolean getDefaultBooleanValue() { return this.defaultValue; }

    @Override
    public void setBooleanValue(boolean value)
    {
        if (this.value != value)
        {
            this.value = value;
            notifyChanged();
        }
    }

    @Override public String getStringValue() { return Boolean.toString(this.value); }
    @Override public String getDefaultStringValue() { return Boolean.toString(this.defaultValue); }
    @Override public void setValueFromString(String value) { setBooleanValue(Boolean.parseBoolean(value)); }
    @Override public boolean isModified() { return this.value != this.defaultValue; }
    @Override public boolean isModified(String value) { return Boolean.parseBoolean(value) != this.defaultValue; }
    @Override public void resetToDefault() { setBooleanValue(this.defaultValue); }
    @Override public JsonElement getAsJsonElement() { return new JsonPrimitive(this.value); }

    @Override
    public void setValueFromJsonElement(JsonElement element)
    {
        if (element != null && element.isJsonPrimitive())
        {
            setBooleanValue(element.getAsBoolean());
        }
    }
}