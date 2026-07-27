package com.mmm.config.value;

@FunctionalInterface
public interface ValueChangeCallback<T>
{
    void onValueChanged(T value);
}