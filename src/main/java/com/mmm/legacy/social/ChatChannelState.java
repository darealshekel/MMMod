package com.mmm.legacy.social;

public final class ChatChannelState {
    private boolean publicChannel;

    public boolean isPublicChannel() { return publicChannel; }
    public void select(boolean value) { publicChannel = value; }
    public void initialize(String initialText) {
        if (initialText != null && !initialText.trim().isEmpty()) publicChannel = false;
    }
    public void close() { publicChannel = false; }
}
