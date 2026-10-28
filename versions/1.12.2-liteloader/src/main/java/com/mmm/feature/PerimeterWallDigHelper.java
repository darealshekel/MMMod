package com.mmm.feature;

import java.util.HashSet;
import java.util.Set;
import java.util.List;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.Identifier;
import net.minecraft.block.state.BlockState;
import com.mmm.legacy.LegacyBlockIds;
import net.minecraft.util.math.BlockPos;

public final class PerimeterWallDigHelper
{
    private static final Set<String> OUTLINE_BLOCKS = new HashSet<>();

    private PerimeterWallDigHelper()
    {
    }

    public static boolean isPositionDisallowed(BlockPos pos)
    {
        if (!FeatureToggle.MMM_PERIMETER_WALL_DIG_HELPER.getBooleanValue())
        {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        ClientWorld world = client.world;
        if (client.player == null || client.player.isSneaking() || world == null)
        {
            return false;
        }

        BlockPos surfacePos = world.getHeight(pos).down();
        BlockState state = world.getBlockState(surfacePos);
        return OUTLINE_BLOCKS.contains(LegacyBlockIds.canonicalId(state))
                || OUTLINE_BLOCKS.contains(Block.REGISTRY.getKey(state.getBlock()).toString());
    }

    public static void setOutlineBlocks(List<String> blocks)
    {
        OUTLINE_BLOCKS.clear();

        for (String name : blocks)
        {
            try
            {
                if (name != null && !name.isBlank()) OUTLINE_BLOCKS.add(new Identifier(name.trim()).toString());
            }
            catch (IllegalArgumentException invalidIdentifier) { /* Ignore malformed saved entries. */ }
        }
    }

    public static void refreshFromConfig()
    {
        setOutlineBlocks(Configs.Generic.PERIMETER_OUTLINE_BLOCKS_LIST.getStrings());
    }

}
