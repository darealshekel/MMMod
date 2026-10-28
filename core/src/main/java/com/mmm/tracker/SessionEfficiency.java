package com.mmm.tracker;

import com.google.gson.JsonObject;

public final class SessionEfficiency
{
    public enum Activity {
        MINING("Mining", 0xFF65BF86),
        NON_INSTANT("Non-instamine mining", 0xFFE2B85A),
        MOVING("Moving, not mining", 0xFF75A4CF),
        INVENTORY("Inventory / containers", 0xFFBA91B8),
        MENU("Other menus / chat", 0xFFA7A7A7),
        OTHER("Other gameplay", 0xFF88816E);

        public final String label;
        public final int color;
        Activity(String label, int color) { this.label = label; this.color = color; }
    }

    private final long[] millis = new long[Activity.values().length];

    public long time(Activity activity) { return millis[activity.ordinal()]; }
    public long tracked() {
        long total = 0;
        for (long value : millis) total += value;
        return total;
    }
    public long untracked(long sessionIgt) { return Math.max(0, sessionIgt - tracked()); }
    public void add(Activity activity, long duration) {
        if (activity != null && duration > 0 && duration <= 250) millis[activity.ordinal()] += duration;
    }
    public JsonObject toJson() {
        var json = new JsonObject();
        for (var activity : Activity.values()) json.addProperty(activity.name(), time(activity));
        return json;
    }
    public static SessionEfficiency fromJson(JsonObject json, long duration) {
        var result = new SessionEfficiency();
        long remaining = Math.max(0, duration);
        if (json == null) return result;
        for (var activity : Activity.values()) {
            try {
                if (!json.has(activity.name())) continue;
                long value = Math.max(0, Math.min(remaining, json.get(activity.name()).getAsLong()));
                result.millis[activity.ordinal()] = value;
                remaining -= value;
            } catch (RuntimeException ignored) { }
        }
        return result;
    }

    /** The interval belongs to the preceding observation; long gaps remain untracked. */
    public static final class Sampler {
        private SessionEfficiency target;
        private long lastIgt;
        private Activity previous;
        public void sample(SessionEfficiency next, long igt, Activity activity) {
            if (target == next && igt >= lastIgt) next.add(previous, igt - lastIgt);
            target = next;
            lastIgt = igt;
            previous = activity;
        }
    }
}
