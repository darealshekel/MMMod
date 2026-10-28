package com.mmm.config.value;

public interface IConfigOptionListEntry
{
    String getStringValue();
    String getDisplayName();
    IConfigOptionListEntry cycle(boolean forward);
    IConfigOptionListEntry fromString(String value);
}