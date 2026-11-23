package com.mmm.platform;

import net.minecraft.text.ClickEvent;

public final class ClientTextActions {
    private ClientTextActions() {}

    public static ClickEvent runCommand(String command) {
        return new ClickEvent.RunCommand(command);
    }
}
