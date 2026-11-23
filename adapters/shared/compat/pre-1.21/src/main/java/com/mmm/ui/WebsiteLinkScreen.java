package com.mmm.ui;

import com.mmm.config.Configs;
import com.mmm.sync.WebsiteLinkManager;
import java.util.Locale;
import com.mmm.ui.BoardPlatform.Canvas;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import com.mmm.ui.BoardPlatform.BoardButton;
import com.mmm.ui.BoardPlatform.BoardField;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public final class WebsiteLinkScreen extends BoardPlatform.BoardScreen {
    private static final String LOGIN_URL = "https://www.mmmaniacs.com/login";
    private final Screen parent;
    private BoardField code;
    private BoardButton link, clear;
    private int left, wide, top;

    public WebsiteLinkScreen(Screen parent) {
        super(BoardPlatform.literal("Website Link"));
        this.parent = parent;
    }

    @Override protected void init() {
        MmmUi.ensureCursorVisible();
        String draft = code == null ? "" : code.getText();
        wide = Math.min(386, width - 24);
        left = (width - wide) / 2;
        top = Math.max(40, Math.min(68, (height - 140) / 2));
        addButton("Back", left + wide - 44, 7, 44, this::close);
        int actionWidth = 85;
        addButton("Open Website", left + wide - actionWidth, top, actionWidth,
                () -> Util.getOperatingSystem().open(LOGIN_URL));
        code = addDrawableChild(new BoardField(textRenderer, left, top + 53,
                wide - actionWidth - 6, 20, BoardPlatform.literal("Website code")));
        code.setMaxLength(12);
        code.setPlaceholder(BoardPlatform.literal("Enter code"));
        code.setChangedListener(value -> {
            String clean = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            if (!clean.equals(value)) code.setText(clean);
            updateActions();
        });
        link = addButton("Link Account", left + wide - actionWidth, top + 53, actionWidth, this::claim);
        clear = addButton("Clear Link", left + wide - actionWidth, top + 85, actionWidth, this::confirmClear);
        code.setText(draft);
        updateActions();
        setFocused(code);
        code.focus(true);
    }

    private BoardButton addButton(String label, int x, int y, int w, Runnable action) {
        return addDrawableChild(new BoardButton(x, y, w, 20, BoardPlatform.literal(label), b -> action.run()) {
            @Override protected void renderFace(Canvas c, int mx, int my, float delta) {
                boolean hot = active && (isMouseOver(mx, my) || isFocused());
                CollectionBoardArt.plate(c, getX(), getY(), getWidth(), getHeight(),
                        hot ? MmmUi.accent() : CollectionBoardArt.LINE,
                        MmmUi.menuSurface(hot ? CollectionBoardArt.ACTIVE : CollectionBoardArt.TILE));
                c.raw().drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth()/2,
                        getY() + 6, active ? MmmUi.TEXT : CollectionBoardArt.MUTED);
            }
        });
    }

    private void updateActions() {
        if (link == null || clear == null) return;
        var state = WebsiteLinkManager.getState();
        link.active = code.getText().length() >= 6 && state.status() != WebsiteLinkManager.Status.SUBMITTING
                && !(state.status() == WebsiteLinkManager.Status.QUEUED && code.getText().equals(state.code()));
        clear.visible = WebsiteLinkManager.hasPersistedLink();
    }

    private void claim() {
        if (link.active) WebsiteLinkManager.claimCode(code.getText());
        updateActions();
    }

    private void confirmClear() {
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                Configs.websiteLinkedMinecraftUuid = "";
                Configs.websiteLinkedMinecraftUsername = "";
                Configs.websiteSyncToken = "";
                Configs.websiteLinkedAtMs = 0L;
                Configs.websiteGlobalTotalBlocks = 0L;
                Configs.websiteGlobalTotalUpdatedAtMs = 0L;
                Configs.websiteLastSuccessfulSyncMs = 0L;
                Configs.clearSourceSyncCooldowns();
                Configs.saveToFile();
                WebsiteLinkManager.reset();
            }
            client.setScreen(this);
        }, BoardPlatform.literal("Clear website link?"), BoardPlatform.literal("This client will need a new website code to sync again.")));
    }

    @Override public void tick() { updateActions(); }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == 257 || key == 335) && code.isFocused()) { claim(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void render(Canvas c, int mx, int my, float delta) {
        c.raw().fill(0, 0, width, height, MmmUi.menuSurface(CollectionBoardArt.BASE));
        c.raw().fill(0, 33, width, 34, CollectionBoardArt.LINE);
        c.raw().drawText(textRenderer, "MMM", left, 13, CollectionBoardArt.ROSE, true);
        c.raw().drawText(textRenderer, "/ Website Link", left + 28, 13, MmmUi.TEXT, false);
        c.raw().drawText(textRenderer, "mmmaniacs.com", left, top + 6, MmmUi.TEXT, false);
        c.raw().drawText(textRenderer, "Get a mod link code on the website.", left, top + 26, CollectionBoardArt.MUTED, false);
        c.raw().drawText(textRenderer, "Website code", left, top + 42, MmmUi.TEXT, false);
        c.raw().fill(left, top + 80, left + wide, top + 81, CollectionBoardArt.LINE);
        boolean linked = WebsiteLinkManager.hasPersistedLink();
        String name = WebsiteLinkManager.getPersistedUsername();
        String account = !linked ? "Not linked" : name.isBlank() ? "Account linked" : "Linked as " + name;
        MmmUi.drawTextWithin(c.raw(), textRenderer, account, left, top + 91, wide - (linked ? 91 : 0),
                linked ? MmmUi.SUCCESS : MmmUi.TEXT, false);
        var state = WebsiteLinkManager.getState();
        int yy = top + 109;
        for (var line : textRenderer.wrapLines(BoardPlatform.literal(state.detail()), wide)) {
            c.raw().drawText(textRenderer, line, left, yy, state.status() == WebsiteLinkManager.Status.ERROR ? MmmUi.ERROR : CollectionBoardArt.MUTED, false);
            yy += textRenderer.fontHeight + 2;
        }
        super.render(c, mx, my, delta);
    }

    @Override public void renderBackground(Canvas c) {}
    @Override public boolean shouldPause() { return MmmUi.shouldPauseGame(); }
    @Override public void close() { client.setScreen(parent); }
}
