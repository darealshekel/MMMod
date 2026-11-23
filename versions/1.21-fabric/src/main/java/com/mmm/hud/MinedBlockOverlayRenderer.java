package com.mmm.hud;

import com.mmm.config.Configs;
import com.mmm.tracker.CollectionProgress;
import com.mmm.tracker.CollectionRankClient;
import com.mmm.tracker.LiveCollections;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class MinedBlockOverlayRenderer
{
    private static final MinedBlockOverlayState STATE = new MinedBlockOverlayState();
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.US);
    private static ClientWorld world;
    private static long sampledAt;
    private static ItemStack icon = ItemStack.EMPTY;
    private static MinedBlockOverlayState.Progress progress;
    private static View view;
    private static View preview;

    private record View(String heading, String target, ItemStack icon) {}

    private MinedBlockOverlayRenderer() {}

    public static void onBlockMined(Block block)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active(client) || block == null) return;
        if (STATE.mined(Registries.BLOCK.getId(block).toString(), System.nanoTime()))
        {
            icon = new ItemStack(block);
            progress = null;
            view = null;
            sampledAt = 0;
        }
    }

    public static void tick(MinecraftClient client)
    {
        if (!active(client) || STATE.blockId() == null) return;
        long now = System.nanoTime();
        if (STATE.opacity(now) == 0 || (progress != null && now - sampledAt < 200_000_000L)) return;
        sampledAt = now;
        // Counts are local; ranking uses the collection book's asynchronous, bounded cache.
        progress = MinedBlockOverlayState.Progress.from(STATE.blockId(), LiveCollections.counts(null));
        String rank = STATE.rankRequestReady(now) ? MinedBlockOverlayState.rankLabel(
                CollectionRankClient.get(progress.collectionId(), client.player.getName().getString())) : "";
        view = viewFor(progress, icon, rank);
    }

    private static View viewFor(MinedBlockOverlayState.Progress value, ItemStack item, String rank)
    {
        String name = CollectionProgress.customName(value.collectionId());
        if (name == null) name = Registries.BLOCK.get(Identifier.of(value.collectionId())).getName().getString();
        String tier = CollectionProgress.tierLabel(value.tier());
        String heading = name + " Collection" + (tier.isEmpty() ? "" : " " + tier) + rank;
        String target = value.complete() ? NUMBERS.format(value.count()) + " | Complete (100%)"
                : NUMBERS.format(value.count()) + " / " + NUMBERS.format(value.next())
                + String.format(Locale.ROOT, " (%.1f%%)", value.percentage());
        return new View(heading, target, item);
    }

    public static void preparePreview(MinecraftClient client)
    {
        String id = world == client.world && STATE.blockId() != null ? STATE.blockId() : "minecraft:stone";
        var counts = client.player == null ? Map.<String, Long>of() : LiveCollections.counts(null);
        preview = viewFor(MinedBlockOverlayState.Progress.from(id, counts), new ItemStack(Registries.BLOCK.get(Identifier.of(id))), "");
    }

    private static boolean active(MinecraftClient client)
    {
        if (!Configs.Generic.MINED_BLOCK_OVERLAY.getBooleanValue() || client.world == null || client.player == null)
        {
            clear();
            return false;
        }
        if (world != client.world)
        {
            clear();
            world = client.world;
        }
        return true;
    }

    public static void clear()
    {
        STATE.clear();
        world = null;
        progress = null;
        view = null;
        icon = ItemStack.EMPTY;
        sampledAt = 0;
    }

    public static void render(DrawContext context, MinecraftClient client)
    {
        if (!active(client) || view == null || client.options.hudHidden
                || client.currentScreen != null || client.isPaused()) return;
        float alpha = STATE.opacity(System.nanoTime());
        if (alpha < 0.01F) return;
        draw(context, client, view, alpha);
    }

    public static void renderPreview(DrawContext context, MinecraftClient client)
    {
        if (preview == null) preparePreview(client);
        draw(context, client, preview, 1);
    }

    private static void draw(DrawContext context, MinecraftClient client, View shown, float alpha)
    {
        int width = contentWidth(client, shown);
        var layout = layout(client, shown);
        context.getMatrices().push();
        context.setShaderColor(1, 1, 1, alpha);
        try
        {
            context.getMatrices().translate(layout.x(), layout.y(), 0);
            context.getMatrices().scale(layout.scale(), layout.scale(), 1);
            context.fill(0, 0, width, MinedBlockOverlayState.Layout.HEIGHT, 0xFF111111);
            boolean shadow = Configs.useHudTextShadow();
            context.drawText(client.textRenderer, shown.heading(), 36, 5, 0xFFFFFFFF, shadow);
            context.drawText(client.textRenderer, shown.target(), 36, 18, 0xFFFFFFFF, shadow);
            context.draw();
            drawIcon(context, client, shown.icon());
        }
        finally
        {
            // Flush before restoring alpha so unrelated HUD elements never inherit the fade.
            context.setShaderColor(1, 1, 1, 1);
            context.getMatrices().pop();
        }
    }

    public static int playtimeY(DrawContext context, MinecraftClient client)
    {
        if (!Configs.Generic.MINED_BLOCK_OVERLAY.getBooleanValue() || world != client.world || view == null
                || client.currentScreen != null || client.isPaused() || STATE.opacity(System.nanoTime()) < 0.01F)
            return Math.max(4, context.getScaledWindowHeight() - 82);
        return layout(client, view).playtimeY();
    }

    private static int contentWidth(MinecraftClient client, View shown)
    {
        if (shown == null) return 220;
        return Math.max(180, 44 + Math.max(client.textRenderer.getWidth(shown.heading()), client.textRenderer.getWidth(shown.target())));
    }

    private static MinedBlockOverlayState.Layout layout(MinecraftClient client, View shown)
    {
        return MinedBlockOverlayState.Layout.fit(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(),
                contentWidth(client, shown), Configs.Generic.MINED_BLOCK_OVERLAY_SCALE.getDoubleValue(),
                Configs.Generic.MINED_BLOCK_OVERLAY_X.getIntegerValue(), Configs.Generic.MINED_BLOCK_OVERLAY_Y.getIntegerValue());
    }

    public static int[] getBounds(MinecraftClient client)
    {
        View shown = client.currentScreen instanceof HudMoveScreen ? preview : view;
        var layout = layout(client, shown);
        int x = Math.round(layout.x()), y = Math.round(layout.y());
        return new int[] {x, y, x + Math.round(contentWidth(client, shown) * layout.scale()),
                y + Math.round(MinedBlockOverlayState.Layout.HEIGHT * layout.scale())};
    }

    private static void drawIcon(DrawContext context, MinecraftClient client, ItemStack item)
    {
        var model = client.getItemRenderer().getModel(item, client.world, client.player, 0);
        context.getMatrices().push();
        try
        {
            context.getMatrices().translate(18, 18, 150);
            context.getMatrices().scale(24, -24, 24);
            if (!model.isSideLit()) DiffuseLighting.disableGuiDepthLighting();
            // Vanilla's solid-item layer ignores partial alpha; use the same atlas with blending.
            client.getItemRenderer().renderItem(item, ModelTransformationMode.GUI, false, context.getMatrices(),
                    layer -> context.getVertexConsumers().getBuffer(TexturedRenderLayers.getEntityTranslucentCull()),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, model);
            context.draw();
        }
        finally
        {
            if (!model.isSideLit()) DiffuseLighting.enableGuiDepthLighting();
            context.getMatrices().pop();
        }
    }
}
