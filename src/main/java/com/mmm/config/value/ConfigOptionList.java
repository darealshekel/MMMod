package com.mmm.config.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ConfigOptionList extends AbstractConfig<ConfigOptionList>
{
    private final IConfigOptionListEntry defaultValue;
    private IConfigOptionListEntry value;

    public ConfigOptionList(String name, IConfigOptionListEntry defaultValue, String comment)
    {
        super(name, comment);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public IConfigOptionListEntry getOptionListValue()
    {
        return this.value;
    }

    public void setOptionListValue(IConfigOptionListEntry value)
    {
        if (value != null && this.value != value)
        {
            this.value = value;
            notifyChanged();
        }
    }

    @Override public String getStringValue() { return this.value.getStringValue(); }
    @Override public String getDefaultStringValue() { return this.defaultValue.getStringValue(); }
    @Override public void setValueFromString(String value) { setOptionListValue(this.defaultValue.fromString(value)); }
    @Override public boolean isModified() { return !getStringValue().equals(getDefaultStringValue()); }
    @Override public boolean isModified(String value) { return !getDefaultStringValue().equalsIgnoreCase(value); }
    @Override public void resetToDefault() { setOptionListValue(this.defaultValue); }
    @Override public JsonElement getAsJsonElement() { return new JsonPrimitive(getStringValue()); }

    @Override
    public void setValueFromJsonElement(JsonElement element)
    {
        if (element != null && element.isJsonPrimitive())
        {
            setValueFromString(element.getAsString());
        }
    }
}