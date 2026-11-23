package com.mmm.ui;

final class WebsiteLauncher {
    private WebsiteLauncher() {}
    static void open(String url) { net.minecraft.util.Util.getPlatform().openUri(url); }
}
