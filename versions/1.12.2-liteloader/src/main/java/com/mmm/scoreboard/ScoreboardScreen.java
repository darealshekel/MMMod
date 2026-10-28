package com.mmm.scoreboard;

import com.mmm.config.Configs;
import com.mmm.config.value.*;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Mouse;

public final class ScoreboardScreen extends ScoreboardBaseScreen {
    private final List<IConfigBase> options = Configs.Generic.SCOREBOARD_OPTIONS.stream()
            .filter(o -> o != Configs.Generic.SCOREBOARD_X && o != Configs.Generic.SCOREBOARD_Y
                    && o != Configs.Generic.SCOREBOARD_DEFAULT_TEAM_CHAT).toList();
    private int offset;
    public ScoreboardScreen(Screen parent) { super(parent, "Scoreboard"); }
    private int capacity() { return Math.max(1, (height - 118) / 26); }
    @Override public void init() {
        super.init();
        offset = Math.max(0, Math.min(offset, Math.max(0, options.size() - capacity())));
        int w = (panelWidth - 16) / 5;
        button(1, left, 40, w, "Move");
        button(2, left + (w + 4), 40, w, "Export");
        button(3, left + (w + 4) * 2, 40, w, "Record");
        button(4, left + (w + 4) * 3, 40, w, "Records");
        button(5, left + (w + 4) * 4, 40, w, "Edit");
        for (int i = 0; i < capacity() && offset + i < options.size(); i++) {
            IConfigBase option = options.get(offset + i);
            int y = 68 + i * 26, right = left + panelWidth;
            if (option instanceof IConfigBoolean bool)
                button(100 + i * 4, right - 130, y, 98, bool.getBooleanValue() ? "ON" : "OFF");
            else if (option instanceof ConfigOptionList list)
                button(100 + i * 4, right - 158, y, 126, textRenderer.trim(list.getOptionListValue().getDisplayName(), 118));
            else {
                button(100 + i * 4, right - 130, y, 22, "-");
                button(101 + i * 4, right - 56, y, 22, "+");
            }
            button(102 + i * 4, right - 28, y, 28, "R");
        }
        button(10, left, height - 38, 24, "<");
        button(11, left + 28, height - 38, 24, ">");
        button(12, left + panelWidth - 90, height - 38, 42, "<<");
        button(13, left + panelWidth - 44, height - 38, 44, ">>");
    }
    @Override public void render(int x, int y, float delta) {
        shell();
        for (int i = 0; i < capacity() && offset + i < options.size(); i++) {
            IConfigBase option = options.get(offset + i);
            text(textRenderer.trim(label(option.getName()), panelWidth - 170), left, 74 + i * 26, 0xFFFFFFFF);
            if (option instanceof IConfigInteger value)
                text(Integer.toString(value.getIntegerValue()), left + panelWidth - 103, 74 + i * 26, 0xFFDDDDDD);
            else if (option instanceof IConfigDouble value)
                text(String.format(Locale.US, "%.2f", value.getDoubleValue()), left + panelWidth - 103, 74 + i * 26, 0xFFDDDDDD);
        }
        super.render(x, y, delta);
    }
    private static String label(String value) {
        String label = value.replaceFirst("^scoreboard", "").replaceAll("([a-z])([A-Z])", "$1 $2");
        return label.isEmpty() ? value : Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
    @Override protected void buttonClicked(ButtonWidget button) {
        int id = button.id;
        if (id >= 100 && id < 100 + capacity() * 4) {
            IConfigBase option = options.get(offset + (id - 100) / 4);
            int action = (id - 100) % 4;
            if (action == 2 && option instanceof IConfigResettable reset) reset.resetToDefault();
            else if (option instanceof IConfigBoolean bool) bool.setBooleanValue(!bool.getBooleanValue());
            else if (option instanceof ConfigOptionList list) list.setValueFromString(list.getOptionListValue().cycle(true).getStringValue());
            else if (option instanceof IConfigInteger value) value.setIntegerValue(value.getIntegerValue() + (action == 0 ? -1 : 1));
            else if (option instanceof IConfigDouble value) value.setDoubleValue(Math.round((value.getDoubleValue() + (action == 0 ? -0.05 : 0.05)) * 100) / 100.0);
            if (option == Configs.Generic.SCOREBOARD_POSITION || option == Configs.Generic.SCOREBOARD_Y_OFFSET) ScoreboardHudRenderer.resetPosition();
            ScoreboardState.resetPage();
            changed(); init();
        } else switch (id) {
            case 1 -> minecraft.openScreen(new ScoreboardMoveScreen(this));
            case 2 -> io(() -> "Exported " + ScoreboardService.exportCurrent().getFileName());
            case 3 -> io(() -> "Recorded " + ScoreboardService.recordCurrent().displayName());
            case 4 -> minecraft.openScreen(new ScoreboardRecordsScreen(this));
            case 5 -> ScoreboardService.getSidebarObjective(minecraft).ifPresentOrElse(
                    objective -> minecraft.openScreen(new ScoreboardEditScreen(this, objective)), () -> status = "No sidebar scoreboard");
            case 10, 11 -> { offset += id == 10 ? -capacity() : capacity(); init(); }
            case 12 -> status = ScoreboardService.pageUp() ? "Previous scoreboard page" : "First scoreboard page";
            case 13 -> status = ScoreboardService.pageDown() ? "Next scoreboard page" : "Last scoreboard page";
            default -> super.buttonClicked(button);
        }
    }
    @Override public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) { offset += wheel > 0 ? -1 : 1; init(); }
    }
}
