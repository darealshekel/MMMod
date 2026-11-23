package com.mmm.legacy.collection;

import com.mmm.legacy.LegacyScreen;
import com.mmm.legacy.LegacyDrawContext;
import com.mmm.sync.WebsiteLinkManager;
import com.mmm.ui.ProgressShareData;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.Window;
import net.minecraft.item.ItemStack;
import static com.mmm.legacy.BoardArt.*;

final class CollectionShareScreen extends LegacyScreen {
    private final ProgressShareData data;
    private final String scope;
    private final ItemStack icon;
    private String player, url, status="";
    private boolean capture, writing;
    private int top, h;
    CollectionShareScreen(Screen parent,String name,String id,long mined,String scope,ItemStack icon) {
        super(parent,name+" Collection");
        this.scope=scope; this.icon=icon; data=ProgressShareData.collection(id,mined);
    }
    @Override protected int maxContentWidth() { return 420; }
    @Override public void init() {
        super.init(); buttons.clear();
        player=minecraft.player.getGameProfile().getName();
        url=WebsiteLinkManager.isCurrentPlayerLinked()?"https://www.mmmaniacs.com/player/"+player.toLowerCase(Locale.ROOT):"";
        int header=com.mmm.ui.BoardEditorStyle.HEADER_HEIGHT;
        h=Math.min(172,height-header-50); top=header+(height-header-h-42)/2;
        int row=top+h+6, x=(width-216)/2;
        button(1,x,row,68,"Save PNG").active=!writing;
        button(2,x+74,row,68,"Copy text"); button(900,x+148,row,68,"Back");
    }
    @Override protected void buttonClicked(ButtonWidget b) {
        if(b.id==1) { capture=true; b.active=false; }
        else if(b.id==2) { setClipboard(data.caption(player,title,scope,url)); status="Copied"; }
        else super.buttonClicked(b);
    }
    @Override public void render(int mx,int my,float delta) {
        fill(0,0,width,height,com.mmm.ui.BoardEditorStyle.FLOOR);
        header(new LegacyDrawContext(),textRenderer,width,left,panelWidth,"Share progress");
        com.mmm.ui.BoardEditorStyle.plate(net.minecraft.client.gui.GuiElement::fill,left,top,panelWidth,h,BLACK,LINE);
        new LegacyDrawContext().drawItem(icon,left+8,top+8);
        fit("MMM / "+title,left+32,top+13,panelWidth-42,TEXT);
        fit(player+" / "+scope,left+10,top+34,panelWidth-20,MUTED);
        fit(data.amount()+" blocks mined",left+10,top+55,panelWidth-20,ROSE);
        int y=top+78;
        for(var metric:data.metrics()) {
            text(metric.label(),left+10,y,MUTED);
            text(metric.value(),left+panelWidth-10-textRenderer.getWidth(metric.value()),y,TEXT); y+=17;
        }
        fit(url.isEmpty()?"mmmaniacs.com":url,left+10,top+h-16,panelWidth-20,MUTED);
        if(capture) { capture=false; savePanel(); }
        super.render(mx,my,delta); fit(status,left,top+h+30,panelWidth,MUTED);
    }
    private void savePanel() {
        try {
            int scale=new Window(minecraft).getScale();
            BufferedImage frame=Screenshot.take(minecraft.width,minecraft.height,minecraft.getRenderTarget());
            // Copy just the opaque report, not a subimage retaining the full framebuffer.
            BufferedImage panel=new BufferedImage(panelWidth*scale,h*scale,BufferedImage.TYPE_INT_RGB);
            var g=panel.createGraphics();
            try { g.drawImage(frame,-left*scale,-top*scale,null); } finally { g.dispose(); frame.flush(); }
            var file=minecraft.gameDir.toPath().resolve("screenshots/mmm").resolve(UUID.randomUUID()+".png");
            writing=true; status="Saving...";
            CompletableFuture.runAsync(()->{
                try { Files.createDirectories(file.getParent()); ImageIO.write(panel,"png",file.toFile()); }
                catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                finally { panel.flush(); }
            }).whenComplete((ok,error)->minecraft.executeTask(()->complete(error)));
        } catch(RuntimeException e) { complete(e); }
    }
    private void complete(Throwable error) {
        writing=false; status=error==null?"Saved to screenshots/mmm":"Could not save image";
        for(var b:buttons) if(b.id==1) b.active=true;
    }
}
