package com.mmm.timer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mmm.config.Configs;
import com.mmm.ui.MmmUi;
import com.mmm.ui.BoardEditorStyle;
import com.mmm.util.UiFormat;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TimerCreditsScreen extends com.mmm.ui.CompatScreen
{
    private static final Map<String, ItemStack> ICON_CACHE = new LinkedHashMap<>();
    private final Screen parent;

    public TimerCreditsScreen(Screen parent)
    {
        super(Text.literal("MMM Timer Complete"));
        this.parent = parent;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        int panelW = BoardEditorStyle.contentWidth(width, 420);
        int x = BoardEditorStyle.contentX(width, 420);
        MmmUi.backdrop(context, width, height);
        MmmUi.boardHeader(context, textRenderer, width, x, "Timer complete");
        int y = BoardEditorStyle.HEADER_HEIGHT + 6;
        context.drawText(textRenderer, MmmTimerState.formatTime(MmmTimerState.getDurationMs()) + " run finished",
                x, y, BoardEditorStyle.TEXT, false);
        int statsY = y + 15, col = (panelW - 6) / 2;
        drawStat(context, x, statsY, col, "Blocks", UiFormat.formatCompact(MmmTimerState.getBlocksBroken()));
        drawStat(context, x + col + 6, statsY, col, "Best Hour", UiFormat.formatCompact(MmmTimerState.getBestHourBlocks()));

        List<MmmTimerState.BlockCount> top = MmmTimerState.getTopBlocks().stream().limit(5).toList();
        int listY = statsY + 39;
        context.drawText(this.textRenderer, Text.literal("TOP BLOCKS"), x, listY, BoardEditorStyle.MUTED, false);
        listY += 14;
        int step = Math.max(14, Math.min(24, (height - listY - 3) / 5));
        if (top.isEmpty())
        {
            context.drawText(this.textRenderer, Text.literal("No blocks mined during this timer."), x + 18, listY, MmmUi.INACTIVE, false);
        }
        else
        {
            for (int i = 0; i < top.size(); i++)
            {
                MmmTimerState.BlockCount entry = top.get(i);
                int rowY = listY + i * step;
                MmmUi.boardRow(context, x, rowY - 2, panelW, step - 1, MmmUi.CARD, BoardEditorStyle.LINE);
                context.drawText(this.textRenderer, Text.literal("#" + (i + 1)), x + 5, rowY + 2, BoardEditorStyle.ROSE, false);
                context.drawItem(getCachedIcon(entry.id()), x + 28, rowY - 2);
                context.drawText(this.textRenderer, Text.literal(blockName(entry.id())), x + 50, rowY + 2, Configs.getHudTextColor(), false);
                String count = UiFormat.formatCompact(entry.count());
                context.drawText(this.textRenderer, Text.literal(count), x + panelW - 28 - this.textRenderer.getWidth(count), rowY + 2, Configs.getHudNumberColor(), false);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (keyCode == 256)
        {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close()
    {
        MmmTimerState.dismissCredits();
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean shouldPause()
    {
        return MmmUi.shouldPauseGame();
    }

    @Override protected void init() {
        addDrawableChild(MmmUi.backButton(this, BoardEditorStyle.contentX(width, 420) + BoardEditorStyle.contentWidth(width, 420)));
    }

    @Override public void renderBackground(DrawContext c, int mx, int my, float delta) {}

    private void drawStat(DrawContext context, int x, int y, int width, String label, String value)
    {
        MmmUi.boardRow(context, x, y, width, 34, MmmUi.INSET, MmmUi.BORDER_SOFT);
        context.drawText(this.textRenderer, Text.literal(label), x + 8, y + 7, MmmUi.MUTED, false);
        context.drawText(this.textRenderer, Text.literal(value), x + width - 8 - this.textRenderer.getWidth(value), y + 18, Configs.getHudNumberColor(), false);
    }

    private static String blockName(String id)
    {
        return resolveBlock(id).getName().getString();
    }

    private static ItemStack getCachedIcon(String id)
    {
        ItemStack cached = ICON_CACHE.get(id);
        if (cached != null)
        {
            return cached.copy();
        }
        Block block = resolveBlock(id);
        Item item = block.asItem();
        ItemStack stack = item == Items.AIR ? new ItemStack(Blocks.STONE) : new ItemStack(item);
        ICON_CACHE.put(id, stack.copy());
        return stack;
    }

    private static Block resolveBlock(String id)
    {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null)
        {
            return Blocks.STONE;
        }
        Block block = Registries.BLOCK.get(identifier);
        return block == null ? Blocks.STONE : block;
    }
}
