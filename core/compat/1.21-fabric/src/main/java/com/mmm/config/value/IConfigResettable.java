package com.mmm.config.value;

public interface IConfigResettable
{
    boolean isModified();
    void resetToDefault();
}