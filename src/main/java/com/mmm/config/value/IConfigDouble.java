package com.mmm.config.value;

public interface IConfigDouble extends IConfigBase
{
    double getDoubleValue();
    double getDefaultDoubleValue();
    double getMinDoubleValue();
    double getMaxDoubleValue();
    void setDoubleValue(double value);
}