package com.mmm.ui;

import com.mmm.MMM;
import com.mmm.config.Configs;
import com.mmm.storage.SessionData;
import com.mmm.sync.WebsiteLinkManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import com.mmm.ui.BoardPlatform.Canvas;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Exports only the progress panel, never the world, chat, or account credentials. */
public final class ProgressShareScreen extends BoardPlatform.BoardScreen implements BoardChrome.Page
{
    private final Screen parent;
    private final String playerName, source, amount, profileUrl;
    private final ProgressShareData data;
    private final List<ProgressShareData.Metric> metrics;
    private final ItemStack icon;
    private ButtonWidget saveButton;
    private boolean capturePending;
    private String status = "";

    private ProgressShareScreen(Screen parent, String title, String source, ItemStack icon, ProgressShareData data)
    {
        super(BoardPlatform.literal(title));
        this.parent = parent;
        this.source = source;
        this.data = data;
        this.amount = data.amount();
        this.icon = icon.copy();
        this.metrics = data.metrics();
        var client = MinecraftClient.getInstance();
        this.playerName = client.getSession().getUsername();
        boolean linked = WebsiteLinkManager.isCurrentPlayerLinked();
        this.profileUrl = linked ? "https://www.mmmaniacs.com/player/" + playerName.toLowerCase(Locale.ROOT) : "";
    }

    public static ProgressShareScreen collection(Screen parent, String name, String id, long count, String scope, ItemStack icon)
    {
        return new ProgressShareScreen(parent, name + " Collection", scope, icon, ProgressShareData.collection(id, count));
    }

    @Override protected void init()
    {
        int left = (width - 216) / 2;
        int yy = panelY() + panelHeight() + 10;
        saveButton = addDrawableChild(BoardPlatform.button(BoardPlatform.literal("Save PNG"), button -> {
            button.active = false;
            status = "Saving...";
            capturePending = true;
        }, left, yy, 68, 20));
        addDrawableChild(BoardPlatform.button(BoardPlatform.literal("Copy text"), button -> {
            client.keyboard.setClipboard(caption());
            status = "Copied";
        }, left + 74, yy, 68, 20));
        addDrawableChild(BoardPlatform.button(BoardPlatform.literal("Back"), button -> close(), left + 148, yy, 68, 20));
    }

    private int baseHeight() { return 142 + metrics.size() * 18; }
    private float panelScale() { return Math.min(1F, Math.min((width - 20) / 420F, (height - BoardEditorStyle.HEADER_HEIGHT - 70) / (float) baseHeight())); }
    private int panelWidth() { return Math.round(420 * panelScale()); }
    private int panelHeight() { return Math.round(baseHeight() * panelScale()); }
    private int panelY() { return BoardEditorStyle.HEADER_HEIGHT + Math.max(6, (height - BoardEditorStyle.HEADER_HEIGHT - panelHeight() - 54) / 2); }

    @Override public void render(Canvas context, int mouseX, int mouseY, float delta)
    {
        int panelX = (width - panelWidth()) / 2;
        int ww = 420, xx = 0, yy = 0;
        BoardChrome.header(context, width, height, "Share collection");
        context.raw().getMatrices().push();
        context.raw().getMatrices().translate(panelX, panelY(), 0);
        context.raw().getMatrices().scale(panelScale(), panelScale(), 1);
        BoardEditorStyle.plate(context.raw()::fill, xx, yy, ww, baseHeight(), BoardEditorStyle.SURFACE, BoardEditorStyle.LINE);
        context.raw().fill(xx, yy, xx + ww, yy + 3, BoardEditorStyle.ROSE);
        context.raw().drawItem(icon, xx + 12, yy + 15);
        MmmUi.drawTextWithin(context.raw(), textRenderer, "MMM / " + getTitle().getString(), xx + 36, yy + 20, ww - 48, BoardEditorStyle.MUTED, false);
        MmmUi.drawTextWithin(context.raw(), textRenderer, playerName, xx + 12, yy + 40, ww - 24, BoardEditorStyle.TEXT, false);
        MmmUi.drawTextWithin(context.raw(), textRenderer, source, xx + 12, yy + 56, ww - 24, BoardEditorStyle.MUTED, false);
        float scale = Math.min(2.5F, (ww - 24.0F) / Math.max(1, textRenderer.getWidth(amount)));
        context.raw().getMatrices().push();
        context.raw().getMatrices().translate(xx + 12, yy + 78, 0);
        context.raw().getMatrices().scale(scale, scale, 1);
        context.raw().drawText(textRenderer, amount, 0, 0, BoardEditorStyle.TEXT, false);
        context.raw().getMatrices().pop();
        context.raw().drawText(textRenderer, "blocks mined", xx + 12, yy + 104, BoardEditorStyle.MUTED, false);
        int rowY = yy + 123;
        for (var metric : metrics)
        {
            context.raw().drawText(textRenderer, metric.label(), xx + 12, rowY, BoardEditorStyle.MUTED, false);
            context.raw().drawText(textRenderer, metric.value(), xx + ww - 12 - textRenderer.getWidth(metric.value()), rowY, BoardEditorStyle.TEXT, false);
            rowY += 18;
        }
        MmmUi.drawTextWithin(context.raw(), textRenderer, profileUrl.isEmpty() ? "mmmaniacs.com" : profileUrl,
                xx + 12, yy + baseHeight() - 13, ww - 24, BoardEditorStyle.MUTED, false);
        context.raw().getMatrices().pop();
        if (capturePending)
        {
            capturePending = false;
            BoardPlatform.flush(context);
            savePanel(panelX, panelY(), panelWidth(), panelHeight());
        }
        super.render(context, mouseX, mouseY, delta);
        MmmUi.drawTextWithin(context.raw(), textRenderer, status, panelX, panelY() + panelHeight() + 35, panelWidth(), BoardEditorStyle.MUTED, false);
    }

    private void savePanel(int x, int y, int width, int height)
    {
        NativeImage cropped = null;
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer()))
        {
            double sx = image.getWidth() / (double) this.width, sy = image.getHeight() / (double) this.height;
            int px = (int) Math.round(x * sx), py = (int) Math.round(y * sy);
            int pw = Math.min(image.getWidth() - px, (int) Math.round(width * sx));
            int ph = Math.min(image.getHeight() - py, (int) Math.round(height * sy));
            cropped = new NativeImage(pw, ph, false);
            for (int cy = 0; cy < ph; cy++)
                for (int cx = 0; cx < pw; cx++) cropped.setColor(cx, cy, image.getColor(px + cx, py + cy));
            NativeImage export = cropped;
            Path file = client.runDirectory.toPath().resolve("screenshots/mmm/" + UUID.randomUUID() + ".png");
            CompletableFuture.runAsync(() -> {
                try (export)
                {
                    Files.createDirectories(file.getParent());
                    export.writeTo(file);
                    client.execute(() -> { status = "Saved to screenshots/mmm"; saveButton.active = true; });
                }
                catch (Exception failure) { client.execute(() -> failed(failure)); }
            }, Util.getIoWorkerExecutor());
        }
        catch (Exception failure)
        {
            if (cropped != null) cropped.close();
            failed(failure);
        }
    }

    private void failed(Exception failure)
    {
        MMM.LOGGER.warn("[MMM] Progress image export failed", failure);
        status = "Could not save image. Try again.";
        saveButton.active = true;
    }

    private String caption()
    {
        return data.caption(playerName, getTitle().getString(), source, profileUrl);
    }

    @Override public void close() { client.setScreen(parent); }
}
