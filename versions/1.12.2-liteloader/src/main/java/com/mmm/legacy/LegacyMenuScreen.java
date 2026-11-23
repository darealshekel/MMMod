package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.config.value.*;
import com.mmm.legacy.settings.*;
import com.mmm.ui.SettingsNavigation.Page;
import com.mmm.ui.ToolDirectory;
import com.mmm.legacy.SettingsBoard.Row;
import com.mmm.legacy.SettingsBoard.Section;
import com.mmm.legacy.collection.CollectionIcons;
import com.mmm.sync.CloudSyncManager;
import com.mmm.sync.WebsiteLinkManager;
import java.util.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import com.mmm.legacy.settings.BoardField;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import static com.mmm.legacy.BoardArt.*;

public final class LegacyMenuScreen extends LegacyScreen {
    private final SettingsBoard board = new SettingsBoard();
    private final LegacyDrawContext draw = new LegacyDrawContext();
    private Page<Row, Section> page = board.home;
    private record Location(Page<Row, Section> page, int offset) {}
    private final Deque<Location> history = new ArrayDeque<>();
    private final Map<Integer,Runnable> actions = new HashMap<>();
    private final List<Topic> topics = new ArrayList<>();
    private record Topic(Page<Row,Section> page, List<Row> rows, ItemStack icon, LegacyMenuButton button, int index) {}
    private List<Row> rows = List.of();
    private TextFieldWidget search;
    private String query = "";
    private int offset, searchOrigin, capacity, total, contentX, contentW, cols, inspectorW, top, bottom, rowH;
    private int inspection, lastMouseX = -1, lastMouseY = -1;
    private long inspectedAt = now();
    private SettingButton dragging;
    private static final int TOPIC_H = 35;
    public LegacyMenuScreen(Screen parent) { super(parent, "Settings"); }
    @Override protected int maxContentWidth() { return 920; }
    private boolean branching() { return query.isBlank() && !page.children().isEmpty(); }

    @Override public void init() {
        boolean focused = search != null && search.isFocused();
        int cursor = search == null ? query.length() : search.getCursor();
        int selection = search == null ? cursor : search.getSelectionEnd();
        super.init();
        buttons.clear(); actions.clear(); topics.clear();
        int right = left + panelWidth;
        action(900, right - 22, 7, 22, "X", () -> minecraft.openScreen(parent));
        action(901, right - 112, 7, 86, "Website Link", () -> minecraft.openScreen(new LegacyLinkScreen(this)));
        contentX = left; contentW = panelWidth;
        if (page == board.home && query.isEmpty())
            action(906, right - 66, 36, 66, ToolDirectory.HOTKEYS.label,
                    () -> minecraft.openScreen(board.tools.get(ToolDirectory.HOTKEYS).apply(this)));
        if (!history.isEmpty() || !query.isEmpty()) action(902, contentX, 38, 44, "Back", this::back);
        search = new BoardField(50,textRenderer,contentX + 1,62,contentW - 26,18);
        search.setMaxLength(128); search.setText(query); search.setFocused(focused);
        search.setCursor(Math.min(cursor,query.length())); search.setSelectionEnd(Math.min(selection,query.length()));
        action(903,right-22,61,22,"x",this::clearSearch);
        top = 86; bottom = height - 29;
        inspectorW = contentW >= 430 ? Math.min(191,contentW/3) : 0;
        if (branching()) {
            if (inspectorW == 0) bottom -= 37;
            int boardW = contentW - (inspectorW == 0 ? 0 : inspectorW + 7);
            cols = boardW >= 315 ? 2 : 1;
            capacity = Math.max(1,(bottom-top)/TOPIC_H) * cols;
            total = page.children().size(); clampOffset();
            int span = boardW / cols;
            for (int i=offset; i<Math.min(total,offset+capacity); i++) {
                var child = page.children().get(i);
                int index = i-offset;
                var options = SettingsBoard.options(child);
                var item = icon(child);
                var tool = toolTile(child);
                String detail = tool != null ? tool.description : child.children().isEmpty()
                        ? options.size()+" controls" : child.children().size()+" topics";
                var tile = new LegacyMenuButton(2000+i, contentX+index%cols*span,
                        top+index/cols*TOPIC_H,span-3,TOPIC_H-3,child.title(),detail,item,false);
                addButton(tile);
                topics.add(new Topic(child,options,item,tile,i));
                actions.put(tile.id, () -> activate(child));
            }
            inspection = Math.max(offset,Math.min(inspection,Math.min(total,offset+capacity)-1));
        } else {
            rows = query.isBlank() ? page.rows() : board.search(query);
            rowH = 32; cols = 1; capacity = Math.max(1,(bottom-top)/rowH); total = rows.size(); clampOffset();
            for (int i=offset; i<Math.min(total,offset+capacity); i++) {
                var row = rows.get(i); int y = top + (i-offset)*rowH + 5, id = 100+(i-offset)*2;
                int cw = SettingsUi.controlWidth(contentW), x = right - cw - 26;
                if (row.config()!=null) {
                    var b = new SettingButton(id,x,y,cw,row.config(),row.slider());
                    addButton(b); actions.put(id,() -> edit(row));
                    action(id+1,right-22,y,21,"R",() -> { row.config().resetToDefault(); Configs.saveToFile(); init(); });
                } else action(id,x+22,y,cw,row.value(),() -> minecraft.openScreen(row.action().apply(this)));
            }
        }
        if (total > capacity) {
            action(904,right-48,height-25,22,"<",() -> scroll(-capacity)).active = offset > 0;
            action(905,right-22,height-25,22,">",() -> scroll(capacity)).active = offset+capacity < total;
        }
    }
    private ItemStack icon(Page<Row,Section> child) {
        ToolDirectory tool = toolTile(child);
        if (tool != null) return CollectionIcons.item(switch (tool) {
            case HOTKEYS -> "feather"; case PROJECTS -> "iron_pickaxe"; case HISTORY -> "book";
            case SUMMARY -> "clock"; case SCOREBOARD -> "sign"; case LEADERBOARDS -> "gold_ingot"; default -> "compass";
        });
        String id = child.title().equals("Everyday settings") ? "compass" : switch (child.source() == null
                ? child.title() : child.source().category()) {
            case "SYNC" -> "ender_pearl"; case "SOCIAL", "SESSIONS" -> "writable_book";
            case "HUD", "COLORS" -> "comparator"; case "Mining visuals", "VISUALS", "BLOCK_ESP" -> "redstone";
            case "HOTKEYS" -> "feather"; case "TIMER" -> "clock"; default -> "iron_pickaxe";
        };
        return CollectionIcons.item(id);
    }
    private ButtonWidget action(int id,int x,int y,int w,String name,Runnable run) {
        actions.put(id,run); return button(id,x,y,w,name);
    }
    private void edit(Row r) {
        var config = r.config();
        if (config instanceof IConfigBoolean b) { b.toggleBooleanValue(); Configs.saveToFile(); init(); }
        else if (config instanceof ConfigOptionList o) minecraft.openScreen(new OptionPickerScreen(this,o));
        else minecraft.openScreen(new ValueEditorScreen(this,config));
    }
    private ToolDirectory toolTile(Page<Row,Section> dest) {
        return dest.source()!=null && "Tools".equals(dest.source().title()) && dest.rows().size()==1
                ? ToolDirectory.find(dest.rows().get(0).label()) : null;
    }
    private void activate(Page<Row,Section> dest) {
        if (toolTile(dest)!=null) { minecraft.openScreen(dest.rows().get(0).action().apply(this)); return; }
        history.push(new Location(page,offset)); open(dest);
    }
    private void open(Page<Row,Section> dest) {
        query=""; page=dest; inspection=offset=0; inspectedAt=now(); search.setFocused(false); init();
    }
    private void clearSearch() { query=""; offset=searchOrigin; search.setFocused(false); init(); }
    private void back() {
        if (!query.isEmpty()) { clearSearch(); return; }
        if (history.isEmpty()) { minecraft.openScreen(parent); return; }
        Location loc=history.pop(); open(loc.page); offset=loc.offset; init();
    }
    private void clampOffset() { offset = Math.max(0,Math.min(offset,Math.max(0,((total-1)/capacity)*capacity))); }
    private void scroll(int n) { offset+=n; clampOffset(); inspection=offset; inspectedAt=now(); init(); }
    @Override protected void buttonClicked(ButtonWidget b) { Runnable run=actions.get(b.id); if (run!=null) run.run(); }
    @Override public void render(int mx,int my,float delta) {
        fill(0,0,width,height,SettingsUi.opacity(BASE));
        BoardArt.header(draw, textRenderer, width, left, panelWidth - 116, title);
        boolean linked=WebsiteLinkManager.isCurrentPlayerLinked();
        String account=linked ? "Linked: "+WebsiteLinkManager.getPersistedUsername() :
                WebsiteLinkManager.hasPersistedLink() ? "Wrong linked account" : "Website link required";
        if (panelWidth>=475) {
            fit(account,left+110,6,panelWidth-226,linked ? 0xff69d59a : ROSE);
            fit(CloudSyncManager.getStatusSummary(),left+110,19,panelWidth-226,MUTED);
        }
        int shift=history.isEmpty() && query.isEmpty() ? 0 : 52;
        fit(query.isBlank() ? page==board.home ? "Controls & preferences" : page.title() : "Search results",
                contentX+shift,44,contentW-shift-(page==board.home && query.isEmpty()?70:0),TEXT);
        search.render();
        if (query.isEmpty() && !search.isFocused()) fit("Search settings and hotkeys",contentX+5,67,contentW-38,MUTED);
        boolean moved=mx!=lastMouseX || my!=lastMouseY; lastMouseX=mx; lastMouseY=my;
        List<String> hint=null;
        if (branching()) {
            for (Topic t:topics) if (moved && mx>=t.button.x && mx<t.button.x+t.button.getWidth()
                    && my>=t.button.y && my<t.button.y+TOPIC_H-3) inspect(t.index);
            for (Topic t:topics) {
                boolean selected=t.index==inspection;
                t.button.selected=selected;
                if(selected) renderInspector(t);
            }
        } else {
            for (int i=offset;i<Math.min(total,offset+capacity);i++) {
                Row row=rows.get(i); int yy=top+(i-offset)*rowH, w=contentW-SettingsUi.controlWidth(contentW)-38;
                fill(contentX,yy,contentX+contentW,yy+rowH-2,SettingsUi.opacity(TILE));
                fit(row.label(),contentX+6,yy+5,w,TEXT);
                fit(query.isBlank()?row.description():row.section(),contentX+6,yy+17,w,MUTED);
                if(mx>=contentX && mx<contentX+contentW && my>=yy && my<yy+rowH-2 && dragging==null) {
                    hint=new ArrayList<>(textRenderer.split(row.label(),Math.min(280,width-24)));
                    if(!row.description().isEmpty()) hint.addAll(textRenderer.split(row.description(),Math.min(280,width-24)));
                    if(row.config()!=null) hint.addAll(textRenderer.split("Value: "+row.value()+" / Default: "+row.config().getDefaultStringValue(),Math.min(280,width-24)));
                }
            }
            if(total==0) text("No matching settings",contentX,top+10,MUTED);
        }
        if(total>capacity) text((offset+1)+"-"+Math.min(total,offset+capacity)+" / "+total,contentX,height-19,MUTED);
        super.render(mx,my,delta);
        if(hint!=null) renderTooltip(hint,mx,my);
        if(panelWidth>=475 && mx>=left+110 && mx<left+panelWidth-116 && my>=5 && my<30)
            renderTooltip(textRenderer.split(account+" / "+CloudSyncManager.getStatusDetail(),Math.min(280,width-24)),mx,my);
    }
    private void renderInspector(Topic t) {
        boolean compact=inspectorW==0;
        boolean hasConfigs=t.rows.stream().anyMatch(r->r.config()!=null);
        int x=compact?contentX:contentX+contentW-inspectorW, y=compact?bottom+2:top, w=compact?contentW:inspectorW;
        int h=compact?32:Math.min(152,bottom-top);
        dossier(draw,x,y,w,h);
        int tx=x+8+Math.round(5*(1-reveal(inspectedAt,0))), edge=x+w-8;
        if ("Tools".equals(t.page.title())) {
            fit("Tools / "+t.rows.size(),tx,y+7,w-16,ROSE);
            String names = String.join(", ", t.rows.stream().map(Row::label).toList());
            int yy=y+19;
            for(String line:textRenderer.split(names,w-16)) {
                if(yy+9>y+h) break;
                fit(line,tx,yy,w-16,TEXT); yy+=11;
            }
            return;
        }
        long changed=t.rows.stream().filter(r->r.config()!=null && r.config().isModified()).count();
        ToolDirectory tool = toolTile(t.page);
        if (tool != null) {
            fit(tool.label,tx,y+7,w-16,ROSE);
            int lineY = y+20;
            for (String line : textRenderer.split(tool.description,w-16)) {
                if (lineY+9>y+h) break;
                fit(line,tx,lineY,w-16,TEXT); lineY+=11;
            }
            return;
        }
        fit(compact?t.page.title()+" / "+changed+" customized":"CATEGORY FILE",tx,y+7,w-16,ROSE);
        if(!compact) {
            String number=String.format(Locale.ROOT,"%02d",t.index+1);
            text(number,edge-textRenderer.getWidth(number),y+7,ROSE);
            draw.drawItem(t.icon,tx,y+21);
            fit(t.page.title(),tx+22,y+25,w-40,TEXT);
        }
        int yy=compact?y+19:y+45, shown=0;
        for(Row r:t.rows) {
            if(yy+9>y+h-(compact?2:20) || shown==4) break;
            if(r.config()==null && r.key()==null && hasConfigs) continue;
            int valueW=Math.min(w/3,textRenderer.getWidth(r.value()));
            fit(r.label(),tx,yy,edge-tx-valueW-6,TEXT);
            fit(r.value(),edge-valueW,yy,valueW,MUTED); yy+=17; shown++;
        }
        if(!compact) fit(changed==0?"Default values":changed+" customized",tx,y+h-14,w-16,ROSE);
    }
    private void inspect(int n) { if(n!=inspection) { inspection=n; inspectedAt=now(); } }
    @Override protected void mouseClicked(int x,int y,int b) {
        search.mouseClicked(x,y,b);
        if(b==0) {
            if(panelWidth>=475 && x>=left+110 && x<left+panelWidth-116 && y>=5 && y<30) { minecraft.openScreen(new LegacyLinkScreen(this)); return; }
            for(var btn:buttons) if(btn instanceof SettingButton s && s.slider && x>=s.x && x<s.x+s.getWidth() && y>=s.y && y<s.y+20) {
                dragging=s; s.slide(x); return;
            }
        }
        super.mouseClicked(x,y,b);
    }
    @Override protected void mouseDragged(int x,int y,int b,long elapsed) { if(dragging!=null) dragging.slide(x); }
    @Override protected void mouseReleased(int x,int y,int b) { if(dragging!=null) { dragging=null; Configs.saveToFile(); } super.mouseReleased(x,y,b); }
    @Override protected void keyPressed(char c,int key) {
        if(key==Keyboard.KEY_ESCAPE) { back(); return; }
        if(!search.isFocused() && branching()) {
            int d=switch(key) { case Keyboard.KEY_LEFT -> -1; case Keyboard.KEY_RIGHT,Keyboard.KEY_TAB -> 1;
                case Keyboard.KEY_UP -> -cols; case Keyboard.KEY_DOWN -> cols; default -> 0; };
            if(d!=0) {
                int next=Math.max(0,Math.min(total-1,inspection+d));
                if(next<offset || next>=offset+capacity) scroll(next<offset?-capacity:capacity);
                inspect(next); return;
            }
            if(key==Keyboard.KEY_RETURN || key==Keyboard.KEY_NUMPADENTER) { activate(page.children().get(inspection)); return; }
        }
        if(!search.isFocused() && !Character.isISOControl(c)) search.setFocused(true);
        search.keyPressed(c,key);
        if(!query.equals(search.getText())) {
            if(query.isEmpty()) searchOrigin=offset;
            query=search.getText(); offset=query.isEmpty()?searchOrigin:0; init();
        }
    }
    @Override public void handleMouse() { super.handleMouse(); int d=Mouse.getEventDWheel(); if(d!=0 && total>capacity) scroll(d>0?-capacity:capacity); }
    @Override public void tick() { search.tick(); }
    @Override public void removed() { if(dragging!=null) Configs.saveToFile(); dragging=null; super.removed(); }
}
