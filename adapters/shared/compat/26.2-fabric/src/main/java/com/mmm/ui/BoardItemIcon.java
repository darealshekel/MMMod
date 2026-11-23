package com.mmm.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Menu models have no bound item components until the client joins a world. */
public final class BoardItemIcon {
    static final Identifier BOOK_TEXTURE = texture("item/book");
    private final Item item;
    private Identifier menuTexture;
    private ClientLevel stackWorld;
    private ItemStack stack;

    public BoardItemIcon(Item item) { this.item = item; }

    public void draw(GuiGraphicsExtractor graphics, int x, int y) {
        var client = Minecraft.getInstance();
        if (client.level != null && client.player != null) {
            if (stackWorld != client.level) {
                stack = new ItemStack(item);
                stackWorld = client.level;
            }
            graphics.item(stack, x, y);
            return;
        }
        stackWorld = null;
        stack = null;
        if (menuTexture == null) menuTexture = findTexture(client);
        drawTexture(graphics, menuTexture, x, y);
    }

    private Identifier findTexture(Minecraft client) {
        String name = BuiltInRegistries.ITEM.getKey(item).getPath();
        String alternate = switch (name) {
            case "compass" -> "item/compass_16";
            case "ender_chest" -> "block/obsidian";
            case "bell" -> "entity/bell/bell_body";
            default -> "block/" + name + "_side";
        };
        for (String path : new String[]{"item/" + name, "block/" + name, alternate, "block/" + name + "_top"}) {
            var id = texture(path);
            if (client.getResourceManager().getResource(id).isPresent()) return id;
        }
        return BOOK_TEXTURE;
    }

    private static Identifier texture(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", "textures/" + path + ".png");
    }

    static void drawTexture(GuiGraphicsExtractor graphics, Identifier texture, int x, int y) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, 16, 16, 16, 16);
    }
}
