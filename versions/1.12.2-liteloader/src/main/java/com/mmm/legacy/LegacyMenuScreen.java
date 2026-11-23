package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.config.value.ConfigOptionList;
import com.mmm.config.value.IConfigBoolean;
import com.mmm.legacy.SettingsBoard.Row;
import com.mmm.legacy.SettingsBoard.Section;
import com.mmm.legacy.collection.CollectionIcons;
import com.mmm.legacy.settings.*;
import com.mmm.sync.CloudSyncManager;
import com.mmm.sync.WebsiteLinkManager;
import com.mmm.ui.SettingsNavigation.Page;
import com.mmm.ui.ToolDirectory;
import java.util.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import static com.mmm.legacy.BoardArt.*;

public final class LegacyMenuScreen extends LegacyScreen {
    private static final int TOP = 84, ROW_H = 32, GROUP_H = 14, TOOL_H = 37;
    private static final int FIRST_TOOL = 2000;
    private final SettingsBoard board = new SettingsBoard();
    private final LegacyDrawContext draw = new LegacyDrawContext();
    private Page<Row, Section> page = board.home.initialCategory();
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<Row, String> groupTitles = new IdentityHashMap<>();
    private final List<Integer> starts = new ArrayList<>();
    private final List<Integer> rowY = new ArrayList<>();
    private TextFieldWidget search;
    private String query = "";
    private int offset, searchOrigin, capacity, total, contentX, contentW, railStep, focusId = -1;
    private SettingButton dragging;

    public LegacyMenuScreen(Screen parent) { super(parent, "Settings"); }
    @Override protected int maxContentWidth() { return 920; }
    private boolean toolsVisible() { return query.isBlank() && "Tools".equals(page.title()); }
    private int toolColumns() { return contentW >= 320 ? 2 : 1; }

    @Override public void init() {
        boolean focused = search != null && search.isFocused();
        int cursor = search == null ? query.length() : search.getCursor();
        int selection = search == null ? cursor : search.getSelectionEnd();
        super.init();
        buttons.clear(); actions.clear(); rows.clear(); groupTitles.clear(); rowY.clear(); starts.clear();
        int right = left + panelWidth;
        action(906, right - 172, 7, 56, "Hotkeys", () -> minecraft.openScreen(board.tools.get(ToolDirectory.HOTKEYS).apply(this)));
        action(901, right - 112, 7, 86, "Website Link", () -> minecraft.openScreen(new LegacyLinkScreen(this)));
        action(900, right - 22, 7, 22, "X", () -> minecraft.openScreen(parent));

        int railW = panelWidth >= 475 ? 122 : 72;
        contentX = left + railW + 7; contentW = right - contentX;
        var categories = board.home.children();
        railStep = Math.min(23, Math.max(1, (height - 39) / Math.max(1, categories.size())));
        for (int i = 0; i < categories.size(); i++) {
            var category = categories.get(i);
            var button = new LegacyMenuButton(1000+i, left, 36+i*railStep, railW, Math.max(1,railStep-1),
                    category.title(), "", null, query.isBlank() && page == category);
            addButton(button);
            actions.put(button.id, () -> select(category));
        }
        search = new BoardField(50, textRenderer, contentX+1, 37, contentW-26, 18);
        search.setMaxLength(128); search.setText(query); search.setFocused(focused);
        search.setCursor(Math.min(cursor,query.length())); search.setSelectionEnd(Math.min(selection,query.length()));
        action(903, right-22, 36, 22, "x", this::clearSearch).active = !query.isEmpty();
        if (!query.isBlank()) action(902,contentX,60,44,"Back",this::clearSearch);
        int listHeight = Math.max(1, height-28-TOP);
        if (toolsVisible()) {
            int cols = toolColumns(), span = contentW / cols;
            total = page.children().size();
            int perPage = Math.max(1, listHeight / TOOL_H)*cols;
            for (int i=0; i<total; i+=perPage) starts.add(i);
            if (focusId>=FIRST_TOOL && focusId<FIRST_TOOL+total) offset=focusId-FIRST_TOOL;
            paginate();
            for (int i=offset; i<offset+capacity; i++) {
                var entry = page.children().get(i).rows().get(0);
                var tool = ToolDirectory.find(entry.label());
                String icon = switch (tool) {
                    case HOTKEYS -> "feather"; case PROJECTS -> "iron_pickaxe"; case HISTORY -> "book";
                    case SUMMARY -> "clock"; case SCOREBOARD -> "sign"; case LEADERBOARDS -> "gold_ingot"; default -> "compass";
                };
                var tile = new LegacyMenuButton(FIRST_TOOL+i, contentX+(i-offset)%cols*span, TOP+(i-offset)/cols*TOOL_H,
                        span-3, TOOL_H-3, tool.label, tool.description, CollectionIcons.item(icon), false);
                addButton(tile); actions.put(tile.id, () -> minecraft.openScreen(entry.action().apply(this)));
            }
        } else {
            if (query.isBlank()) {
                for (var group : page.rowGroups()) for (var row : group.rows()) {
                    rows.add(row);
                    if (!page.title().equals(group.title())) groupTitles.put(row, group.title());
                }
            } else rows.addAll(board.search(query));
            total = rows.size();
            // Repeat the group heading at a page break without separating it from its first control.
            for (int i=0; i<total;) {
                int first=i, used=0;
                starts.add(first);
                while (i<total) {
                    int step=ROW_H+(hasHeading(i,first)?GROUP_H:0);
                    if (i>first && used+step>listHeight) break;
                    used+=step; i++;
                }
            }
            paginate();
            int y=TOP;
            for (int i=offset; i<offset+capacity; i++) {
                if (hasHeading(i,offset)) y+=GROUP_H;
                rowY.add(y);
                var row=rows.get(i);
                int id=100+(i-offset)*2, cw=SettingsUi.controlWidth(contentW), x=right-cw-26;
                if (row.config()!=null) {
                    addButton(new SettingButton(id,x,y+5,cw,row.config(),row.slider()));
                    actions.put(id,() -> edit(row));
                    action(id+1,right-22,y+5,21,"R",() -> { row.config().resetToDefault(); Configs.saveToFile(); init(); });
                } else action(id,x+22,y+5,cw,row.value(),() -> minecraft.openScreen(row.action().apply(this)));
                y+=ROW_H;
            }
        }
        if (starts.size()>1) {
            action(904,right-48,height-25,22,"<",() -> scroll(-1)).active = offset>0;
            action(905,right-22,height-25,22,">",() -> scroll(1)).active = offset+capacity<total;
        }
    }
    private boolean hasHeading(int i,int first) {
        String name=groupTitles.get(rows.get(i));
        return name!=null && (i==first || !name.equals(groupTitles.get(rows.get(i-1))));
    }
    private void paginate() {
        if (starts.isEmpty()) { offset=capacity=0; return; }
        int index=Collections.binarySearch(starts,offset);
        if (index<0) index=Math.max(0,-index-2);
        offset=starts.get(index);
        capacity=(index+1<starts.size()?starts.get(index+1):total)-offset;
    }
    private ButtonWidget action(int id,int x,int y,int w,String name,Runnable run) {
        actions.put(id,run); return button(id,x,y,w,name);
    }
    private void edit(Row r) {
        if (r.config() instanceof IConfigBoolean b) { b.toggleBooleanValue(); Configs.saveToFile(); init(); }
        else if (r.config() instanceof ConfigOptionList o) minecraft.openScreen(new OptionPickerScreen(this,o));
        else minecraft.openScreen(new ValueEditorScreen(this,r.config()));
    }
    private void select(Page<Row,Section> category) {
        page=category; query=""; offset=searchOrigin=0; search.setFocused(false);
        if (toolsVisible()) focusId=FIRST_TOOL;
        init();
    }
    private void clearSearch() { query=""; offset=searchOrigin; search.setFocused(false); init(); }
    private void scroll(int direction) {
        if (starts.size()<2) return;
        int index=Math.max(0,Math.min(starts.size()-1,starts.indexOf(offset)+direction));
        offset=starts.get(index);
        if (toolsVisible()) focusId=FIRST_TOOL+offset;
        init();
    }
    @Override protected void buttonClicked(ButtonWidget b) { Runnable run=actions.get(b.id); if (run!=null) run.run(); }
    @Override public void render(int mx,int my,float delta) {
        fill(0,0,width,height,SettingsUi.opacity(BASE));
        BoardArt.header(draw,textRenderer,width,left,panelWidth-176,title);
        boolean linked=WebsiteLinkManager.isCurrentPlayerLinked();
        String account=linked ? "Linked: "+WebsiteLinkManager.getPersistedUsername() :
                WebsiteLinkManager.hasPersistedLink() ? "Wrong linked account" : "Website link required";
        if (panelWidth>=475) {
            fit(account,left+110,6,panelWidth-288,linked?0xff69d59a:ROSE);
            fit(CloudSyncManager.getStatusSummary(),left+110,19,panelWidth-288,MUTED);
        }
        fill(contentX-4,36,contentX-3,height-4,SettingsUi.opacity(LINE));
        search.render();
        if (query.isEmpty() && !search.isFocused()) fit("Search settings and hotkeys",contentX+5,42,contentW-38,MUTED);
        int shift=query.isBlank()?0:49;
        fit(query.isBlank()?page.title():"Search results",contentX+shift,66,contentW-shift,TEXT);
        List<String> hint=null;
        for (int n=0; n<rowY.size(); n++) {
            int i=offset+n, yy=rowY.get(n), w=contentW-SettingsUi.controlWidth(contentW)-38;
            Row row=rows.get(i);
            if (hasHeading(i,offset)) fit(groupTitles.get(row),contentX+4,yy-GROUP_H+2,contentW-8,ROSE);
            fill(contentX,yy,contentX+contentW,yy+ROW_H-2,SettingsUi.opacity(TILE));
            fit(row.label(),contentX+6,yy+5,w,TEXT);
            fit(query.isBlank()?row.description():row.section(),contentX+6,yy+17,w,MUTED);
            if (mx>=contentX && mx<contentX+contentW && my>=yy && my<yy+ROW_H-2 && dragging==null) {
                hint=new ArrayList<>(textRenderer.split(row.label(),Math.min(280,width-24)));
                if (!row.description().isEmpty()) hint.addAll(textRenderer.split(row.description(),Math.min(280,width-24)));
                if (row.config()!=null) hint.addAll(textRenderer.split("Value: "+row.value()+" / Default: "+row.config().getDefaultStringValue(),Math.min(280,width-24)));
            }
        }
        if (total==0) text("No matching settings",contentX,TOP+6,MUTED);
        if (starts.size()>1) fit((offset+1)+"-"+(offset+capacity)+" / "+total,contentX,height-19,contentW-54,MUTED);
        super.render(mx,my,delta);
        for (var b:buttons) {
            if (b.id==focusId && b.active && !search.isFocused()) fill(b.x,b.y-1,b.x+b.getWidth(),b.y,TEXT);
            if (b.id>=1000 && b.id<2000 && mx>=b.x && mx<b.x+b.getWidth() && my>=b.y && my<b.y+railStep-1)
                hint=List.of(b.message);
        }
        if (hint!=null) renderTooltip(hint,mx,my);
        if (accountHit(mx,my)) renderTooltip(textRenderer.split(account+" / "+CloudSyncManager.getStatusDetail(),Math.min(280,width-24)),mx,my);
    }
    private boolean accountHit(int x,int y) {
        return panelWidth>=475 && x>=left+110 && x<left+panelWidth-176 && y>=5 && y<30;
    }
    @Override protected void mouseClicked(int x,int y,int b) {
        focusId=-1; search.mouseClicked(x,y,b);
        if (b!=0) return;
        if (accountHit(x,y)) { minecraft.openScreen(new LegacyLinkScreen(this)); return; }
        // An action can rebuild the button list. Never dispatch this press to the new page.
        for (var btn:buttons) if (btn.mouseClicked(minecraft,x,y)) {
            if (btn instanceof SettingButton s && s.slider) { dragging=s; s.slide(x); return; }
            lastClickedButton=btn;
            btn.playClickSound(minecraft.getSoundManager());
            buttonClicked(btn);
            return;
        }
    }
    @Override protected void mouseDragged(int x,int y,int b,long elapsed) { if (dragging!=null) dragging.slide(x); }
    @Override protected void mouseReleased(int x,int y,int b) { if (dragging!=null) { dragging=null; Configs.saveToFile(); } super.mouseReleased(x,y,b); }
    @Override protected void keyPressed(char c,int key) {
        if (key==Keyboard.KEY_ESCAPE) {
            if (!query.isEmpty()) clearSearch(); else minecraft.openScreen(parent);
            return;
        }
        if (key==Keyboard.KEY_TAB) {
            List<Integer> stops=new ArrayList<>();
            for (var btn:buttons) if (btn.active && btn.visible) stops.add(btn.id);
            stops.add(50);
            int i=stops.indexOf(search.isFocused()?50:focusId), d=Screen.isShiftDown()?-1:1;
            focusId=stops.get(Math.floorMod((i<0 && d<0?0:i)+d,stops.size()));
            search.setFocused(focusId==50); return;
        }
        if (!search.isFocused() && toolsVisible() && total>0) {
            int step=switch(key) {
                case Keyboard.KEY_LEFT -> -1; case Keyboard.KEY_RIGHT -> 1;
                case Keyboard.KEY_UP -> -toolColumns(); case Keyboard.KEY_DOWN -> toolColumns();
                default -> 0;
            };
            if (step!=0) {
                int current=focusId>=FIRST_TOOL && focusId<FIRST_TOOL+total ? focusId-FIRST_TOOL : offset;
                int next=Math.max(0,Math.min(total-1,current+step));
                focusId=FIRST_TOOL+next;
                if (next<offset || next>=offset+capacity) init();
                return;
            }
        }
        if (!search.isFocused() && (key==Keyboard.KEY_RETURN || key==Keyboard.KEY_NUMPADENTER || key==Keyboard.KEY_SPACE && focusId>=0)) {
            for (var btn:buttons) if (btn.id==focusId && btn.active && btn.visible) { buttonClicked(btn); return; }
            return;
        }
        if (!search.isFocused() && !Character.isISOControl(c)) { focusId=-1; search.setFocused(true); }
        search.keyPressed(c,key);
        if (!query.equals(search.getText())) {
            if (query.isBlank()) searchOrigin=offset;
            query=search.getText(); offset=query.isBlank()?searchOrigin:0; init();
        }
    }
    @Override public void handleMouse() { super.handleMouse(); int d=Mouse.getEventDWheel(); if (d!=0) scroll(d>0?-1:1); }
    @Override public void tick() { search.tick(); }
    @Override public void removed() { if (dragging!=null) Configs.saveToFile(); dragging=null; super.removed(); }
}
