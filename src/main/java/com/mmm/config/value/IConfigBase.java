package com.mmm.config.value;

import com.google.gson.JsonElement;

public interface IConfigBase extends IConfigResettable, IStringRepresentable
{
    String getName();
    String getPrettyName();
    String getComment();
    String getDefaultStringValue();
    JsonElement getAsJsonElement();
    void setValueFromJsonElement(JsonElement element);
}