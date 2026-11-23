package com.mmm.ui;

final class WebsiteLauncher {
    private WebsiteLauncher() {}
    static void open(String url) { org.lwjgl.sdl.SDLMisc.SDL_OpenURL(url); }
}
