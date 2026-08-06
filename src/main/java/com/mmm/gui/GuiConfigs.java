package com.mmm.gui;

import com.mmm.ui.CompatScreen;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.config.Hotkeys;
import com.mmm.hotkey.HotkeyChordCapture;
import com.mmm.hotkey.MmmHotkey;
import com.mmm.ui.MmmUi;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class GuiConfigs extends CompatScreen
{
    private static final int ROW_HEIGHT = 32;
    private static final int ROW_GAP = 4;
    private static final int HOTKEY_WIDTH = 84;
    private static final int MAX_LIST_WIDTH = 560;
    private static final int SEARCH_HEIGHT = 20;
    private static final int SEARCH_WIDTH = 260;
    private final Screen parent;
    private final List<RowTarget> rowTargets = new ArrayList<>();
    private final HotkeyChordCapture chordCapture = new HotkeyChordCapture();
    private final Set<Integer> heldCaptureKeys = new HashSet<>();
    private int scrollOffset;
    private int listTop;
    private int listBottom;
    private MmmHotkey capturing;
    private TextFieldWidget searchField;
    private String searchQuery = "";

    public GuiConfigs()
    {
        this(null);
    }

    public GuiConfigs(Screen parent)
    {
        super(Text.literal("Hotkeys"));
        this.parent = parent;
    }

    public static GuiConfigs createForTab(String ignoredTabName, Screen parent)
    {
        return new GuiConfigs(parent);
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.clearChildren();
        this.searchField = new TextFieldWidget(this.textRenderer, 0, 0, SEARCH_WIDTH - 10, SEARCH_HEIGHT, Text.literal("Search hotkeys"));
        this.searchField.setDrawsBackground(false);
        this.searchField.setEditableColor(MmmUi.TEXT);
        this.searchField.setUneditableColor(MmmUi.MUTED);
        this.searchField.setMaxLength(64);
        this.searchField.setPlaceholder(Text.literal("Search hotkeys..."));
        this.searchField.setText(this.searchQuery);
        this.searchField.setChangedListener(value -> {
            this.searchQuery = value;
            this.scrollOffset = 0;
        });
        this.addDrawableChild(this.searchField);

        this.listTop = MmmUi.TOP_BAR_HEIGHT + 62;
        this.listBottom = this.height - 10;
    }

    @Override
    public boolean shouldPause()
    {
        return MmmUi.shouldPauseGame();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta)
    {
        // MMM draws its own opaque background; suppress Minecraft's menu blur.
    }

    @Override
    public void close()
    {
        Configs.saveToFile();
        if (this.client != null)
        {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.backdrop(context, this.width, this.height);
        MmmUi.drawMmmScreensSidebar(context, this.textRenderer, this.width, this.height, mouseX, mouseY, "HOTKEYS");
        int left = MmmUi.contentLeft(this.width);
        int contentWidth = MmmUi.contentWidth(this.width);
        int listWidth = Math.max(1, Math.min(contentWidth, MAX_LIST_WIDTH));
        context.drawText(this.textRenderer, Text.literal("HOTKEYS"), left + 8, MmmUi.TOP_BAR_HEIGHT + 10, MmmUi.accent(), false);

        this.rowTargets.clear();
        int searchX = left + 8;
        int searchY = MmmUi.TOP_BAR_HEIGHT + 30;
        int searchWidth = Math.max(80, Math.min(SEARCH_WIDTH, listWidth - 16));
        boolean canClearSearch = !this.searchQuery.isBlank();
        int clearWidth = canClearSearch ? 20 : 0;
        this.searchField.setX(searchX + 5);
        this.searchField.setY(searchY + 5);
        this.searchField.setWidth(Math.max(40, searchWidth - 10 - clearWidth));
        MmmUi.fieldShell(context, searchX, searchY, searchWidth, SEARCH_HEIGHT, this.searchField.isFocused());
        if (canClearSearch)
        {
            int clearX = searchX + searchWidth - 19;
            boolean clearHovered = inside(mouseX, mouseY, clearX, searchY, 18, SEARCH_HEIGHT);
            MmmUi.card(context, clearX, searchY, 18, SEARCH_HEIGHT, clearHovered ? MmmUi.accentHover() : MmmUi.INSET,
                    clearHovered ? MmmUi.accent() : MmmUi.BORDER_SOFT);
            MmmUi.drawTextWithin(context, this.textRenderer, "X", clearX + 6, searchY + 6, 8, MmmUi.TEXT, false);
            this.rowTargets.add(new RowTarget(clearX, searchY, 18, SEARCH_HEIGHT, () -> {
                this.searchField.setText("");
                this.searchField.setFocused(true);
            }));
        }

        List<Row> rows = filteredRows();
        int top = MmmUi.TOP_BAR_HEIGHT + 62;
        int bottom = this.height - 10;
        this.listTop = top;
        this.listBottom = bottom;
        int maxVisibleHeight = Math.max(1, bottom - top);
        int maxScroll = Math.max(0, rows.size() * (ROW_HEIGHT + ROW_GAP) - maxVisibleHeight);
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));
        int y = top - this.scrollOffset;
        context.enableScissor(left, top, left + listWidth, bottom);
        for (Row row : rows)
        {
            if (y + ROW_HEIGHT >= top && y < bottom)
            {
                drawRow(context, row, left + 8, y, listWidth - 16, mouseX, mouseY);
            }
            y += ROW_HEIGHT + ROW_GAP;
        }
        context.disableScissor();

        if (rows.isEmpty())
        {
            MmmUi.drawTextWithin(context, this.textRenderer, "No hotkeys match your search.", left + 16, top + 12,
                    listWidth - 32, MmmUi.MUTED, false);
        }

        if (maxScroll > 0)
        {
            int trackX = left + listWidth - 4;
            int trackHeight = maxVisibleHeight;
            int thumbHeight = Math.max(20, trackHeight * trackHeight / (trackHeight + maxScroll));
            int thumbY = top + (int) ((trackHeight - thumbHeight) * (this.scrollOffset / (double) maxScroll));
            context.fill(trackX, top, trackX + 2, bottom, MmmUi.SCROLLBAR_TRACK);
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, MmmUi.scrollbarThumb());
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRow(DrawContext context, Row row, int x, int y, int width, int mouseX, int mouseY)
    {
        MmmUi.card(context, x, y, width, ROW_HEIGHT, MmmUi.CARD, MmmUi.BORDER_SOFT);
        int hotkeyWidth = Math.min(HOTKEY_WIDTH, Math.max(60, width / 5));
        int hotkeyX = x + width - hotkeyWidth - 10;
        int labelWidth = Math.max(40, hotkeyX - x - 20);
        MmmUi.drawTextWithin(context, this.textRenderer, row.label(), x + 9, y + 5, labelWidth, MmmUi.TEXT, false);
        MmmUi.drawTextWithin(context, this.textRenderer, row.description(), x + 9, y + 17, labelWidth, MmmUi.MUTED, false);

        String hotkeyStorage = this.capturing == row.hotkey()
                ? (this.chordCapture.isEmpty()
                    ? "PRESS KEYS"
                    : this.chordCapture.storageString())
                : row.hotkey().getStorageString();
        String hotkeyLabel = displayHotkey(hotkeyStorage);
        if (hotkeyLabel.isBlank())
        {
            hotkeyLabel = "UNBOUND";
        }
        boolean hotkeyHovered = inside(mouseX, mouseY, hotkeyX, y + 7, hotkeyWidth, 18);
        MmmUi.card(context, hotkeyX, y + 7, hotkeyWidth, 18, MmmUi.INSET,
                hotkeyHovered || this.capturing == row.hotkey() ? MmmUi.accent() : MmmUi.BORDER_SOFT);
        MmmUi.drawTextWithin(context, this.textRenderer, hotkeyLabel, hotkeyX + 5, y + 12, hotkeyWidth - 10, MmmUi.TEXT, false);
        this.rowTargets.add(new RowTarget(hotkeyX, y + 7, hotkeyWidth, 18, () -> beginCapture(row.hotkey())));
    }

    private void beginCapture(MmmHotkey hotkey)
    {
        this.capturing = hotkey;
        this.chordCapture.clear();
        this.heldCaptureKeys.clear();
    }

    private void finishCapture()
    {
        if (this.capturing != null && !this.chordCapture.isEmpty())
        {
            this.capturing.setStorageString(this.chordCapture.storageString());
            Configs.saveToFile();
        }
        this.capturing = null;
        this.chordCapture.clear();
        this.heldCaptureKeys.clear();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (MmmUi.handleMmmScreensSidebarClick(this, this.parent, mouseX, mouseY, "HOTKEYS"))
        {
            this.capturing = null;
            this.chordCapture.clear();
            return true;
        }

        if (this.capturing != null)
        {
            this.chordCapture.press("MOUSE_" + (button + 1));
            finishCapture();
            return true;
        }

        if (button == 0)
        {
            for (RowTarget target : this.rowTargets)
            {
                if (inside(mouseX, mouseY, target.x(), target.y(), target.width(), target.height()))
                {
                    target.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        this.scrollOffset = Math.max(0, this.scrollOffset - (int) Math.signum(verticalAmount) * 32);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (this.capturing != null)
        {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE)
            {
                this.capturing.setStorageString("");
                this.capturing = null;
                this.chordCapture.clear();
                this.heldCaptureKeys.clear();
                Configs.saveToFile();
                return true;
            }
            if ((keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) && this.chordCapture.isEmpty())
            {
                this.capturing.setStorageString("");
                this.capturing = null;
                Configs.saveToFile();
                return true;
            }

            this.heldCaptureKeys.add(keyCode);
            this.chordCapture.press(keyName(keyCode, scanCode));
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE)
        {
            if (this.searchField != null && !this.searchField.getText().isBlank())
            {
                this.searchField.setText("");
                return true;
            }
            this.close();
            return true;
        }

        if (this.searchField != null && this.searchField.isFocused()
                && this.searchField.keyPressed(new KeyInput(keyCode, scanCode, 0)))
        {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers)
    {
        if (this.capturing != null && !this.chordCapture.isEmpty())
        {
            this.heldCaptureKeys.remove(keyCode);
            if (this.heldCaptureKeys.isEmpty())
            {
                finishCapture();
            }
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers)
    {
        if (this.capturing != null)
        {
            return true;
        }
        if (super.charTyped(chr, modifiers))
        {
            return true;
        }
        if (this.searchField != null
                && (Character.isLetterOrDigit(chr) || Character.isWhitespace(chr)))
        {
            this.searchField.setFocused(true);
            return this.searchField.charTyped(new CharInput(chr, modifiers));
        }
        return false;
    }

    private List<Row> rows()
    {
        List<Row> rows = new ArrayList<>();
        for (MmmHotkey hotkey : Hotkeys.HOTKEY_LIST)
        {
            rows.add(new Row(hotkey.getPrettyName(), hotkey.getComment(), hotkey));
        }
        for (FeatureToggle toggle : FeatureToggle.VALUES)
        {
            rows.add(new Row(toggle.getPrettyName(), toggle.getComment(), toggle.getHotkey()));
        }
        return rows;
    }

    private List<Row> filteredRows()
    {
        String query = this.searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank())
        {
            return rows();
        }

        List<Row> filtered = new ArrayList<>();
        for (Row row : rows())
        {
            String searchable = String.join(" ",
                    row.label(),
                    row.description(),
                    row.hotkey().getStorageString(),
                    displayHotkey(row.hotkey().getStorageString())).toLowerCase(Locale.ROOT);
            if (searchable.contains(query))
            {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private static String displayHotkey(String storage)
    {
        if (storage == null)
        {
            return "";
        }
        return storage.replace(',', '+');
    }

    private static String keyName(int keyCode, int scanCode)
    {
        String key = InputUtil.fromKeyCode(new KeyInput(keyCode, scanCode, 0)).getTranslationKey();
        if (key.startsWith("key.keyboard."))
        {
            key = key.substring("key.keyboard.".length());
        }
        return key.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height)
    {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private record Row(String label, String description, MmmHotkey hotkey) {}
    private record RowTarget(int x, int y, int width, int height, Runnable action) {}
}