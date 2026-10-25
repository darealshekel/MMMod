package com.mmm.hud;

public enum HudModuleId
{
    MAIN("Main HUD"),
    TIMER("Timer"),
    BLOCK_STATS("Block Stats"),
    NOTIFICATION("Notification");

    private final String label;

    HudModuleId(String label)
    {
        this.label = label;
    }

    public String label()
    {
        return this.label;
    }
}
