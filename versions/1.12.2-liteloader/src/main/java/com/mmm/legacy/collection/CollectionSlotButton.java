package com.mmm.legacy.collection;

import com.mmm.legacy.LegacyDrawContext;
import static com.mmm.legacy.BoardArt.*;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;

public final class CollectionSlotButton extends ButtonWidget {
    enum Kind { SLOT, TAB, ACTION, ENTRY, MILESTONE }
    private final Kind kind;
    private final String caption;
    private final long opened;
    private final int order;
    String focusKey;
    boolean chosen;
    int tier, earned;
    long earnedAt;
    private final ItemStack icon;
    private final boolean redBook;
    private final Runnable action;
    private final Supplier<String> badge;
    public final Supplier<List<String>> details;
    public CollectionSlotButton(int id, int x, int y, int size, String name, ItemStack icon, boolean redBook,
            Runnable action, Supplier<String> badge, Supplier<List<String>> details) {
        this(id,x,y,size,size,Kind.SLOT,name,"",icon,redBook,action,badge,details,0,0);
    }
    CollectionSlotButton(int id,int x,int y,int w,int h,Kind kind,String name,String caption,ItemStack icon,boolean redBook,
            Runnable action,Supplier<String> badge,Supplier<List<String>> details,long opened,int order) {
        super(id, x, y, w, h, name);
        this.focusKey = name;
        this.kind=kind; this.caption=caption; this.opened=opened; this.order=order;
        this.icon = icon; this.redBook = redBook; this.action = action; this.badge = badge; this.details = details;
    }
    CollectionSlotButton(int x,int y,int w,int h,Kind kind,String name,String caption,ItemStack icon,
            Supplier<String> badge,Runnable action,Supplier<List<String>> details,long opened,int order) {
        this(-1,x,y,w,h,kind,name,caption,icon,kind==Kind.TAB && icon.isEmpty(),action,badge,details,opened,order);
    }
    void select(boolean chosen) { this.chosen=chosen; }
    void drawFocus(LegacyDrawContext c) {
        c.drawBorder(x - 1, y - 1, width + 2, height + 2, TEXT);
    }
    public boolean contains(int mx, int my) { return visible && mx >= x && my >= y && mx < x + width && my < y + height; }
    public void press() { if (active) action.run(); }
    @Override public void render(Minecraft client, int mx, int my, float delta) {
        if (!visible) return;
        hovered = contains(mx, my);
        var c = new LegacyDrawContext();
        if(kind!=Kind.SLOT) {
            var font=client.textRenderer;
            int inset=Math.round(4*(1-reveal(opened,order)));
            int rim=chosen || hovered && active ? ROSE : LINE;
            com.mmm.ui.BoardEditorStyle.plate(c::fill,x,y,width,height,chosen ? ACTIVE : hovered && active ? 0xff302528 : TILE,rim);
            if(chosen) c.fill(x,y,x+2,y+height-1,ROSE);
            if(kind==Kind.ACTION) {
                String label=font.trim(caption,width-4);
                c.drawText(font,label,x+(width-font.getWidth(label))/2,y+(height-8)/2,active?TEXT:MUTED,false);
            } else if(kind==Kind.MILESTONE) {
                int mark=tier<=earned?RED:tier==earned+1?ROSE:LINE;
                if(earnedAt>0 && now()-earnedAt<350) mark=ROSE;
                c.fill(x+4,y+4,x+7,y+height-4,mark);
                c.drawText(font,font.trim(caption,width-14),x+10+inset,y+4,TEXT,false);
                c.drawText(font,font.trim(badge.get(),width-14),x+10+inset,y+height-11,MUTED,false);
            } else {
                int ix=x+(caption.isEmpty()?(width-16)/2:4), iy=y+(height-16)/2;
                if(redBook) RedBookIcon.draw(c,ix,iy); else c.drawItem(icon,ix,iy);
                int tx=x+24+inset, w=Math.max(0,x+width-tx-3);
                c.drawText(font,font.trim(caption,w),tx,kind==Kind.ENTRY?y+4:y+(height-8)/2,TEXT,false);
                if(kind==Kind.ENTRY) c.drawText(font,font.trim(badge.get(),w),tx,y+height-12,MUTED,false);
            }
            return;
        }
        c.fill(x, y, x + width, y + height, hovered && active ? 0xFF514545 : 0xFF303030);
        c.drawBorder(x, y, width, height, hovered && active ? 0xFFF26969 : 0xFF151515);
        String overlay = badge.get();
        int ix = x + (width - 16) / 2;
        int iy = y + (overlay.isEmpty() ? (height - 16) / 2 : height < 32 ? 1 : 3);
        if (redBook) RedBookIcon.draw(c, ix, iy); else c.drawItem(icon, ix, iy);
        if (!overlay.isEmpty()) {
            int by = y + height - (height < 32 ? 9 : 10);
            c.fill(x + 1, by, x + width - 1, y + height - 1, 0xFF202023);
            c.drawText(client.textRenderer, overlay, x + (width - client.textRenderer.getWidth(overlay)) / 2, by, 0xFFEAEAEA, false);
        }
        if (!active) c.fill(x, y, x + width, y + height, 0x77000000);
    }
}
