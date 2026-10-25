package com.mmm.legacy.collection;

import com.mmm.config.Configs;
import com.mmm.legacy.LegacyDrawContext;
import com.mmm.tracker.PinnedCollection;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/** Draw in the mining HUD's existing transform; reserve HEIGHT + 3 pixels when view() is non-null. */
public final class PinnedCollectionHud {
    public static final int HEIGHT = 56;
    public static final int MIN_WIDTH = 196;
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.US);
    private PinnedCollectionHud() {}
    public static void render(LegacyDrawContext context, Minecraft client, int x, int y, int width) {
        var pin = PinnedCollection.view();
        if (pin == null) return;
        if (Configs.Generic.HUD_TEXT_BACKGROUND.getBooleanValue())
            context.fill(x - 2, y - 2, x + width + 2, y + HEIGHT - 3, Configs.getHudBackgroundColor());
        context.drawItem(CollectionIcons.block(pin.blockId()), x, y + 2);
        text(context, client, pin.name(), x + 22, y, width - 22, 0xFFF5F5F5);
        text(context, client, pin.scope(), x + 22, y + 11, width - 22, 0xFFBBBBBB);
        String progress = NUMBERS.format(pin.count()) + " / " + NUMBERS.format(pin.target())
                + (pin.completed() ? " | Complete" : String.format(Locale.ROOT, " | %.1f%%", pin.fraction() * 100));
        text(context, client, progress, x, y + 24, width, 0xFFF5F5F5);
        text(context, client, CollectionRankText.format(pin.rank()), x, y + 36, width, 0xFFF5F5F5);
        context.fill(x, y + 49, x + width, y + 53, 0xB0222222);
        context.fill(x, y + 49, x + (int)(width * pin.fraction()), y + 53, pin.completed() ? 0xFF77DD88 : 0xFFCC3344);
    }
    private static void text(LegacyDrawContext context, Minecraft client, String text, int x, int y, int width, int color) {
        context.drawText(client.textRenderer, client.textRenderer.trim(text, Math.max(0, width)), x, y, color, Configs.useHudTextShadow());
    }
}
