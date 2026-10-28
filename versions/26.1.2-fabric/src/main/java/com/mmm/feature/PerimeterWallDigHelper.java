package com.mmm.feature;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;

public final class PerimeterWallDigHelper
{
    private static final ArrayList<Block> OUTLINE_BLOCKS = new ArrayList<>();

    private PerimeterWallDigHelper()
    {
    }

    public static boolean isPositionDisallowed(BlockPos pos)
    {
        if (!FeatureToggle.MMM_PERIMETER_WALL_DIG_HELPER.getBooleanValue())
        {
            return false;
        }

        ClientLevel world = Minecraft.getInstance().level;
        if (world == null)
        {
            return false;
        }

        BlockPos surfacePos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos).below();
        return OUTLINE_BLOCKS.contains(world.getBlockState(surfacePos).getBlock());
    }

    public static void setOutlineBlocks(List<String> blocks)
    {
        OUTLINE_BLOCKS.clear();

        for (String name : blocks)
        {
            Block block = getBlockFromName(name);
            if (block != null)
            {
                OUTLINE_BLOCKS.add(block);
            }
        }
    }

    public static void refreshFromConfig()
    {
        setOutlineBlocks(Configs.Generic.PERIMETER_OUTLINE_BLOCKS_LIST.getStrings());
    }

    private static Block getBlockFromName(String name)
    {
        try
        {
            Identifier identifier = Identifier.parse(name);
            return BuiltInRegistries.BLOCK.containsKey(identifier) ? BuiltInRegistries.BLOCK.getValue(identifier) : null;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }
}
