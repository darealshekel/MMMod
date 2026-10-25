package com.mmm.feature;

import com.mmm.config.Configs;
import java.lang.reflect.Field;
import net.minecraft.Bootstrap;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.client.resource.model.ModelTransformations.Type;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SmallDigItemRendererTest {
    @BeforeAll static void registry() throws Exception {
        Field initialized = Bootstrap.class.getDeclaredField("initialized");
        initialized.setAccessible(true);
        if (!initialized.getBoolean(null)) {
            initialized.setBoolean(null, true);
            net.minecraft.sound.SoundEvent.init();
            Block.init();
            net.minecraft.entity.living.effect.StatusEffect.init();
            net.minecraft.enchantment.Enchantment.init();
            Item.init();
        }
    }
    @BeforeEach void config() { Configs.Generic.SMALL_DIG_ITEMS.setBooleanValue(true); Configs.Generic.SMALL_DIG_ITEM_SCALE.setDoubleValue(0.2); }
    @AfterEach void reset() { Configs.Generic.SMALL_DIG_ITEMS.resetToDefault(); Configs.Generic.SMALL_DIG_ITEM_SCALE.resetToDefault(); }
    @Test void toolsIncludingHoesAndShearsStayFullSizeInEveryContext() {
        for (Item item : new Item[] {Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_AXE, Items.DIAMOND_HOE, Items.SHEARS}) {
            var stack = new ItemStack(item);
            assertTrue(SmallDigItemRenderer.isMiningTool(stack));
            for (Type type : Type.values()) assertEquals(1, SmallDigItemRenderer.getScale(stack, type), item.toString());
        }
        assertFalse(SmallDigItemRenderer.isMiningTool(new ItemStack(Items.DIAMOND_SWORD)));
    }
    @Test void trackedMetadataBlocksScaleOnlyInHandsAndOnGround() {
        var stone = new ItemStack(Blocks.STONE, 1, 3);
        assertEquals(0.2F, SmallDigItemRenderer.getScale(stone, Type.FIRST_PERSON_RIGHT_HAND));
        assertEquals(0.2F, SmallDigItemRenderer.getScale(stone, Type.THIRD_PERSON_LEFT_HAND));
        assertEquals(0.1F, SmallDigItemRenderer.getScale(stone, Type.GROUND));
        for (Type type : new Type[] {Type.GUI, Type.FIXED, Type.HEAD, Type.NONE})
            assertEquals(1, SmallDigItemRenderer.getScale(stone, type));
    }
    @Test void disabledAndUntrackedItemsRemainVanilla() {
        assertEquals(1, SmallDigItemRenderer.getScale(new ItemStack(Items.APPLE), Type.GROUND));
        assertEquals(1, SmallDigItemRenderer.getScale(ItemStack.EMPTY, Type.GROUND));
        Configs.Generic.SMALL_DIG_ITEMS.setBooleanValue(false);
        assertEquals(1, SmallDigItemRenderer.getScale(new ItemStack(Blocks.STONE), Type.GROUND));
    }
}

