package com.mmm.ui;

import static com.mmm.ui.BoardEditorStyle.*;

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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Exports only the progress panel, never the world, chat, or account credentials. */
public final class ProgressShareScreen extends Screen
{
    private static final int MAX_WIDTH = 420;
    private final Screen parent;
    private final String playerName, source, amount, profileUrl;
    private final ProgressShareData data;
    private final List<ProgressShareData.Metric> metrics;
    private final ItemStack icon;
    private ButtonWidget saveButton;
    private boolean capturePending, captureRendered;
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
        capturePending = captureRendered = false;
        int left = panelX(), right = left + panelWidth();
        int actionY = panelY() + panelHeight() + 6;
        saveButton = addButton("Save PNG", left, actionY, 66, () -> {
            saveButton.active = false;
            status = "Saving...";
            capturePending = true;
        });
        addButton("Copy text", left + 71, actionY, 66, () -> {
            client.keyboard.setClipboard(caption());
            status = "Copied";
        });
        addButton("Back", right - 48, (HEADER_HEIGHT - 18) / 2, 48, this::close);
    }

    private ButtonWidget addButton(String label, int x, int y, int w, Runnable action) {
        return addDrawableChild(new ButtonWidget(x, y, w, 18, Text.literal(label), b -> action.run(), message -> message.get()) {
            @Override protected void renderWidget(DrawContext c, int mx, int my, float delta) {
                boolean hot = active && (isHovered() || isFocused());
                CollectionBoardArt.plate(c, getX(), getY(), getWidth(), getHeight(),
                        hot ? ROSE : LINE, hot ? CollectionBoardArt.ACTIVE : TILE);
                c.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth() / 2,
                        getY() + 5, active ? TEXT : MUTED);
            }
        });
    }

    private int panelX() { return contentX(width, MAX_WIDTH); }
    private int panelWidth() { return contentWidth(width, MAX_WIDTH); }
    private int toolbarHeight() { return panelWidth() < 300 ? 43 : 32; }
    private int baseHeight() { return 142 + metrics.size() * 18; }
    private int panelHeight() { return Math.max(1, Math.min(baseHeight(), height - HEADER_HEIGHT - toolbarHeight() - 8)); }
    private int panelY() { return HEADER_HEIGHT + Math.max(4, (height - HEADER_HEIGHT - toolbarHeight() - panelHeight()) / 2); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        int panelX = panelX(), top = panelY(), w = panelWidth(), h = panelHeight();
        int right = panelX + w, bottom = top + h;
        context.fill(0, 0, width, height, FLOOR);
        context.fill(0, 0, width, HEADER_HEIGHT - 1, SURFACE);
        context.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, LINE);
        context.drawText(textRenderer, "MMM", panelX, 11, ROSE, true);
        MmmUi.drawTextWithin(context, textRenderer, "/ Share progress", panelX + 27, 11, w - 82, TEXT, false);

        // The export rectangle contains only the opaque record. Keep controls, status
        // messages and the screen header outside it, including at compact GUI scales.
        boolean compact = h < 164;
        int pad = compact ? 7 : 12;
        int headerH = compact ? 24 : 34, footerH = compact ? 15 : 22;
        int rowH = Math.min(18, Math.max(10, (h - headerH - footerH - 29) / metrics.size()));
        int ledgerY = bottom - footerH - metrics.size() * rowH;
        context.fill(panelX, top, right, bottom, SURFACE);
        context.drawBorder(panelX, top, w, h, LINE);
        context.fill(panelX, top, right, top + 3, 0xffd90000);
        context.drawItem(icon, panelX + pad, top + (headerH - 16) / 2 + 1);
        MmmUi.drawTextWithin(context, textRenderer, getTitle().getString(), panelX + pad + 22,
                top + (headerH - 8) / 2 + 1, w - 2 * pad - 52, TEXT, true);
        context.drawText(textRenderer, "MMM", right - pad - textRenderer.getWidth("MMM"), top + (headerH - 8) / 2 + 1, ROSE, false);
        context.fill(panelX + pad, top + headerH - 1, right - pad, top + headerH, LINE);

        int totalW = (w - 2 * pad) * 55 / 100;
        int summaryH = ledgerY - top - headerH;
        float scale = Math.min(compact ? 1.5f : 2.5f, Math.min((totalW - 4f) / Math.max(1, textRenderer.getWidth(amount)),
                Math.max(.75f, (summaryH - 11f) / 9f)));
        int amountH = (int)Math.ceil(9 * scale);
        int amountY = top + headerH + Math.max(1, (summaryH - amountH - 10) / 2);
        BoardGraphics.push(context);
        try {
            BoardGraphics.translate(context, panelX + pad, amountY);
            BoardGraphics.scale(context, scale, scale);
            context.drawText(textRenderer, amount, 0, 0, TEXT, false);
        } finally { BoardGraphics.pop(context); }
        context.drawText(textRenderer, "blocks mined", panelX + pad, amountY + amountH, MUTED, false);
        int ownerX = panelX + pad + totalW + 7, ownerY = top + headerH + Math.max(1, (summaryH - 19) / 2);
        MmmUi.drawTextWithin(context, textRenderer, playerName, ownerX, ownerY, right - pad - ownerX, TEXT, false);
        MmmUi.drawTextWithin(context, textRenderer, source, ownerX, ownerY + 11, right - pad - ownerX, MUTED, false);

        for (int i = 0; i < metrics.size(); i++) {
            var metric = metrics.get(i);
            int y = ledgerY + rowH * i, ty = y + (rowH - 8) / 2;
            context.fill(panelX + 1, y, right - 1, y + rowH, i % 2 == 0 ? TILE : SURFACE);
            int valueW = Math.min(textRenderer.getWidth(metric.value()), (w - 2 * pad) / 2);
            MmmUi.drawTextWithin(context, textRenderer, metric.label(), panelX + pad, ty, w - 2 * pad - valueW - 6, MUTED, false);
            MmmUi.drawTextRightWithin(context, textRenderer, metric.value(), right - pad, ty, valueW, TEXT, false);
        }
        context.fill(panelX + pad, bottom - footerH, right - pad, bottom - footerH + 1, LINE);
        MmmUi.drawTextWithin(context, textRenderer, profileUrl.isEmpty() ? "mmmaniacs.com" : profileUrl,
                panelX + pad, bottom - footerH + (footerH - 8) / 2, w - 2 * pad, ROSE, false);
        if (capturePending)
        {
            capturePending = false;
            // GUI drawing is deferred on 1.21.6. Read the rendered panel on the next tick.
            captureX = panelX;
            captureY = panelY();
            captureWidth = panelWidth();
            captureHeight = panelHeight();
            captureRendered = true;
        }
        super.render(context, mouseX, mouseY, delta);
        boolean stacked = w < 300;
        int statusX = stacked ? panelX : panelX + 145;
        MmmUi.drawTextWithin(context, textRenderer, status, statusX, bottom + (stacked ? 29 : 11), right - statusX, MUTED, false);
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
            BoardGraphics.capture(client.getFramebuffer(), image -> {
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

    // The preview paints its own opaque panel; vanilla background blur would blur the export.
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override public void close() { client.setScreen(parent); }
}
