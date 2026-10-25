package com.mmm.feature;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Matches the legacy indicator's bounded, 150 ms completion flash, including instant breaks. */
public final class BreakIndicatorState {
    public static final long FLASH_NANOS = 150_000_000L;
    public static final int LIMIT = 64;
    private final Map<BlockPos, Pulse> pulses = new LinkedHashMap<>();

    public void complete(BlockPos pos, Box bounds, long now) {
        pulses.remove(pos);
        pulses.put(pos.toImmutable(), new Pulse(bounds, now));
        while (pulses.size() > LIMIT) pulses.remove(pulses.keySet().iterator().next());
    }

    public Map<BlockPos, Pulse> active(long now) {
        pulses.values().removeIf(pulse -> now - pulse.started() >= FLASH_NANOS);
        return pulses;
    }

    public void clear() { pulses.clear(); }

    public static Box scaledBounds(Box box, double progress) {
        double scale = Math.max(0.12, Math.min(1, Double.isFinite(progress) ? progress : 0));
        double x = (box.minX + box.maxX) / 2, y = (box.minY + box.maxY) / 2, z = (box.minZ + box.maxZ) / 2;
        double hx = box.getXLength() * scale / 2 + 0.003;
        double hy = box.getYLength() * scale / 2 + 0.003;
        double hz = box.getZLength() * scale / 2 + 0.003;
        return new Box(x - hx, y - hy, z - hz, x + hx, y + hy, z + hz);
    }

    public record Pulse(Box bounds, long started) {
        public float opacity(long now) {
            return net.minecraft.util.math.MathHelper.clamp(1 - (now - started) / (float) FLASH_NANOS, 0, 1);
        }
    }
}
