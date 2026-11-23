package com.mmm.ui;

import com.mmm.MMM;
import com.mmm.config.Configs;
import com.mmm.sync.WebsiteLinkManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Exports only the progress panel, never the world, chat, or account credentials. */
public final class ProgressShareScreen extends com.mmm.ui.CompatScreen
{
    private final Screen parent;
    private final String playerName, source, amount, profileUrl;
    private final ProgressShareData data;
    private final List<ProgressShareData.Metric> metrics;
    private final BoardItemIcon icon;
    private Button saveButton;
    private boolean capturePending;
    private boolean captureRendered;
    private int captureX, captureY, captureWidth, captureHeight;
    private String status = "";

    private ProgressShareScreen(Screen parent, String title, String source, BoardItemIcon icon, ProgressShareData data)
    {
        super(Component.literal(title));
        this.parent = parent;
        this.source = source;
        this.data = data;
        this.amount = data.amount();
        this.icon = icon;
        this.metrics = data.metrics();
        var client = Minecraft.getInstance();
        this.playerName = client.getUser().getName();
        var uuid = client.getUser().getProfileId();
        boolean linked = WebsiteLinkManager.hasPersistedLink() && uuid != null
                && uuid.toString().equalsIgnoreCase(Configs.websiteLinkedMinecraftUuid);
        this.profileUrl = linked ? "https://www.mmmaniacs.com/player/" + playerName.toLowerCase(Locale.ROOT) : "";
    }

    public static ProgressShareScreen collection(Screen parent, String name, String id, long count, String scope, ItemStack icon)
    {
        return collection(parent, name, id, count, scope, new BoardItemIcon(icon.getItem()));
    }

    static ProgressShareScreen collection(Screen parent, String name, String id, long count, String scope, BoardItemIcon icon)
    {
        return new ProgressShareScreen(parent, name + " Collection", scope, icon, ProgressShareData.collection(id, count));
    }

    @Override protected void init()
    {
        int left = (width - 216) / 2;
        int yy = panelY() + panelHeight() + 10;
        saveButton = addRenderableWidget(Button.builder(Component.literal("Save PNG"), button -> {
            button.active = false;
            status = "Saving...";
            capturePending = true;
        }).bounds(left, yy, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Copy text"), button -> {
            minecraft.keyboardHandler.setClipboard(caption());
            status = "Copied";
        }).bounds(left + 74, yy, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose()).bounds(left + 148, yy, 68, 20).build());
    }

    private int baseHeight() { return 142 + metrics.size() * 18; }
    private float panelScale() { return Math.min(1F, Math.min(BoardEditorStyle.contentWidth(width, 420) / 420F, Math.max(1, height - BoardEditorStyle.HEADER_HEIGHT - 58) / (float) baseHeight())); }
    private int panelWidth() { return Math.round(420 * panelScale()); }
    private int panelHeight() { return Math.round(baseHeight() * panelScale()); }
    private int panelY() { return BoardEditorStyle.HEADER_HEIGHT + Math.max(4, (height - BoardEditorStyle.HEADER_HEIGHT - panelHeight() - 48) / 2); }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
    {
        int panelX = (width - panelWidth()) / 2;
        int ww = 420, xx = 0, yy = 0;
        BoardMenuChrome.editor(context, font, width, height, "Collections / Share");
        context.pose().pushMatrix();
        context.pose().translate(panelX, panelY());
        context.pose().scale(panelScale(), panelScale());
        // The entire crop, including the clipped corners, must be opaque.
        context.fill(xx, yy, xx + ww, yy + baseHeight(), BoardEditorStyle.FLOOR);
        CollectionBoardArt.dossier(context, xx, yy, ww, baseHeight());
        icon.draw(context, xx + 12, yy + 15);
        MmmUi.drawTextWithin(context, font, "MMM / " + getTitle().getString(), xx + 36, yy + 20, ww - 48, BoardEditorStyle.MUTED, false);
        MmmUi.drawTextWithin(context, font, playerName, xx + 12, yy + 40, ww - 24, MmmUi.TEXT, false);
        MmmUi.drawTextWithin(context, font, source, xx + 12, yy + 56, ww - 24, BoardEditorStyle.MUTED, false);
        float scale = Math.min(2.5F, (ww - 24.0F) / Math.max(1, font.width(amount)));
        context.pose().pushMatrix();
        context.pose().translate(xx + 12, yy + 78);
        context.pose().scale(scale, scale);
        context.text(font, amount, 0, 0, MmmUi.TEXT, false);
        context.pose().popMatrix();
        context.text(font, "blocks mined", xx + 12, yy + 104, BoardEditorStyle.MUTED, false);
        int rowY = yy + 123;
        for (var metric : metrics)
        {
            context.text(font, metric.label(), xx + 12, rowY, BoardEditorStyle.MUTED, false);
            context.text(font, metric.value(), xx + ww - 12 - font.width(metric.value()), rowY, MmmUi.TEXT, false);
            rowY += 18;
        }
        MmmUi.drawTextWithin(context, font, profileUrl.isEmpty() ? "mmmaniacs.com" : profileUrl,
                xx + 12, yy + baseHeight() - 13, ww - 24, BoardEditorStyle.MUTED, false);
        context.pose().popMatrix();
        if (capturePending)
        {
            capturePending = false;
            // GUI drawing is deferred. Read back on the next tick, after this frame.
            captureX = panelX;
            captureY = panelY();
            captureWidth = panelWidth();
            captureHeight = panelHeight();
            captureRendered = true;
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        MmmUi.drawTextWithin(context, font, status, panelX, panelY() + panelHeight() + 35, panelWidth(), BoardEditorStyle.MUTED, false);
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int x, int y, float delta) {}

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
            Screenshot.takeScreenshot(BoardPlatform.renderTarget(), image -> {
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
                    Path file = minecraft.gameDirectory.toPath().resolve("screenshots/mmm/" + UUID.randomUUID() + ".png");
                    CompletableFuture.runAsync(() -> {
                        try (export)
                        {
                            Files.createDirectories(file.getParent());
                            export.writeToFile(file);
                            minecraft.execute(() -> { status = "Saved to screenshots/mmm"; saveButton.active = true; });
                        }
                        catch (Exception failure) { minecraft.execute(() -> failed(failure)); }
                    }, Util.ioPool());
                }
                catch (Exception failure)
                {
                    if (cropped != null) cropped.close();
                    minecraft.execute(() -> failed(failure));
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

    @Override public void onClose() { BoardPlatform.open(parent); }
}
