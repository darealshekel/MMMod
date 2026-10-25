package com.mmm.feature;

import com.mmm.config.FeatureToggle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class FlatDigger
{
    private FlatDigger()
    {
    }

    public static boolean shouldBlock(BlockPos pos)
    {
        if (!FeatureToggle.MMM_FLAT_DIGGER.getBooleanValue())
        {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.isShiftKeyDown())
        {
            return false;
        }

        int playerFeetY = client.player.blockPosition().getY();
        return pos.getY() < playerFeetY;
    }
}
