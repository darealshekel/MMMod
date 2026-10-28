package com.mmm.config.value;

public interface IConfigBoolean extends IConfigBase
{
    boolean getBooleanValue();
    boolean getDefaultBooleanValue();
    void setBooleanValue(boolean value);

    default void toggleBooleanValue()
    {
        setBooleanValue(!getBooleanValue());
    }
}