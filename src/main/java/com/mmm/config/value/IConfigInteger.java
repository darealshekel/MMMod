package com.mmm.config.value;

public interface IConfigInteger extends IConfigBase
{
    int getIntegerValue();
    int getDefaultIntegerValue();
    int getMinIntegerValue();
    int getMaxIntegerValue();
    void setIntegerValue(int value);
}