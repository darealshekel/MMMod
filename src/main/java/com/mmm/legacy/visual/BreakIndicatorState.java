package com.mmm.legacy.visual;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Bounded completion pulses also cover blocks which finish inside a single client tick. */
public final class BreakIndicatorState {
    public static final long FLASH_NANOS = 150_000_000L;
    public static final int LIMIT = 64;
    private final Map<BlockPos, Pulse> pulses = new LinkedHashMap<>();
    public void complete(BlockPos pos, Box bounds, long now) {
        pulses.remove(pos);
        pulses.put(pos.immutable(), new Pulse(bounds, now));
        while (pulses.size() > LIMIT) pulses.remove(pulses.keySet().iterator().next());
    }
    public Map<BlockPos, Pulse> active(long now) {
        Iterator<Pulse> iterator = pulses.values().iterator();
        while (iterator.hasNext()) if (now - iterator.next().started >= FLASH_NANOS) iterator.remove();
        return pulses;
    }
    public void clear() { pulses.clear(); }
    public static Box scaledBounds(Box box, double progress) {
        double scale = Math.max(0.12, Math.min(1, Double.isFinite(progress) ? progress : 0));
        double x = (box.minX + box.maxX) / 2, y = (box.minY + box.maxY) / 2, z = (box.minZ + box.maxZ) / 2;
        double hx = (box.maxX - box.minX) * scale / 2 + 0.003;
        double hy = (box.maxY - box.minY) * scale / 2 + 0.003;
        double hz = (box.maxZ - box.minZ) * scale / 2 + 0.003;
        return new Box(x - hx, y - hy, z - hz, x + hx, y + hy, z + hz);
    }
    public record Pulse(Box bounds, long started) {
        public float opacity(long now) { return Math.max(0, Math.min(1, 1 - (now - started) / (float) FLASH_NANOS)); }
    }
}

