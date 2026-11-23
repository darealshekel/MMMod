package com.mmm.ui;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import static com.mmm.ui.CollectionBoardArt.*;

public final class CollectionSlotButton extends AbstractButton {
    enum Tier {
        EARNED(0xffe7b8b8, 0, 1, 3, 38, 60, 24, 0),
        NEXT(ROSE, 8, 20, 34, 73, 34, 20, 8),
        LOCKED(0xff888484, 28, 34, 34, 62, 54, 62, 62);
        final int ink;
        final int[] rows;
        Tier(int ink, int... rows) { this.ink = ink; this.rows = rows; }
        void draw(GuiGraphicsExtractor c, int x, int y) {
            for (int r = 0; r < rows.length; r++) for (int col = 0; col < 7; col++) {
                if ((rows[r] & (64 >> col)) != 0) c.fill(x + col,y + r,x + col + 1,y + r + 1,ink);
            }
        }
    }
    enum Kind { TAB, ENTRY, MILESTONE, ACTION }
    private final Kind kind;
    private final BoardItemIcon icon;
    private final Runnable action;
    private final String caption;
    private final Supplier<String> subline;
    public final Supplier<List<Component>> details;
    private final long opened;
    private final int order;
    private boolean chosen;
    int tierNumber;
    private boolean inspected;
    private long inspectedAt;
    void inspect(boolean value, long at) { inspected = value; inspectedAt = at; }
    boolean isInspected() { return inspected; }
    private Tier tier;
    private long earnedAt = -1, previousFrame;
    private float hover;

    CollectionSlotButton(int x, int y, int w, int h, Kind kind, String name, String caption,
            BoardItemIcon icon, Supplier<String> subline, Runnable action, Supplier<List<Component>> details, long opened, int order) {
        super(x, y, w, h, Component.literal(name));
        this.kind = kind; this.caption = caption; this.icon = icon; this.subline = subline;
        this.action = action; this.details = details; this.opened = opened; this.order = order;
    }
    void select(boolean value) { chosen = value; }
    void setTier(Tier value, long at) { tier = value; earnedAt = at; }
    @Override public void onPress(net.minecraft.client.input.InputWithModifiers input) { action.run(); }
    @Override protected void extractContents(GuiGraphicsExtractor c, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        long time = now();
        boolean hot = active && (Minecraft.getInstance().getLastInputType().isKeyboard() ? isFocused() : isHovered());
        float step = Math.clamp((time - previousFrame) / 105f, 0f, 1f);
        hover = Math.clamp(hover + (hot ? step : -step), 0f, 1f);
        previousFrame = time;
        float enter = reveal(time - opened, order);
        float flash = earnedAt < 0 ? 0f : Math.clamp(1 - (time - earnedAt) / 480f, 0f, 1f);
        boolean accent = chosen || tier == Tier.NEXT;
        int face = accent ? ACTIVE : kind == Kind.TAB ? RAIL : TILE;
        face = ARGB.srgbLerp(hover, face, 0xff393232);
        if (flash > 0) face = ARGB.srgbLerp(flash * .6f, face, ROSE);
        if (kind == Kind.MILESTONE) {
            int baseY = y;
            y += Math.round(2 * (1f - enter) - hover);
            c.fill(x+2,baseY+h-2,x+w-2,baseY+h,BLACK);
            int rim = tier == Tier.NEXT ? 0xff9a474e : tier == Tier.EARNED ? 0xff675155 : LINE;
            plate(c,x,y,w,h-1,rim,face);
            if (inspected) brackets(c,x,y,w,h-1,2 + Math.round(4 * reveal(time - inspectedAt,0)));
        } else {
            c.fill(x, y, x + w, y + h, face);
            if (accent || hot) c.fill(x, y + 1, x + 2, y + h - 1, RED);
        }
        if (isFocused()) c.outline(x, y, w, h, ROSE);
        var f = Minecraft.getInstance().font;
        int color = !active ? 0xff777777 : accent ? ROSE : TEXT;
        int slide = Math.round((1 - enter) * 5), tx = x + 5 + slide;
        if (kind == Kind.TAB || kind == Kind.ENTRY) {
            boolean compact = caption.isEmpty();
            int ix = compact ? x + (w - 16) / 2 : tx;
            int iy = y + (h - 16) / 2;
            if (icon == null) RedBookIcon.draw(c, ix, iy); else icon.draw(c, ix, iy);
            tx += 21;
        }
        if (kind == Kind.ACTION) {
            c.centeredText(f, MmmUi.truncate(f, caption, w - 4), x + w/2, y + (h-8)/2, color);
        } else if (kind == Kind.ENTRY || kind == Kind.MILESTONE) {
            if (tier != null) {
                int iy = y + (h - 15) / 2;
                plate(c,tx,iy,15,15,tier == Tier.LOCKED ? LINE : 0xff734349,tier == Tier.NEXT ? 0xff60282b : BASE);
                if (flash > 0 && tier == Tier.EARNED) {
                    var matrices = c.pose();
                    matrices.pushMatrix();
                    matrices.translate(tx+7.5f,iy+7.5f);
                    float scale = 1f + flash*flash*.65f;
                    matrices.scale(scale,scale);
                    tier.draw(c,-3,-3);
                    matrices.popMatrix();
                } else tier.draw(c, tx + 4, iy + 4);
                tx += 19;
            }
            int ty = y + (h - 19) / 2;
            MmmUi.drawTextWithin(c,f,caption,tx,ty,x+w-tx-3,color,false);
            MmmUi.drawTextWithin(c,f,subline.get(),tx,ty + 11,x+w-tx-3,MUTED,false);
        } else if (!caption.isEmpty()) MmmUi.drawTextWithin(c,f,caption,tx,y+(h-8)/2,x+w-tx-3,color,false);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
}
