package com.mmm.feature;

import com.mmm.config.FeatureToggle;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

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
        if (client.player == null || client.player.isSneaking())
        {
            return false;
        }

        int playerFeetY = net.minecraft.util.math.MathHelper.floor(client.player.y);
        return pos.getY() < playerFeetY;
    }
}
