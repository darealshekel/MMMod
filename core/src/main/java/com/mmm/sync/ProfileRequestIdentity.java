package com.mmm.sync;

/** All access is on the client thread. Each identity gets its own request generation. */
final class ProfileRequestIdentity {
    private String identity = "";
    private long generation;
    private long lastAttempt;
    private boolean pending;

    long begin(String key, long now, boolean force) {
        if (!identity.equals(key)) {
            identity = key;
            generation++;
            pending = false;
            lastAttempt = 0;
        }
        if (pending || (!force && lastAttempt != 0 && now - lastAttempt < 1800000L)) return 0;
        pending = true;
        lastAttempt = now;
        return ++generation;
    }

    boolean finish(long ticket, String currentIdentity) {
        if (ticket != generation) return false;
        pending = false;
        return identity.equals(currentIdentity);
    }
}
