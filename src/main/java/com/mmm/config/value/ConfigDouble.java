package com.mmm.config.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ConfigDouble extends AbstractConfig<ConfigDouble> implements IConfigDouble
{
    private final double defaultValue;
    private final double minValue;
    private final double maxValue;
    private double value;

    public ConfigDouble(String name, double defaultValue, double minValue, double maxValue, String comment)
    {
        super(name, comment);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = clamp(defaultValue);
        this.value = this.defaultValue;
    }

    private double clamp(double value) { return Math.max(this.minValue, Math.min(this.maxValue, value)); }
    @Override public double getDoubleValue() { return this.value; }
    @Override public double getDefaultDoubleValue() { return this.defaultValue; }
    @Override public double getMinDoubleValue() { return this.minValue; }
    @Override public double getMaxDoubleValue() { return this.maxValue; }

    @Override
    public void setDoubleValue(double value)
    {
        double next = clamp(value);
        if (Double.compare(this.value, next) != 0)
        {
            this.value = next;
            notifyChanged();
        }
    }

    @Override public String getStringValue() { return Double.toString(this.value); }
    @Override public String getDefaultStringValue() { return Double.toString(this.defaultValue); }
    @Override public void setValueFromString(String value) { setDoubleValue(Double.parseDouble(value.trim())); }
    @Override public boolean isModified() { return Double.compare(this.value, this.defaultValue) != 0; }
    @Override public boolean isModified(String value) { try { return Double.compare(clamp(Double.parseDouble(value.trim())), this.defaultValue) != 0; } catch (Exception ignored) { return true; } }
    @Override public void resetToDefault() { setDoubleValue(this.defaultValue); }
    @Override public JsonElement getAsJsonElement() { return new JsonPrimitive(this.value); }

    @Override
    public void setValueFromJsonElement(JsonElement element)
    {
        if (element != null && element.isJsonPrimitive())
        {
            setDoubleValue(element.getAsDouble());
        }
    }
}