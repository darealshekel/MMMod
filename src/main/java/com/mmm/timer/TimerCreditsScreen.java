package com.mmm.timer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mmm.config.Configs;
import com.mmm.ui.MmmUi;
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

public class TimerCreditsScreen extends Screen
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
        context.fill(0, 0, this.width, this.height, 0xF0050505);

        int panelW = Math.min(420, this.width - 32);
        int panelH = Math.min(260, this.height - 32);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        long now = System.currentTimeMillis();
        int pulse = (int) (40 + Math.round((Math.sin(now / 180.0D) + 1.0D) * 30.0D));
        int glow = (pulse << 24) | 0x00E00000;
        context.fill(x - 4, y - 4, x + panelW + 4, y + panelH + 4, glow);
        MmmUi.card(context, x, y, panelW, panelH, MmmUi.CARD, MmmUi.BORDER);
        context.fill(x + 14, y + 16, x + 18, y + 36, MmmUi.RED);
        context.drawText(this.textRenderer, Text.literal("TIMER COMPLETE"), x + 28, y + 16, Configs.getHudTitleColor(), false);
        context.drawText(this.textRenderer, Text.literal(MmmTimerState.formatTime(MmmTimerState.getDurationMs()) + " run finished"), x + 28, y + 31, MmmUi.MUTED, false);

        int statsY = y + 58;
        drawStat(context, x + 18, statsY, "Blocks", UiFormat.formatCompact(MmmTimerState.getBlocksBroken()));
        drawStat(context, x + panelW / 2 + 6, statsY, "Best Hour", UiFormat.formatCompact(MmmTimerState.getBestHourBlocks()));

        List<MmmTimerState.BlockCount> top = MmmTimerState.getTopBlocks().stream().limit(5).toList();
        int listY = statsY + 46;
        context.drawText(this.textRenderer, Text.literal("TOP BLOCKS"), x + 18, listY, Configs.getHudTextColor(), false);
        listY += 18;
        if (top.isEmpty())
        {
            context.drawText(this.textRenderer, Text.literal("No blocks mined during this timer."), x + 18, listY, MmmUi.INACTIVE, false);
        }
        else
        {
            for (int i = 0; i < top.size(); i++)
            {
                MmmTimerState.BlockCount entry = top.get(i);
                int rowY = listY + i * 24;
                context.fill(x + 18, rowY - 3, x + panelW - 18, rowY + 19, MmmUi.INSET);
                context.drawBorder(x + 18, rowY - 3, panelW - 36, 22, MmmUi.BORDER_SOFT);
                context.drawText(this.textRenderer, Text.literal("#" + (i + 1)), x + 26, rowY + 4, MmmUi.RED, false);
                context.drawItem(getCachedIcon(entry.id()), x + 50, rowY);
                context.drawText(this.textRenderer, Text.literal(blockName(entry.id())), x + 72, rowY + 5, Configs.getHudTextColor(), false);
                String count = UiFormat.formatCompact(entry.count());
                context.drawText(this.textRenderer, Text.literal(count), x + panelW - 28 - this.textRenderer.getWidth(count), rowY + 5, Configs.getHudNumberColor(), false);
            }
        }

        context.drawText(this.textRenderer, Text.literal("ESC to close"), x + panelW - 18 - this.textRenderer.getWidth("ESC to close"), y + panelH - 22, MmmUi.MUTED, false);
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
        return false;
    }

    private void drawStat(DrawContext context, int x, int y, String label, String value)
    {
        int width = 176;
        MmmUi.card(context, x, y, width, 34, MmmUi.INSET, MmmUi.BORDER_SOFT);
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
