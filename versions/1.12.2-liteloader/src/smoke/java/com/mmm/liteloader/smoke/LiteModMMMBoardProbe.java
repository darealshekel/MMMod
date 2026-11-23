package com.mmm.liteloader.smoke;

import com.mumfrey.liteloader.Tickable;
import com.mmm.config.Configs;
import com.mmm.config.Hotkeys;
import com.mmm.hotkey.MmmHotkey;
import com.mmm.leaderboard.LeaderboardData;
import com.mmm.ui.BoardEditorStyle;
import com.mmm.ui.ToolDirectory;
import com.mmm.ui.SettingsNavigation.Page;
import com.mmm.legacy.LegacyMenuScreen;
import com.mmm.legacy.collection.CollectionBookScreen;
import com.mmm.legacy.collection.InventoryCollectionButton;
import com.mmm.liteloader.LiteModMMM;
import com.mmm.ui.CollectionBoardState;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.render.Window;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.world.GameMode;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.input.Keyboard;

/** Out-of-band driver for the release .litemod. All clicks go through the game screen. */
public final class LiteModMMMBoardProbe implements Tickable {
    private int stage, ticks;
    private boolean stopped;
    private Screen menu, inventory, book;
    private int route;
    private int launcherStep, toolIndex;
    private final boolean[] launcherComplete = new boolean[2];
    private Object toolsPage;
    private static final ToolDirectory[] TOOLS = {ToolDirectory.HOTKEYS, ToolDirectory.PROJECTS, ToolDirectory.HISTORY,
            ToolDirectory.SUMMARY, ToolDirectory.SCOREBOARD, ToolDirectory.LEADERBOARDS};
    private static final String[] TOOL_SCREENS = {"LegacyHotkeyScreen", "ProjectManagerScreen", "LegacyHistoryScreen",
            "LegacySessionScreen", "ScoreboardScreen", "LeaderboardScreen"};
    private String previousColor;
    private static final String[][] ROUTES = {
        {"Website Link", "LegacyLinkScreen"}, {"Open Config Gui", "HotkeyEditorScreen"},
        {"Blocks/sec Smoothing", "OptionPickerScreen"}, {"Perimeter Block List", "PerimeterBlocksScreen"},
        {"Projects", "ProjectManagerScreen"}, {"Sync Scoreboard", "LegacyScoreboardScreen"},
        {"Summary", "LegacySessionScreen"}, {"History", "LegacyHistoryScreen"},
        {"Timer", "TimerScreen"}, {"Timer>Results", "TimerResultsScreen"},
        {"Scoreboard", "ScoreboardScreen"}, {"Scoreboard>Move", "ScoreboardMoveScreen"},
        {"Scoreboard>Edit", "ScoreboardEditScreen"}, {"Scoreboard>Records", "ScoreboardRecordsScreen"},
        {"Ignored Players", "MmmChatIgnoreListScreen"}, {"Milestone Sounds", "GoalSoundSettingsScreen"},
        {"Leaderboards", "LeaderboardScreen"}, {"Leaderboards>Sidebar", "LeaderboardScreen"},
        {"Leaderboards>Profile", "LeaderboardProfileScreen"}, {"Move HUD", "HudEditorScreen"},
        {"@summary", "LegacySessionScreen"}, {"@history", "LegacyHistoryScreen"},
        {"@edit", "ScoreboardEditScreen"}, {"@records", "ScoreboardRecordsScreen"},
        {"Daily Goal", "ValueEditorScreen"}, {"@scoreboard", "ScoreboardScreen"},
        {"@chat", "MmmChatIgnoreListScreen"}, {"@leaderboard", "LeaderboardScreen"}, {"@efficiency", "LegacySessionScreen"}
    };
    public String getName() { return "MMM Board Probe"; }
    public String getVersion() { return "test-only"; }
    public void init(File path) {}
    public void upgradeSettings(String version, File config, File old) {}
    public void onTick(Minecraft mc,float partial,boolean inGame,boolean clock) {
        if(!Boolean.getBoolean("mmm.boardProbe") || !clock || stopped || ++ticks<12) return;
        ticks=0;
        try {
            if(stage>0 && (mc.world==null || mc.player==null)) return;
            if ((stage == 2 || stage == 8) && !launcherComplete[stage == 8 ? 1 : 0]) {
                verifyLauncher(mc, stage == 8);
                return;
            }
            switch(stage++) {
                case 0:
                    java.net.URL resource=LiteModMMM.class.getResource("LiteModMMM.class");
                    java.net.JarURLConnection archive=(java.net.JarURLConnection)resource.openConnection();
                    File loaded=new File(archive.getJarFileURL().toURI()).getCanonicalFile();
                    check(loaded.getParentFile().equals(new File(mc.gameDir,"mods").getCanonicalFile()),
                            "MMM implementation was not discovered in the game mods folder: "+loaded);
                    String source=resource.toString();
                    boolean forge;
                    try { Class.forName("net.minecraftforge.fml.common.Loader"); forge=true; }
                    catch(ClassNotFoundException ignored) { forge=false; }
                    check(forge==Boolean.getBoolean("mmm.boardProbeForge"),"Wrong loader mode");
                    System.out.println("MMM_BOARD_SOURCE "+source+" forge="+forge);
                    Configs.Generic.WEBSITE_SYNC_ENABLED.setBooleanValue(false);
                    mc.options.pauseOnUnfocus=false; mc.options.viewDistance=2; mc.options.guiScale=1;
                    mc.startGame("board-test","Board verification",new WorldSettings(1,GameMode.SURVIVAL,false,false,WorldGeneratorType.FLAT));
                    break;
                case 1:
                    mc.openScreen(null); chord(mc, Hotkeys.OPEN_CONFIG_GUI, Keyboard.KEY_X, Keyboard.KEY_V);
                    menu=mc.screen; check(menu instanceof LegacyMenuScreen,"X,V did not open Settings"); break;
                case 2:
                    shot(mc,"settings");
                    for(char c:"menu opacity".toCharArray()) key(menu,c,Keyboard.KEY_NONE);
                    check((Integer)get(menu,"total")>=1,"Search found no matching rows");
                    key(menu,(char)0,Keyboard.KEY_ESCAPE);
                    click(menu,"Website Link");
                    check(mc.screen.getClass().getSimpleName().equals("LegacyLinkScreen"),"Wrong link screen");
                    TextFieldWidget code=(TextFieldWidget)get(mc.screen,"code");
                    check(code.isFocused(),"Code not focused"); code.setText("TEST12");
                    mc.screen.init(); check("TEST12".equals(((TextFieldWidget)get(mc.screen,"code")).getText()),"Resize lost code");
                    key(mc.screen,(char)0,Keyboard.KEY_ESCAPE);
                    check(mc.screen==menu,"Link screen lost parent");
                    inventory=new SurvivalInventoryScreen(mc.player); mc.openScreen(inventory); break;
                case 3:
                    InventoryCollectionButton button=null;
                    for(Class<?> type=inventory.getClass();type!=null;type=type.getSuperclass()) for(Field f:type.getDeclaredFields())
                        if(f.getType()==InventoryCollectionButton.class) { f.setAccessible(true); button=(InventoryCollectionButton)f.get(inventory); }
                    check(button!=null,"Inventory button not injected");
                    mouse(inventory,button.x+9,button.y+9); book=mc.screen;
                    check(book instanceof CollectionBookScreen,"Click did not enter collections"); break;
                case 4:
                    shot(mc,"overview");
                    tab(book,false);
                    TextFieldWidget edit=(TextFieldWidget)get(book,"searchField");
                    check(edit.isFocused(),"First Tab did not reach search");
                    key(book,'e',mc.options.inventoryKey.getKeyCode()); key(book,' ',Keyboard.KEY_SPACE);
                    key(book,(char)13,Keyboard.KEY_RETURN);
                    check(mc.screen==book && "e ".equals(edit.getText()),"Search consumed space or the inventory shortcut");
                    key(book,'\b',Keyboard.KEY_BACK); key(book,'\b',Keyboard.KEY_BACK);
                    check(edit.getText().isEmpty(),"Backspace did not edit search");
                    verifyCycle(book,edit);
                    focus(book,"Ores collections"); key(book,(char)13,Keyboard.KEY_RETURN);
                    check("ORES".equals(String.valueOf(get(book,"category"))),"Enter did not open Ores");
                    focus(book,"Progress filter"); key(book,' ',Keyboard.KEY_SPACE);
                    check("Progress filter".equals(focusedName(book)),"Filter rebuild lost keyboard focus");
                    click(book,"Sort collections"); click(book,"World scope");
                    check(Configs.Generic.COLLECTION_FILTER.getIntegerValue()==1 && Configs.Generic.COLLECTION_SORT.getIntegerValue()==1 &&
                            !Configs.Generic.COLLECTION_GLOBAL.getBooleanValue(),"Preferences not bound");
                    click(book,"Close"); check(mc.screen==inventory,"Lost inventory parent");
                    mc.openScreen(new CommandChat()); ((CommandChat)mc.screen).collections(); break;
                case 5:
                    book=mc.screen; check(book instanceof CollectionBookScreen,"Chat command did not open collections");
                    check((Integer)get(book,"filter")==1 && (Integer)get(book,"sort")==1 &&
                            !((Boolean)get(book,"allWorlds")) && String.valueOf(get(book,"category")).equals("ORES"),"Fresh screen lost preferences");
                    click(book,"Progress filter"); click(book,"Progress filter");
                    TextFieldWidget search=(TextFieldWidget)get(book,"searchField");
                    mouse(book,search.x+4,search.y+4);
                    for(char c:"minecraft:diamond_ore".toCharArray()) key(book,c,Keyboard.KEY_NONE);
                    List<?> matches=(List<?>)get(book,"rows");
                    check(matches.size()==1,"Expected one diamond ore row: search="+get(book,"search")+" filter="+get(book,"filter")+" rows="+matches.size());
                    check(get(matches.get(0),"id").equals("minecraft:diamond_ore"),"Wrong search match");
                    focus(book,(String)get(matches.get(0),"name")); key(book,' ',Keyboard.KEY_SPACE);
                    focus(book,"Tier II"); key(book,(char)13,Keyboard.KEY_RETURN);
                    check(((CollectionBoardState)get(book,"board")).inspectedTier()==2,"Tier record not selected"); break;
                case 6:
                    shot(mc,"tier"); verifyFocusPixels(mc,book);
                    key(book,' ',Keyboard.KEY_SPACE); check(((CollectionBoardState)get(book,"board")).inspectedTier()==0,"Space did not return to the next tier");
                    click(book,"Tier II"); click(book,"World scope");
                    check(((CollectionBoardState)get(book,"board")).inspectedTier()==0,"Scope retained stale tier");
                    click(book,"Share collection"); check(mc.screen.getClass().getSimpleName().equals("CollectionShareScreen"),"Share panel missing");
                    click(mc.screen,"Save PNG"); break;
                case 7:
                    File[] exported=new File(mc.gameDir,"screenshots/mmm").listFiles((dir,name)->name.endsWith(".png"));
                    if(exported==null || exported.length==0) {stage--;break;}
                    java.awt.image.BufferedImage image=javax.imageio.ImageIO.read(exported[0]);
                    check(image.getWidth()>50 && image.getWidth()<mc.width && image.getHeight()<mc.height,"Export not cropped");
                    verifyChrome(mc, false); shot(mc,"share");
                    click(mc.screen,"Back"); check(mc.screen==book,"Share lost parent");
                    mc.options.guiScale=4; mc.openScreen(menu); break;
                case 8:
                    shot(mc,"compact-settings"); click(menu,"Website Link"); key(mc.screen,(char)0,Keyboard.KEY_ESCAPE);
                    mc.openScreen(book); click(book,"Tier II"); break;
                case 9:
                    shot(mc,"compact-tier");
                    focus(book,"Tier II"); key(book,'e',mc.options.inventoryKey.getKeyCode());
                    check(mc.screen==null,"Inventory shortcut did not close the focused board");
                    System.out.println("MMM_BOARD_KEYBOARD_OK");
                    previousColor=Configs.Generic.MENU_HEX_COLOR.getStringValue();
                    Configs.Generic.MENU_OPACITY.setIntegerValue(25);
                    openColor(mc); break;
                case 10:
                    verifyColor(mc,"compact");
                    key(mc.screen,(char)0,Keyboard.KEY_ESCAPE);
                    check(Configs.Generic.MENU_HEX_COLOR.getStringValue().equals(previousColor),"Cancel persisted color");
                    mc.options.guiScale=2; openColor(mc); break;
                case 11:
                    verifyColor(mc,"wide"); click(mc.screen,"Apply");
                    check(mc.screen==menu,"Apply lost board");
                    check((com.mmm.legacy.settings.SettingsUi.color(Configs.Generic.MENU_HEX_COLOR.getStringValue()) & 0xffffff)==0x804020,"Apply lost RGB");
                    Configs.Generic.MENU_OPACITY.resetToDefault();
                    var scoreboard=mc.world.getScoreboard();
                    scoreboard.setDisplayObjective(1,scoreboard.createObjective("Digs",net.minecraft.scoreboard.criterion.ScoreboardCriterion.DUMMY));
                    openRoute(mc); break;
                case 12:
                    String[] entry=ROUTES[route];
                    check(mc.screen!=null && entry[1].equals(mc.screen.getClass().getSimpleName()),"Wrong screen: "+entry[0]);
                    verifyChrome(mc,entry[1].equals("HudEditorScreen")); verifyBody(mc,entry[1]); shot(mc,"route-"+route);
                    if(entry[1].equals("HotkeyEditorScreen")) { click(mc.screen,"Unassign"); click(mc.screen,"Default"); }
                    if(entry[1].equals("GoalSoundSettingsScreen")) click(mc.screen,"50%");
                    Screen parent=(Screen)get(mc.screen,"parent");
                    key(mc.screen,(char)0,Keyboard.KEY_ESCAPE); check(mc.screen==parent,"Wrong Back target: "+entry[0]);
                    System.out.println("MMM_BOARD_ROUTE_OK "+entry[0]+" -> "+entry[1]);
                    if(++route<ROUTES.length) {openRoute(mc);stage--;break;}
                    System.out.println("MMM_BOARD_EDITORS_OK count="+route); System.out.println("MMM_BOARD_PROBE_OK");
                    stopped=true; mc.stop(); break;
                default: throw new AssertionError("Unknown stage");
            }
        } catch(Throwable failure) { stopped=true; failure.printStackTrace(); System.out.println("MMM_BOARD_PROBE_FAILED stage="+(stage-1)+" "+failure); mc.stop(); }
    }
    private static void chord(Minecraft mc,MmmHotkey binding,int modifier,int last) throws Exception {
        Field f=Keyboard.class.getDeclaredField("keyDownBuffer"); f.setAccessible(true);
        java.nio.ByteBuffer keys=(java.nio.ByteBuffer)f.get(null);
        byte prev=keys.get(modifier);
        try {
            if(modifier!=0) keys.put(modifier,(byte)1);
            binding.onKeyEvent(mc,last,0,1); binding.onKeyEvent(mc,last,0,0);
        } finally { keys.put(modifier,prev); }
    }
    private void verifyLauncher(Minecraft mc, boolean compact) throws Exception {
        String size = compact ? "compact" : "wide";
        switch (launcherStep) {
            case 0:
                check(mc.screen == menu && (compact ? menu.width < 460 : menu.width > 680), "Wrong test viewport");
                check((Integer)get(menu,"left") < (Integer)get(menu,"contentX"), "Category rail missing");
                check("Everyday settings".equals(((Page<?,?>)get(menu,"page")).title()),"Did not land directly in Everyday");
                check(((List<?>)get(menu,"rows")).size()==5,"Everyday controls not inline");
                ButtonWidget home = named(menu, ToolDirectory.HOTKEYS.label);
                TextFieldWidget search = (TextFieldWidget)get(menu,"search");
                check(home != null && home.x >= 0 && home.x + home.getWidth() <= menu.width && home.y+20 <= search.y,
                        "Hotkeys button clipped or overlaps search");
                plate(mc,home.x,home.y,"home-hotkeys-"+size);
                verifyInlinePixels(mc,size,"Everyday settings");
                shot(mc,"launcher-"+size);
                tab(menu,false); check((Integer)get(menu,"focusId")==home.id,"Header Hotkeys not keyboard reachable");
                key(menu,(char)13,Keyboard.KEY_RETURN); launcherStep++; return;
            case 1:
                check(mc.screen.getClass().getSimpleName().equals(TOOL_SCREENS[0]),"Home Hotkeys opened wrong screen");
                verifyBody(mc, TOOL_SCREENS[0]);
                for (char c : "Open Config Gui".toCharArray()) key(mc.screen,c,Keyboard.KEY_NONE);
                Screen bindings = mc.screen;
                click(bindings, "Open Config Gui");
                check(mc.screen.getClass().getSimpleName().equals("HotkeyEditorScreen"),"Binding did not open editor");
                click(mc.screen,"Cancel"); check(mc.screen == bindings,"Editor did not return to bindings");
                click(bindings,"Back"); check(mc.screen == menu,"Home Hotkeys lost parent");
                verifyRail(mc,size);
                launcherStep=5; return;
            case 5:
                verifyInlinePixels(mc,size,"HUD"); shot(mc,"inline-hud-"+size);
                click(menu,"Tools"); toolsPage = get(menu,"page");
                check(mc.screen == menu && ((com.mmm.ui.SettingsNavigation.Page<?,?>)toolsPage).title().equals("Tools"),"Missing Tools page");
                int columns=compact?1:2;
                ButtonWidget first=focusedTool(0), second=byId(menu,2001);
                check(second!=null && (first.y==second.y)==(columns==2),"Wrong Tools grid fixture");
                check(((Integer)get(menu,"capacity")<TOOLS.length)==compact,"Compact Tools must paginate");
                arrow(Keyboard.KEY_LEFT,0);
                arrow(Keyboard.KEY_RIGHT,1);
                arrow(Keyboard.KEY_DOWN,1+columns);
                arrow(Keyboard.KEY_UP,1);
                arrow(Keyboard.KEY_LEFT,0);
                for (int i=1;i<TOOLS.length;i++) arrow(Keyboard.KEY_RIGHT,i);
                arrow(Keyboard.KEY_RIGHT,TOOLS.length-1);
                check(((Integer)get(menu,"offset")>0)==compact,"Right did not reveal last Tools page");
                launcherStep=6; return;
            case 6:
                ButtonWidget last=focusedTool(TOOLS.length-1);
                var frame=Screenshot.take(mc.width,mc.height,mc.getRenderTarget());
                int scale=new Window(mc).getScale();
                try {
                    for (int x=last.x;x<last.x+last.getWidth();x++)
                        check((frame.getRGB(x*scale,(last.y-1)*scale)&0xffffff)==(com.mmm.legacy.BoardArt.TEXT&0xffffff),
                                "Missing keyboard focus on last visible tool");
                } finally { frame.flush(); }
                shot(mc,"tool-keys-"+size);
                key(menu,(char)13,Keyboard.KEY_RETURN);
                launcherStep=7; return;
            case 7:
                check(mc.screen!=menu && TOOL_SCREENS[TOOLS.length-1].equals(mc.screen.getClass().getSimpleName()),
                        "Enter did not open the focused tool");
                key(mc.screen,(char)0,Keyboard.KEY_ESCAPE);
                check(mc.screen==menu && get(menu,"page")==toolsPage,"Esc lost Tools origin");
                focusedTool(TOOLS.length-1);
                for (int i=TOOLS.length-2;i>=0;i--) arrow(Keyboard.KEY_LEFT,i);
                check((Integer)get(menu,"offset")==0,"Left did not restore first Tools page");
                arrow(Keyboard.KEY_UP,0);
                System.out.println("MMM_BOARD_TOOL_KEYS_OK "+size+" columns="+(compact?1:2)
                        +" pagedBothWays="+compact+" enter=Leaderboards");
                launcherStep=2; return;
            case 2:
                check(((com.mmm.ui.SettingsNavigation.Page<?,?>)toolsPage).children().size() == TOOLS.length,"Unsupported/missing tool tile");
                while (byId(menu,2000+toolIndex) == null) click(menu,">");
                // Wait for the framebuffer to reflect this page (including pagination).
                launcherStep++; return;
            case 3:
                ButtonWidget tile = byId(menu,2000+toolIndex);
                check(tile != null && TOOLS[toolIndex].label.equals(tile.message) && tile.getClass().getSimpleName().equals("LegacyMenuButton"),"Not a visible tool tile");
                plate(mc,tile.x,tile.y,"tool-tile-"+size);
                if (toolIndex == 0) shot(mc,"tools-"+size);
                mouse(menu,tile.x+tile.getWidth()/2,tile.y+9);
                launcherStep++; return;
            case 4:
                check(mc.screen != menu && mc.screen.getClass().getSimpleName().equals(TOOL_SCREENS[toolIndex]),
                        "Tile did not open " + TOOLS[toolIndex].label + " directly");
                verifyBody(mc,TOOL_SCREENS[toolIndex]);
                shot(mc,"tool-"+size+"-"+TOOLS[toolIndex].name());
                if (TOOLS[toolIndex] == ToolDirectory.SUMMARY) {
                    click(mc.screen,"Activity"); check((Integer)get(mc.screen,"tab")==1,"Activity tab not selected");
                    click(mc.screen,"Blocks"); check((Integer)get(mc.screen,"tab")==2,"Blocks tab not selected");
                }
                click(mc.screen,"Back");
                check(mc.screen == menu && get(menu,"page")==toolsPage, "Wrong tool Back target");
                System.out.println("MMM_BOARD_TOOL_OK "+size+" "+TOOLS[toolIndex].label);
                if (++toolIndex < TOOLS.length) { launcherStep=2; return; }
                click(menu,"Everyday settings");
                check("Everyday settings".equals(((Page<?,?>)get(menu,"page")).title()),"Tools lost Everyday rail route");
                launcherComplete[compact ? 1 : 0]=true; launcherStep=toolIndex=0;
                if (!compact) { mc.options.guiScale=2; mc.openScreen(menu); }
                return;
            default: throw new AssertionError("Unexpected launcher step");
        }
    }
    private ButtonWidget focusedTool(int index) throws Exception {
        ButtonWidget tile=byId(menu,2000+index);
        int start=(Integer)get(menu,"offset");
        check((Integer)get(menu,"focusId")==2000+index && tile!=null && TOOLS[index].label.equals(tile.message)
                && index>=start && index<start+(Integer)get(menu,"capacity"),"Focus is not on visible tool "+TOOLS[index].label);
        return tile;
    }
    private void arrow(int keyCode,int expected) throws Exception {
        key(menu,(char)0,keyCode); focusedTool(expected);
    }
    private void verifyRail(Minecraft mc,String size) throws Exception {
        var root=(Page<?,?>)get(get(menu,"board"),"home");
        int checked=0, forward=0, backward=0;
        for (var category:root.children()) {
            ButtonWidget rail=named(menu,category.title());
            int railStep=(Integer)get(menu,"railStep");
            check(rail!=null && rail.x+rail.getWidth()<(Integer)get(menu,"contentX") && rail.y+railStep<=menu.height,
                    "Offscreen category: "+category.title());
            mouse(menu,rail.x+rail.getWidth()/2,rail.y+railStep/2);
            check(get(menu,"page")==category && mc.screen==menu,"Rail did not switch directly: "+category.title());
            if ("Tools".equals(category.title())) continue;
            List<Object> expected=new java.util.ArrayList<>();
            for (var group:category.rowGroups()) expected.addAll(group.rows());
            List<?> visibleRows=(List<?>)get(menu,"rows");
            check(!expected.isEmpty() && expected.size()==visibleRows.size(),"Missing inline rows for "+category.title());
            List<Integer> previousStarts=new java.util.ArrayList<>();
            check((Integer)get(menu,"offset")==0,"Category did not open at its first row: "+category.title());
            for (int i=0;i<expected.size();i++) {
                if (i==(Integer)get(menu,"offset")+(Integer)get(menu,"capacity")) {
                    previousStarts.add((Integer)get(menu,"offset"));
                    click(menu,">");
                    check((Integer)get(menu,"offset")==i,"One Next click should show "+category.title()+" row "+i+", got "+get(menu,"offset"));
                    forward++;
                }
                int id=100+2*(i-(Integer)get(menu,"offset"));
                var control=byId(menu,id);
                check(visibleRows.get(i)==expected.get(i) && control!=null && control.y+20<=menu.height-25,
                        "Missing visible inline control: "+category.title()+" #"+i);
                if ("Everyday settings".equals(category.title()) && i==0) {
                    check("Mining Tracker".equals(get(expected.get(i),"label")), "Wrong first Everyday control");
                    var tracker=com.mmm.config.FeatureToggle.MMM_MINING_TRACKER;
                    boolean original=tracker.getBooleanValue();
                    mouse(menu,control.x+control.getWidth()/2,control.y+9);
                    check(mc.screen==menu && tracker.getBooleanValue()!=original, "Everyday toggle did not change live config");
                    mouse(menu,control.x+control.getWidth()/2,control.y+9);
                    check(tracker.getBooleanValue()==original, "Everyday toggle failed to restore");
                    System.out.println("MMM_BOARD_EVERYDAY_CLICK_OK "+size+" Mining Tracker");
                }
                checked++;
            }
            for (int j=previousStarts.size()-1;j>=0;j--) {
                click(menu,"<");
                check(previousStarts.get(j).equals(get(menu,"offset")),
                        "One Previous click should show "+category.title()+" row "+previousStarts.get(j)+", got "+get(menu,"offset"));
                backward++;
            }
        }
        click(menu,"HUD");
        if (named(menu,">")!=null) click(menu,">");
        Object hud=get(menu,"page"), offset=get(menu,"offset");
        row(mc,"Open Config Gui");
        check(mc.screen.getClass().getSimpleName().equals("HotkeyEditorScreen"),"Cross-category search missed binding");
        key(mc.screen,(char)0,Keyboard.KEY_ESCAPE);
        check(mc.screen==menu && "Open Config Gui".equals(get(menu,"query")),"Search result lost origin");
        key(menu,(char)0,Keyboard.KEY_ESCAPE);
        check(get(menu,"page")==hud && get(menu,"offset").equals(offset),"Clearing search lost category or offset");
        click(menu,"Website Link"); key(mc.screen,(char)0,Keyboard.KEY_ESCAPE);
        check(mc.screen==menu && get(menu,"page")==hud && get(menu,"offset").equals(offset),"Header link lost origin");
        key(menu,(char)0,Keyboard.KEY_ESCAPE);
        check(mc.screen==get(menu,"parent"),"Category Escape did not return to Settings caller");
        mc.openScreen(menu);
        check(forward>0 && forward==backward,"No complete pager round trip: "+size);
        System.out.println("MMM_BOARD_PAGER_OK "+size+" forward="+forward+" back="+backward);
        System.out.println("MMM_BOARD_INLINE_OK "+size+" categories="+root.children().size()+" controls="+checked);
    }
    private void verifyInlinePixels(Minecraft mc,String size,String category) throws Exception {
        var rail=named(menu,category);
        check(rail!=null && category.equals(((Page<?,?>)get(menu,"page")).title()),"Missing active category: "+category);
        int scale=new Window(mc).getScale(), x=(Integer)get(menu,"contentX")+6, ink=0;
        var frame=Screenshot.take(mc.width,mc.height,mc.getRenderTarget());
        try {
            check((frame.getRGB((rail.x+5)*scale,rail.y*scale)&0xffffff)==(com.mmm.legacy.BoardArt.ROSE&0xffffff),
                    "Active category not painted: "+category);
            for (Object y:(List<?>)get(menu,"rowY")) {
                int rowInk=0;
                for (int yy=(Integer)y+5; yy<(Integer)y+15; yy++) for (int xx=x; xx<x+50; xx++)
                    if ((frame.getRGB(xx*scale,yy*scale)&0xffffff)==(com.mmm.legacy.BoardArt.TEXT&0xffffff)) rowInk++;
                check(rowInk>4,"Unpainted inline row: "+category+" at "+y); ink+=rowInk;
            }
            check(ink>0,"No visible inline controls");
            System.out.println("MMM_BOARD_INLINE_PIXELS "+size+" "+category+" ink="+ink);
        } finally { frame.flush(); }
    }
    private void row(Minecraft mc,String label) throws Exception {
        mc.openScreen(menu);
        if(!((String)get(menu,"query")).isEmpty()) key(menu,(char)0,Keyboard.KEY_ESCAPE);
        for(char c:label.toCharArray()) key(menu,c,Keyboard.KEY_NONE);
        List<?> rows=(List<?>)get(menu,"rows");
        int index=0;
        while(index<rows.size() && !label.equalsIgnoreCase((String)get(rows.get(index),"label"))) index++;
        check(index<rows.size(),"Missing row: "+label);
        while(index>=(Integer)get(menu,"offset")+(Integer)get(menu,"capacity")) click(menu,">");
        int id=100+2*(index-(Integer)get(menu,"offset"));
        for(Field f:Screen.class.getDeclaredFields()) if(List.class.isAssignableFrom(f.getType())) {
            f.setAccessible(true);
            for(Object value:(List<?>)f.get(menu)) if(value instanceof ButtonWidget b && b.id==id) {
                mouse(menu,b.x+b.getWidth()/2,b.y+9); return;
            }
        }
        throw new AssertionError("Missing visible control: "+label);
    }
    private void openColor(Minecraft mc) throws Exception {
        row(mc,"MMM Menu"); check(mc.screen.getClass().getSimpleName().equals("ValueEditorScreen"),"No color editor");
        TextFieldWidget field=(TextFieldWidget)get(mc.screen,"input"); field.setText("#FF004020");
        int left=(Integer)get(mc.screen,"left"), w=(Integer)get(mc.screen,"panelWidth");
        mouse(mc.screen,left+42+(w-77+1)/2,101);
        field=(TextFieldWidget)get(mc.screen,"input");
        int red=(com.mmm.legacy.settings.SettingsUi.color(field.getText())>>>16)&255;
        check(red>=127 && red<=129,"Red ramp click did not set midpoint: "+field.getText());
        field.setText("#B0804020"); mc.screen.init();
        check("#B0804020".equals(((TextFieldWidget)get(mc.screen,"input")).getText()),"Resize lost draft");
    }
    private static void verifyChrome(Minecraft mc,boolean hud) {
        java.awt.image.BufferedImage frame=Screenshot.take(mc.width,mc.height,mc.getRenderTarget());
        int scale=new Window(mc).getScale(), y=hud?mc.screen.height-43:BoardEditorStyle.HEADER_HEIGHT-1;
        try {
            check((frame.getRGB(2*scale,y*scale+scale/2)&0xffffff)==(BoardEditorStyle.LINE&0xffffff),"Missing separator on "+mc.screen.getClass());
        } finally {frame.flush();}
    }
    private static void verifyBody(Minecraft mc,String name) throws Exception {
        int top;
        switch(name) {
            case "LegacyLinkScreen": case "TimerScreen": case "TimerResultsScreen": top=37; break;
            case "LeaderboardProfileScreen": top=36; break;
            case "LegacyHistoryScreen": top=106; break;
            case "LegacySessionScreen": top=70; break;
            case "LegacyHotkeyScreen": top=78; break;
            case "ProjectManagerScreen": top=57; break;
            case "LeaderboardScreen": top=(Integer)get(mc.screen,"listTop")-36; break;
            case "ScoreboardScreen": top=84; break;
            case "ScoreboardRecordsScreen": case "ScoreboardEditScreen": top=84; break;
            case "GoalSoundSettingsScreen": top=75; break;
            default: return;
        }
        int x=(Integer)get(mc.screen,name.startsWith("Leaderboard")?"x":"left");
        plate(mc,x,top,name);
    }
    private static void plate(Minecraft mc, int x, int top, String name) {
        int scale=new Window(mc).getScale();
        java.awt.image.BufferedImage frame=Screenshot.take(mc.width,mc.height,mc.getRenderTarget());
        try {
            int rim=frame.getRGB((x+5)*scale,top*scale)&0xffffff;
            int face=frame.getRGB((x+3)*scale,(top+3)*scale)&0xffffff;
            int corner=frame.getRGB(x*scale,top*scale)&0xffffff;
            check(rim==(BoardEditorStyle.LINE&0xffffff)||rim==(BoardEditorStyle.MUTED&0xffffff)||rim==(BoardEditorStyle.ROSE&0xffffff),"Missing body plate rim: "+name);
            check(face==(BoardEditorStyle.SURFACE&0xffffff)||face==(BoardEditorStyle.TILE&0xffffff)||face==0x39191b||face==(BoardEditorStyle.LINE&0xffffff),"Missing body plate face: "+name);
            check(corner!=rim && corner!=face,"Lost clipped body corner: "+name);
            System.out.println("MMM_BOARD_BODY_PIXELS "+name+" face="+Integer.toHexString(face)+" rim="+Integer.toHexString(rim));
        } finally {frame.flush();}
    }
    private static void verifyColor(Minecraft mc,String size) throws Exception {
        verifyChrome(mc,false); shot(mc,"color-"+size);
        java.awt.image.BufferedImage frame=Screenshot.take(mc.width,mc.height,mc.getRenderTarget());
        int scale=new Window(mc).getScale(), left=(Integer)get(mc.screen,"left"), w=(Integer)get(mc.screen,"panelWidth");
        int start=left+42, length=w-77, marker=start+Math.round(length*128/255f);
        try {
            int text=BoardEditorStyle.TEXT&0xffffff, floor=BoardEditorStyle.FLOOR&0xffffff;
            for(int px=0;px<scale;px++) check((frame.getRGB(marker*scale+px,100*scale)&0xffffff)==text,"Soft marker center");
            check((frame.getRGB(marker*scale-1,100*scale)&0xffffff)==floor,"Soft marker left");
            check((frame.getRGB((marker+1)*scale,100*scale)&0xffffff)==floor,"Soft marker right");
            int shades=0, previous=-1;
            for(int px=(start+3)*scale;px<(start+length-3)*scale;px++) {
                if(px>=(marker-2)*scale && px<=(marker+2)*scale) continue;
                int rgb=frame.getRGB(px,100*scale)&0xffffff;
                check((rgb&0xffff)==0x4020,"Wrong channel ramp: "+Integer.toHexString(rgb));
                if((rgb>>>16)!=previous) { previous=rgb>>>16; shades++; }
            }
            check(shades>=190,"Low-resolution gradient: "+shades);
            int ink=0;
            for(int x=left*scale;x<(left+30)*scale;x++) for(int y=99*scale;y<109*scale;y++)
                if((frame.getRGB(x,y)&0xffffff)==(BoardEditorStyle.MUTED&0xffffff)) ink++;
            check(ink>8,"Missing crisp Red label");
            for(int y=40*scale;y<frame.getHeight()-scale;y+=13*scale)
                check((frame.getRGB(2*scale,y)&0xffffff)==floor,"Game shows through color backdrop");
            System.out.println("MMM_BOARD_COLOR_PIXELS "+size+" scale="+scale+" shades="+shades+" marker="+marker+" labelInk="+ink);
        } finally { frame.flush(); }
    }
    private void openRoute(Minecraft mc) throws Exception {
        String[] steps=ROUTES[route][0].split(">");
        if(steps[0].startsWith("@")) {
            mc.openScreen(null);
            switch(steps[0]) {
                case "@summary": chord(mc,Hotkeys.OPEN_SUMMARY,Keyboard.KEY_LMENU,Keyboard.KEY_S); break;
                case "@history": chord(mc,Hotkeys.OPEN_HISTORY,Keyboard.KEY_LMENU,Keyboard.KEY_H); break;
                case "@scoreboard":
                    Hotkeys.OPEN_SCOREBOARD.setStorageString("F8"); chord(mc,Hotkeys.OPEN_SCOREBOARD,0,Keyboard.KEY_F8);
                    Hotkeys.OPEN_SCOREBOARD.resetToDefault(); break;
                case "@leaderboard": case "@efficiency": case "@chat":
                    CommandChat chat=new CommandChat(); mc.openScreen(chat);
                    if(steps[0].equals("@chat")) chat.clickSettings(mc);
                    else chat.command("/mmm "+steps[0].substring(1));
                    break;
                case "@edit": chord(mc,Hotkeys.EDIT_SCOREBOARD,0,Keyboard.KEY_C); break;
                case "@records": chord(mc,Hotkeys.EXPORT_SCOREBOARD,0,Keyboard.KEY_BACKSLASH); break;
                default: throw new AssertionError(steps[0]);
            }
        } else {
            row(mc,steps[0]);
            if(steps.length>1 && steps[1].equals("Profile")) {
                // Supply one offline ranking row; the click and profile render remain production code.
                Field data=mc.screen.getClass().getDeclaredField("data"); data.setAccessible(true);
                data.set(mc.screen,new LeaderboardData.Page(java.util.Collections.singletonList(new LeaderboardData.Row(1,"BoardTest",12,"Local fixture")),1,1,1,12));
                mouse(mc.screen,(Integer)get(mc.screen,"x")+10,(Integer)get(mc.screen,"listTop")+6);
            } else if(steps.length>1) click(mc.screen,steps[1]);
        }
    }
    private static void shot(Minecraft mc,String name) {
        Screenshot.take(mc.gameDir,"board-"+name+".png",mc.width,mc.height,mc.getRenderTarget());
    }
    private static void tab(Screen screen,boolean reverse) throws Exception {
        // Screen.isShiftDown reads LWJGL2's polled state, not the character passed to keyPressed.
        // Scope the modifier to this dispatch; no OS input is sent.
        Field f=Keyboard.class.getDeclaredField("keyDownBuffer"); f.setAccessible(true);
        java.nio.ByteBuffer keys=(java.nio.ByteBuffer)f.get(null);
        byte left=keys.get(Keyboard.KEY_LSHIFT), right=keys.get(Keyboard.KEY_RSHIFT);
        try {
            keys.put(Keyboard.KEY_LSHIFT,(byte)(reverse?1:0)); keys.put(Keyboard.KEY_RSHIFT,(byte)0);
            key(screen,'\t',Keyboard.KEY_TAB);
        } finally { keys.put(Keyboard.KEY_LSHIFT,left); keys.put(Keyboard.KEY_RSHIFT,right); }
    }
    private static String focusedName(Screen screen) throws Exception {
        ButtonWidget b=(ButtonWidget)get(screen,"focusedSlot");
        return b==null?null:b.message;
    }
    private static void focus(Screen screen,String name) throws Exception {
        int count=((List<?>)get(screen,"slots")).size()+1;
        // Always initiate keyboard navigation, even if the mouse last clicked this control.
        do { tab(screen,false); if(name.equals(focusedName(screen))) return; } while(--count>0);
        throw new AssertionError("Cannot focus "+name);
    }
    private static void verifyCycle(Screen screen,TextFieldWidget edit) throws Exception {
        List<?> slots=(List<?>)get(screen,"slots");
        List<ButtonWidget> expected=new java.util.ArrayList<>();
        for(Object obj:slots) {
            ButtonWidget b=(ButtonWidget)obj;
            if(b.visible && b.active) expected.add(b);
        }
        check(expected.size()<slots.size(),"Fixture needs a disabled pagination control");
        for(boolean reverse:new boolean[]{false,true}) {
            for(int i=0;i<expected.size();i++) {
                tab(screen,reverse);
                ButtonWidget next=expected.get(reverse?expected.size()-1-i:i);
                check(!edit.isFocused() && get(screen,"focusedSlot")==next,"Wrong tab stop: "+next.message);
            }
            tab(screen,reverse);
            check(edit.isFocused() && get(screen,"focusedSlot")==null,"Tab order did not wrap to search");
        }
    }
    private static void verifyFocusPixels(Minecraft mc,Screen screen) throws Exception {
        ButtonWidget b=(ButtonWidget)get(screen,"focusedSlot");
        check(b!=null && "Tier II".equals(b.message),"Wrong focus before screenshot");
        java.awt.image.BufferedImage frame=Screenshot.take(mc.width,mc.height,mc.getRenderTarget());
        int scale=new Window(mc).getScale(), top=(b.y-1)*scale+scale/2;
        try {
            for(int dx=0;dx<b.getWidth();dx++)
                check((frame.getRGB((b.x+dx)*scale+scale/2,top)&0xffffff)==0xf5f5f5,"Keyboard focus ring not visible");
        } finally { frame.flush(); }
        System.out.println("MMM_BOARD_FOCUS_PIXELS tier=II width="+b.getWidth());
    }
    private static void check(boolean ok,String description) { if(!ok) throw new AssertionError(description); }
    private static Object get(Object obj,String name) throws Exception {
        for(Class<?> t=obj.getClass();t!=null;t=t.getSuperclass()) try {
            Field field=t.getDeclaredField(name); field.setAccessible(true); return field.get(obj);
        } catch(NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
    private static Method method(String[] names,Class<?>... args) throws Exception {
        for(String name:names) try { Method m=Screen.class.getDeclaredMethod(name,args); m.setAccessible(true); return m; }
        catch(NoSuchMethodException ignored) {}
        throw new NoSuchMethodException(names[0]);
    }
    static void mouse(Screen screen,int x,int y) throws Exception {
        method(new String[]{"mouseClicked","func_73864_a","a"},int.class,int.class,int.class).invoke(screen,x,y,0);
    }
    static void key(Screen screen,char c,int code) throws Exception {
        method(new String[]{"keyPressed","func_73869_a","a"},char.class,int.class).invoke(screen,c,code);
    }
    static void click(Screen screen,String label) throws Exception {
        ButtonWidget b = named(screen,label);
        if (b == null) throw new AssertionError("No active "+label+" on "+screen.getClass());
        mouse(screen,b.x+b.getWidth()/2,b.y+9);
    }
    private static ButtonWidget byId(Screen screen,int id) throws Exception { return find(screen,null,id); }
    private static ButtonWidget named(Screen screen,String label) throws Exception { return find(screen,label,-1); }
    private static ButtonWidget find(Screen screen,String label,int id) throws Exception {
        for(Field f:Screen.class.getDeclaredFields()) if(List.class.isAssignableFrom(f.getType())) {
            f.setAccessible(true);
            for(Object value:(List<?>)f.get(screen)) if(value instanceof ButtonWidget) {
                ButtonWidget b=(ButtonWidget)value;
                if(b.active && b.visible && (label==null?id==b.id:label.equals(b.message))) return b;
            }
        }
        return null;
    }
    private static final class CommandChat extends ChatScreen {
        void collections() throws Exception {command("/mmm collections");}
        void command(String value) throws Exception {chatField.setText(value);keyPressed((char)13,Keyboard.KEY_RETURN);}
        void clickSettings(Minecraft mc) throws Exception {
            mouse(this,chatField.x+mc.textRenderer.getWidth("MINECRAFT")+mc.textRenderer.getWidth("MMM")+29,chatField.y-9);
        }
    }
}
