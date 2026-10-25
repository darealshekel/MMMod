package com.mmm.config.value;

public interface IConfigResettable
{
    boolean isModified();
    boolean isModified(String value);
    void resetToDefault();
}