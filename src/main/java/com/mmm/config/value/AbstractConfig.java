package com.mmm.config.value;

import java.util.Objects;

public abstract class AbstractConfig<T extends AbstractConfig<T>> implements IConfigBase
{
    private final String name;
    private final String prettyName;
    private final String comment;
    private ValueChangeCallback<T> callback;
    private boolean notifying;

    protected AbstractConfig(String name, String comment)
    {
        this.name = Objects.requireNonNull(name);
        this.prettyName = splitCamelCase(name);
        this.comment = comment == null ? "" : comment;
    }

    @Override public final String getName() { return this.name; }
    @Override public final String getPrettyName() { return this.prettyName; }
    @Override public final String getComment() { return this.comment; }

    public final void setValueChangeCallback(ValueChangeCallback<T> callback)
    {
        this.callback = callback;
    }

    @SuppressWarnings("unchecked")
    protected final void notifyChanged()
    {
        if (this.callback == null || this.notifying)
        {
            return;
        }

        this.notifying = true;
        try
        {
            this.callback.onValueChanged((T) this);
        }
        finally
        {
            this.notifying = false;
        }
    }

    private static String splitCamelCase(String value)
    {
        if (value == null || value.isBlank())
        {
            return "";
        }

        String spaced = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}