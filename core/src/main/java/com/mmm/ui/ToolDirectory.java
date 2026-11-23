package com.mmm.ui;

/** Player-facing tool names and hints shared by the version-specific screen launchers. */
public enum ToolDirectory {
    HOTKEYS("Hotkeys", "View and change your MMM key bindings."),
    PROJECTS("Projects", "Manage your mining projects."),
    PROFILE("Profile", "View your mining profile and records."),
    HISTORY("History", "Browse previous mining sessions."),
    SUMMARY("Summary", "Inspect a mining session at a glance."),
    SCOREBOARD("Scoreboard", "Configure your scoreboard display."),
    LEADERBOARDS("Leaderboards", "Compare mining totals and placements.");

    public final String label;
    public final String description;

    ToolDirectory(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public static ToolDirectory find(String label) {
        for (ToolDirectory tool : values()) if (tool.label.equals(label)) return tool;
        return null;
    }
}
