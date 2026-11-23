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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Exports only the progress panel, never the world, chat, or account credentials. */
public final class ProgressShareScreen extends com.mmm.ui.CompatScreen
{
    private final Screen parent;
    private final String playerName, source, amount, profileUrl;
    private final ProgressShareData data;
    private final List<ProgressShareData.Metric> metrics;
    private final ItemStack icon;
    private ButtonWidget saveButton;
    private boolean capturePending;
    private boolean captureRendered;
    private int captureX, captureY, captureWidth, captureHeight;
    private String status = "";

    private ProgressShareScreen(Screen parent, String title, String source, ItemStack icon, ProgressShareData data)
    {
        super(Text.literal(title));
        this.parent = parent;
        this.source = source;
        this.data = data;
        this.amount = data.amount();
        this.icon = icon.copy();
        this.metrics = data.metrics();
        var client = MinecraftClient.getInstance();
        this.playerName = client.getSession().getUsername();
        var uuid = client.getSession().getUuidOrNull();
        boolean linked = WebsiteLinkManager.hasPersistedLink() && uuid != null
                && uuid.toString().equalsIgnoreCase(Configs.websiteLinkedMinecraftUuid);
        this.profileUrl = linked ? "https://www.mmmaniacs.com/player/" + playerName.toLowerCase(Locale.ROOT) : "";
    }

    public static ProgressShareScreen session(Screen parent, SessionData data, String worldName)
    {
        return new ProgressShareScreen(parent, "Session Summary", worldName, new ItemStack(Items.DIAMOND_PICKAXE),
                ProgressShareData.session(data.totalBlocks, data.getActiveDurationMs(), data.getWallDurationMs(),
                        data.getAverageBlocksPerHour(), data.getBestHourBlocks()));
    }

    public static ProgressShareScreen collection(Screen parent, String name, String id, long count, String scope, ItemStack icon)
    {
        return new ProgressShareScreen(parent, name + " Collection", scope, icon, ProgressShareData.collection(id, count));
    }

    @Override protected void init()
    {
        int left = (width - 142) / 2;
        int yy = panelY() + panelHeight() + 10;
        saveButton = addDrawableChild(ButtonWidget.builder(Text.literal("Save PNG"), button -> {
            button.active = false;
            status = "Saving...";
            capturePending = true;
        }).dimensions(left, yy, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Copy text"), button -> {
            client.keyboard.setClipboard(caption());
            status = "Copied";
        }).dimensions(left + 74, yy, 68, 20).build());
        addDrawableChild(MmmUi.backButton(this, BoardEditorStyle.contentX(width, 420) + BoardEditorStyle.contentWidth(width, 420)));
    }

    private int baseHeight() { return 142 + metrics.size() * 18; }
    private float panelScale() { return Math.max(0.05F, Math.min(1F, Math.min(BoardEditorStyle.contentWidth(width, 420) / 420F, (height - BoardEditorStyle.HEADER_HEIGHT - 54) / (float) baseHeight()))); }
    private int panelWidth() { return Math.round(420 * panelScale()); }
    private int panelHeight() { return Math.round(baseHeight() * panelScale()); }
    private int panelY() { return BoardEditorStyle.HEADER_HEIGHT + 5 + Math.max(0, (height - BoardEditorStyle.HEADER_HEIGHT - panelHeight() - 54) / 2); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        int panelX = (width - panelWidth()) / 2;
        int ww = 420, xx = 0, yy = 0;
        MmmUi.backdrop(context, width, height);
        MmmUi.boardHeader(context, textRenderer, width, BoardEditorStyle.contentX(width, 420), "Share progress");
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(panelX, panelY());
        context.getMatrices().scale(panelScale(), panelScale());
        context.fill(xx, yy, xx + ww, yy + baseHeight(), BoardEditorStyle.FLOOR);
        com.mmm.ui.MmmUi.drawBorder(context, xx, yy, ww, baseHeight(), BoardEditorStyle.LINE);
        context.fill(xx, yy, xx + ww, yy + 3, MmmUi.accent());
        context.drawItem(icon, xx + 12, yy + 15);
        MmmUi.drawTextWithin(context, textRenderer, "MMM / " + getTitle().getString(), xx + 36, yy + 20, ww - 48, BoardEditorStyle.MUTED, false);
        MmmUi.drawTextWithin(context, textRenderer, playerName, xx + 12, yy + 40, ww - 24, MmmUi.TEXT, false);
        MmmUi.drawTextWithin(context, textRenderer, source, xx + 12, yy + 56, ww - 24, BoardEditorStyle.MUTED, false);
        float scale = Math.min(2.5F, (ww - 24.0F) / Math.max(1, textRenderer.getWidth(amount)));
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(xx + 12, yy + 78);
        context.getMatrices().scale(scale, scale);
        context.drawText(textRenderer, amount, 0, 0, MmmUi.TEXT, false);
        context.getMatrices().popMatrix();
        context.drawText(textRenderer, "blocks mined", xx + 12, yy + 104, BoardEditorStyle.MUTED, false);
        int rowY = yy + 123;
        for (var metric : metrics)
        {
            context.drawText(textRenderer, metric.label(), xx + 12, rowY, BoardEditorStyle.MUTED, false);
            context.drawText(textRenderer, metric.value(), xx + ww - 12 - textRenderer.getWidth(metric.value()), rowY, MmmUi.TEXT, false);
            rowY += 18;
        }
        MmmUi.drawTextWithin(context, textRenderer, profileUrl.isEmpty() ? "mmmaniacs.com" : profileUrl,
                xx + 12, yy + baseHeight() - 13, ww - 24, BoardEditorStyle.MUTED, false);
        context.getMatrices().popMatrix();
        if (capturePending)
        {
            capturePending = false;
            // GUI drawing is deferred in 1.21.11. Read back on the next tick, after this frame.
            captureX = panelX;
            captureY = panelY();
            captureWidth = panelWidth();
            captureHeight = panelHeight();
            captureRendered = true;
        }
        super.render(context, mouseX, mouseY, delta);
        MmmUi.drawTextWithin(context, textRenderer, status, panelX, panelY() + panelHeight() + 35, panelWidth(), BoardEditorStyle.MUTED, false);
    }

    @Override public void tick()
    {
        if (captureRendered)
        {
            captureRendered = false;
            savePanel(captureX, captureY, captureWidth, captureHeight);
        }
    }

    private void savePanel(int x, int y, int width, int height)
    {
        int guiWidth = this.width, guiHeight = this.height;
        try
        {
            ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> {
                NativeImage cropped = null;
                try (image)
                {
                    double sx = image.getWidth() / (double) guiWidth, sy = image.getHeight() / (double) guiHeight;
                    int px = Math.clamp((int) Math.round(x * sx), 0, image.getWidth() - 1);
                    int py = Math.clamp((int) Math.round(y * sy), 0, image.getHeight() - 1);
                    int pw = Math.clamp((int) Math.round(width * sx), 1, image.getWidth() - px);
                    int ph = Math.clamp((int) Math.round(height * sy), 1, image.getHeight() - py);
                    cropped = new NativeImage(pw, ph, false);
                    image.copyRect(cropped, px, py, 0, 0, pw, ph, false, false);
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
                    client.execute(() -> failed(failure));
                }
            });
        }
        catch (Exception failure) { failed(failure); }
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

    @Override public void renderBackground(DrawContext c, int mx, int my, float delta) {}
    @Override public void close() { client.setScreen(parent); }
}
