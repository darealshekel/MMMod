package com.mmm.hud;

public enum HudModuleId
{
    MAIN("Main HUD"),
    TIMER("Timer"),
    HOURLY("Hourly Stats"),
    BLOCK_STATS("Block Stats"),
    ACTIVE_DIGGERS("Active Diggers"),
    MINED_BLOCKS("Collection Overlay"),
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
