package com.mmm.ui;

import com.mmm.config.Configs;
import com.mmm.sync.WebsiteLinkManager;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class WebsiteLinkScreen extends CompatScreen {
    private static final String LOGIN_URL = "https://www.mmmaniacs.com/login";
    private final Screen parent;
    private EditBox code;
    private Button link, clear;
    private int left, wide, top;

    public WebsiteLinkScreen(Screen parent) {
        super(Component.literal("Website Link"));
        this.parent = parent;
    }

    @Override protected void init() {
        MmmUi.ensureCursorVisible();
        String draft = code == null ? "" : code.getValue();
        wide = Math.min(386, width - 24);
        left = (width - wide) / 2;
        top = Math.max(40, Math.min(68, (height - 140) / 2));
        addButton("Back", left + wide - 44, 7, 44, this::onClose);
        int actionWidth = 85;
        addButton("Open Website", left + wide - actionWidth, top, actionWidth,
                () -> WebsiteLauncher.open(LOGIN_URL));
        code = addRenderableWidget(new EditBox(font, left, top + 53,
                wide - actionWidth - 6, 20, Component.literal("Website code")));
        code.setMaxLength(12);
        code.setHint(Component.literal("Enter code"));
        code.setResponder(value -> {
            String clean = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            if (!clean.equals(value)) code.setValue(clean);
            updateActions();
        });
        link = addButton("Link Account", left + wide - actionWidth, top + 53, actionWidth, this::claim);
        clear = addButton("Clear Link", left + wide - actionWidth, top + 85, actionWidth, this::confirmClear);
        code.setValue(draft);
        updateActions();
        setInitialFocus(code);
    }

    private Button addButton(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(new Button(x, y, w, 20, Component.literal(label), b -> action.run(), message -> message.get()) {
            @Override protected void extractContents(GuiGraphicsExtractor c, int mx, int my, float delta) {
                boolean hot = active && (isHovered() || isFocused());
                CollectionBoardArt.plate(c, getX(), getY(), getWidth(), getHeight(),
                        hot ? MmmUi.accent() : CollectionBoardArt.LINE,
                        MmmUi.menuSurface(hot ? CollectionBoardArt.ACTIVE : CollectionBoardArt.TILE));
                c.centeredText(font, getMessage(), getX() + getWidth()/2,
                        getY() + 6, active ? MmmUi.TEXT : CollectionBoardArt.MUTED);
            }
        });
    }

    private void updateActions() {
        if (link == null || clear == null) return;
        var state = WebsiteLinkManager.getState();
        link.active = code.getValue().length() >= 6 && state.status() != WebsiteLinkManager.Status.SUBMITTING
                && !(state.status() == WebsiteLinkManager.Status.QUEUED && code.getValue().equals(state.code()));
        clear.visible = WebsiteLinkManager.hasPersistedLink();
    }

    private void claim() {
        if (link.active) WebsiteLinkManager.claimCode(code.getValue());
        updateActions();
    }

    private void confirmClear() {
        BoardPlatform.open(new ConfirmScreen(confirmed -> {
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
            BoardPlatform.open(this);
        }, Component.literal("Clear website link?"), Component.literal("This client will need a new website code to sync again.")));
    }

    @Override public void tick() { updateActions(); }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) && code.isFocused()) { claim(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, MmmUi.menuSurface(CollectionBoardArt.BASE));
        c.fill(0, 33, width, 34, CollectionBoardArt.LINE);
        c.text(font, "MMM", left, 13, CollectionBoardArt.ROSE, true);
        c.text(font, "/ Website Link", left + 28, 13, MmmUi.TEXT, false);
        c.text(font, "mmmaniacs.com", left, top + 6, MmmUi.TEXT, false);
        c.text(font, "Get a mod link code on the website.", left, top + 26, CollectionBoardArt.MUTED, false);
        c.text(font, "Website code", left, top + 42, MmmUi.TEXT, false);
        c.fill(left, top + 80, left + wide, top + 81, CollectionBoardArt.LINE);
        boolean linked = WebsiteLinkManager.hasPersistedLink();
        String name = WebsiteLinkManager.getPersistedUsername();
        String account = !linked ? "Not linked" : name.isBlank() ? "Account linked" : "Linked as " + name;
        MmmUi.drawTextWithin(c, font, account, left, top + 91, wide - (linked ? 91 : 0),
                linked ? MmmUi.SUCCESS : MmmUi.TEXT, false);
        var state = WebsiteLinkManager.getState();
        if (!state.detail().isBlank()) c.textWithWordWrap(font, Component.literal(state.detail()), left, top + 109, wide,
                state.status() == WebsiteLinkManager.Status.ERROR ? MmmUi.ERROR : CollectionBoardArt.MUTED);
        super.extractRenderState(c, mx, my, delta);
    }

    @Override public void extractBackground(GuiGraphicsExtractor c, int mx, int my, float delta) {}
    @Override public boolean isPauseScreen() { return MmmUi.shouldPauseGame(); }
    @Override public void onClose() { BoardPlatform.open(parent); }
}
