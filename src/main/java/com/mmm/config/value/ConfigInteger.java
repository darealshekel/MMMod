package com.mmm.config.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ConfigInteger extends AbstractConfig<ConfigInteger> implements IConfigInteger
{
    private final int defaultValue;
    private final int minValue;
    private final int maxValue;
    private int value;

    public ConfigInteger(String name, int defaultValue, int minValue, int maxValue, String comment)
    {
        super(name, comment);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = clamp(defaultValue);
        this.value = this.defaultValue;
    }

    private int clamp(int value) { return Math.max(this.minValue, Math.min(this.maxValue, value)); }
    @Override public int getIntegerValue() { return this.value; }
    @Override public int getDefaultIntegerValue() { return this.defaultValue; }
    @Override public int getMinIntegerValue() { return this.minValue; }
    @Override public int getMaxIntegerValue() { return this.maxValue; }

    @Override
    public void setIntegerValue(int value)
    {
        int next = clamp(value);
        if (this.value != next)
        {
            this.value = next;
            notifyChanged();
        }
    }

    @Override public String getStringValue() { return Integer.toString(this.value); }
    @Override public String getDefaultStringValue() { return Integer.toString(this.defaultValue); }
    @Override public void setValueFromString(String value) { setIntegerValue(Integer.parseInt(value.trim())); }
    @Override public boolean isModified() { return this.value != this.defaultValue; }
    @Override public boolean isModified(String value) { try { return clamp(Integer.parseInt(value.trim())) != this.defaultValue; } catch (Exception ignored) { return true; } }
    @Override public void resetToDefault() { setIntegerValue(this.defaultValue); }
    @Override public JsonElement getAsJsonElement() { return new JsonPrimitive(this.value); }

    @Override
    public void setValueFromJsonElement(JsonElement element)
    {
        if (element != null && element.isJsonPrimitive())
        {
            setIntegerValue(element.getAsInt());
        }
    }
}