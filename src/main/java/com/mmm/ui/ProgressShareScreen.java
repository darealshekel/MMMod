package com.mmm.ui;

import com.mmm.MMM;
import com.mmm.config.Configs;
import com.mmm.storage.SessionData;
import com.mmm.sync.WebsiteLinkManager;
import com.mmm.tracker.CollectionProgress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
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
public final class ProgressShareScreen extends Screen
{
    private record Metric(String label, String value) {}
    private final Screen parent;
    private final String playerName, source, amount, profileUrl;
    private final List<Metric> metrics;
    private final ItemStack icon;
    private ButtonWidget saveButton;
    private boolean capturePending;
    private String status = "";
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.US);

    private ProgressShareScreen(Screen parent, String title, String source, long blocks, ItemStack icon, List<Metric> metrics)
    {
        super(Text.literal(title));
        this.parent = parent;
        this.source = source;
        this.amount = NUMBERS.format(blocks);
        this.icon = icon.copy();
        this.metrics = List.copyOf(metrics);
        var client = MinecraftClient.getInstance();
        this.playerName = client.getSession().getUsername();
        var uuid = client.getSession().getUuidOrNull();
        boolean linked = WebsiteLinkManager.hasPersistedLink() && uuid != null
                && uuid.toString().equalsIgnoreCase(Configs.websiteLinkedMinecraftUuid);
        this.profileUrl = linked ? "https://www.mmmaniacs.com/player/" + playerName.toLowerCase(Locale.ROOT) : "";
    }

    public static ProgressShareScreen session(Screen parent, SessionData data, String worldName)
    {
        return new ProgressShareScreen(parent, "Session Summary", worldName, data.totalBlocks, new ItemStack(Items.DIAMOND_PICKAXE),
                List.of(new Metric("Session IGT", clock(data.getActiveDurationMs())),
                        new Metric("Session Time", clock(data.getWallDurationMs())),
                        new Metric("Average / hour", NUMBERS.format(data.getAverageBlocksPerHour())),
                        new Metric("Best Hour", NUMBERS.format(data.getBestHourBlocks()))));
    }

    public static ProgressShareScreen collection(Screen parent, String name, String id, long count, String scope, ItemStack icon)
    {
        var goals = CollectionProgress.milestones(id);
        return new ProgressShareScreen(parent, name + " Collection", scope, count, icon,
                List.of(new Metric("Milestones completed", CollectionProgress.completed(id, count) + " / " + goals.size()),
                        new Metric("Collection cap", NUMBERS.format(goals.getLast())),
                        new Metric("Next milestone", CollectionProgress.next(id, count) == 0 ? "Completed" : NUMBERS.format(CollectionProgress.next(id, count)))));
    }

    @Override protected void init()
    {
        int left = (width - 216) / 2;
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
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close()).dimensions(left + 148, yy, 68, 20).build());
    }

    private int baseHeight() { return 142 + metrics.size() * 18; }
    private float panelScale() { return Math.min(1F, Math.min((width - 20) / 420F, (height - 70) / (float) baseHeight())); }
    private int panelWidth() { return Math.round(420 * panelScale()); }
    private int panelHeight() { return Math.round(baseHeight() * panelScale()); }
    private int panelY() { return Math.max(8, (height - panelHeight() - 54) / 2); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        int panelX = (width - panelWidth()) / 2;
        int ww = 420, xx = 0, yy = 0;
        MmmUi.backdrop(context, width, height);
        context.getMatrices().push();
        context.getMatrices().translate(panelX, panelY(), 0);
        context.getMatrices().scale(panelScale(), panelScale(), 1);
        context.fill(xx, yy, xx + ww, yy + baseHeight(), 0xFF101010);
        context.drawBorder(xx, yy, ww, baseHeight(), MmmUi.BORDER);
        context.fill(xx, yy, xx + ww, yy + 3, 0xFFD90000);
        context.drawItem(icon, xx + 12, yy + 15);
        MmmUi.drawTextWithin(context, textRenderer, "MMM / " + getTitle().getString(), xx + 36, yy + 20, ww - 48, MmmUi.LABEL, false);
        MmmUi.drawTextWithin(context, textRenderer, playerName, xx + 12, yy + 40, ww - 24, MmmUi.TEXT, false);
        MmmUi.drawTextWithin(context, textRenderer, source, xx + 12, yy + 56, ww - 24, MmmUi.LABEL, false);
        float scale = Math.min(2.5F, (ww - 24.0F) / Math.max(1, textRenderer.getWidth(amount)));
        context.getMatrices().push();
        context.getMatrices().translate(xx + 12, yy + 78, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.drawText(textRenderer, amount, 0, 0, MmmUi.TEXT, false);
        context.getMatrices().pop();
        context.drawText(textRenderer, "blocks mined", xx + 12, yy + 104, MmmUi.LABEL, false);
        int rowY = yy + 123;
        for (var metric : metrics)
        {
            context.drawText(textRenderer, metric.label, xx + 12, rowY, MmmUi.LABEL, false);
            context.drawText(textRenderer, metric.value, xx + ww - 12 - textRenderer.getWidth(metric.value), rowY, MmmUi.TEXT, false);
            rowY += 18;
        }
        MmmUi.drawTextWithin(context, textRenderer, profileUrl.isEmpty() ? "mmmaniacs.com" : profileUrl,
                xx + 12, yy + baseHeight() - 13, ww - 24, MmmUi.LABEL, false);
        context.getMatrices().pop();
        if (capturePending)
        {
            capturePending = false;
            context.draw();
            savePanel(panelX, panelY(), panelWidth(), panelHeight());
        }
        super.render(context, mouseX, mouseY, delta);
        MmmUi.drawTextWithin(context, textRenderer, status, panelX, panelY() + panelHeight() + 35, panelWidth(), MmmUi.LABEL, false);
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
        StringBuilder text = new StringBuilder(playerName + " / " + getTitle().getString() + "\n" + source + "\n" + amount + " blocks mined");
        for (var metric : metrics) text.append('\n').append(metric.label).append(": ").append(metric.value);
        if (!profileUrl.isEmpty()) text.append('\n').append(profileUrl);
        return text.toString();
    }

    private static String clock(long ms)
    {
        long seconds = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    @Override public void close() { client.setScreen(parent); }
}
