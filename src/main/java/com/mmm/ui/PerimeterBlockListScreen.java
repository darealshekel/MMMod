package com.mmm.ui;

import com.mmm.config.Configs;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class PerimeterBlockListScreen extends Screen
{
    private static final int ROW_HEIGHT = 30;
    private static final int ROW_GAP = 4;
    private static final int FIELD_HEIGHT = 20;
    private final Screen parent;
    private final List<String> blocks = new ArrayList<>();
    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private TextFieldWidget blockField;
    private int scrollOffset;
    private String status = "Add the floor blocks the helper must protect.";
    private boolean statusError;

    public PerimeterBlockListScreen(Screen parent)
    {
        super(Text.literal("Perimeter Block List"));
        this.parent = parent;
        this.blocks.addAll(Configs.Generic.PERIMETER_OUTLINE_BLOCKS_LIST.getStrings());
    }

    @Override
    protected void init()
    {
        MmmUi.ensureCursorVisible();
        this.clearChildren();
        this.blockField = new TextFieldWidget(this.textRenderer, 0, 0, 180, FIELD_HEIGHT, Text.literal("Block ID"));
        this.blockField.setDrawsBackground(false);
        this.blockField.setEditableColor(MmmUi.TEXT);
        this.blockField.setMaxLength(128);
        this.blockField.setPlaceholder(Text.literal("minecraft:netherrack"));
        this.addDrawableChild(this.blockField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        MmmUi.ensureCursorVisible();
        this.clickTargets.clear();
        Layout layout = layout();
        MmmUi.backdrop(context, this.width, this.height);
        MmmUi.drawMmmScreensSidebar(context, this.textRenderer, this.width, this.height, mouseX, mouseY, "SETTINGS");
        MmmUi.card(context, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), MmmUi.PANEL, MmmUi.BORDER);
        MmmUi.drawSectionHeading(context, this.textRenderer, "PERIMETER BLOCK LIST", layout.contentX(), layout.contentY(), layout.contentWidth());
        MmmUi.drawTextWithin(context, this.textRenderer, "The helper prevents digging below these configured surface blocks.",
                layout.contentX(), layout.contentY() + 18, layout.contentWidth(), MmmUi.MUTED, false);

        int addWidth = Math.min(70, Math.max(52, layout.contentWidth() / 5));
        int fieldWidth = Math.max(50, layout.contentWidth() - addWidth - 6);
        int inputY = layout.inputY();
        MmmUi.card(context, layout.contentX(), inputY, fieldWidth, FIELD_HEIGHT, MmmUi.INSET,
                this.blockField.isFocused() ? MmmUi.accent() : MmmUi.BORDER_SOFT);
        this.blockField.setX(layout.contentX() + 6);
        this.blockField.setY(inputY + 6);
        this.blockField.setWidth(Math.max(24, fieldWidth - 12));
        drawButton(context, layout.contentX() + fieldWidth + 6, inputY, addWidth, FIELD_HEIGHT, "ADD", mouseX, mouseY, this::addBlock);

        int visibleRows = visibleRows(layout);
        int maxScroll = Math.max(0, this.blocks.size() - visibleRows);
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));
        if (this.blocks.isEmpty())
        {
            MmmUi.card(context, layout.contentX(), layout.listTop(), layout.contentWidth(), 38, MmmUi.CARD, MmmUi.BORDER_SOFT);
            MmmUi.drawTextWithin(context, this.textRenderer, "No protected blocks configured.", layout.contentX() + 10,
                    layout.listTop() + 10, layout.contentWidth() - 20, MmmUi.MUTED, false);
        }
        else
        {
            int end = Math.min(this.blocks.size(), this.scrollOffset + visibleRows);
            int rowY = layout.listTop();
            for (int index = this.scrollOffset; index < end; index++)
            {
                String blockId = this.blocks.get(index);
                drawBlockRow(context, blockId, layout.contentX(), rowY, layout.contentWidth(), mouseX, mouseY);
                rowY += ROW_HEIGHT + ROW_GAP;
            }
        }

        int statusColor = this.statusError ? MmmUi.ERROR : MmmUi.MUTED;
        MmmUi.drawTextWithin(context, this.textRenderer, this.status, layout.contentX(), layout.statusY(),
                Math.max(1, layout.contentWidth() - 72), statusColor, false);
        drawButton(context, layout.panelX() + layout.panelWidth() - 70, layout.statusY() - 6,
                58, FIELD_HEIGHT, "DONE", mouseX, mouseY, this::close);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawBlockRow(DrawContext context, String blockId, int x, int y, int width, int mouseX, int mouseY)
    {
        MmmUi.card(context, x, y, width, ROW_HEIGHT, MmmUi.CARD, MmmUi.BORDER_SOFT);
        String canonical = canonicalBlockId(blockId);
        boolean valid = canonical != null;
        String title = valid ? blockName(canonical) : "Unknown block";
        MmmUi.drawTextWithin(context, this.textRenderer, title, x + 9, y + 6, width - 50,
                valid ? MmmUi.TEXT : MmmUi.ERROR, false);
        MmmUi.drawTextWithin(context, this.textRenderer, valid ? canonical : blockId, x + 9, y + 17,
                width - 50, MmmUi.MUTED, false);
        drawButton(context, x + width - 31, y + 5, 22, 20, "X", mouseX, mouseY, () -> removeBlock(blockId));
    }

    private void addBlock()
    {
        String requested = this.blockField == null ? "" : this.blockField.getText().trim();
        if (requested.isEmpty())
        {
            setStatus("Enter a block ID first.", true);
            return;
        }
        String canonical = canonicalBlockId(requested);
        if (canonical == null)
        {
            setStatus("Unknown block: " + requested, true);
            return;
        }
        boolean duplicate = this.blocks.stream().map(PerimeterBlockListScreen::canonicalBlockId)
                .anyMatch(canonical::equals);
        if (duplicate)
        {
            setStatus(canonical + " is already in the list.", true);
            return;
        }
        this.blocks.add(canonical);
        persist();
        this.blockField.setText("");
        this.blockField.setFocused(true);
        this.scrollOffset = Math.max(0, this.blocks.size() - visibleRows(layout()));
        setStatus("Added " + blockName(canonical) + ".", false);
    }

    private void removeBlock(String blockId)
    {
        if (this.blocks.remove(blockId))
        {
            persist();
            setStatus("Removed " + (canonicalBlockId(blockId) == null ? blockId : blockName(canonicalBlockId(blockId))) + ".", false);
        }
    }

    private void persist()
    {
        Configs.Generic.PERIMETER_OUTLINE_BLOCKS_LIST.setStrings(List.copyOf(this.blocks));
        Configs.saveToFile();
    }

    private static String canonicalBlockId(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            Identifier identifier = new Identifier(normalized.contains(":") ? normalized : "minecraft:" + normalized);
            return Registries.BLOCK.containsId(identifier) ? identifier.toString() : null;
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private static String blockName(String canonical)
    {
        try
        {
            return Registries.BLOCK.get(new Identifier(canonical)).getName().getString();
        }
        catch (RuntimeException ignored)
        {
            return canonical;
        }
    }

    private void setStatus(String value, boolean error)
    {
        this.status = value;
        this.statusError = error;
    }

    private void drawButton(DrawContext context, int x, int y, int width, int height, String label,
                            int mouseX, int mouseY, Runnable action)
    {
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        context.fill(x, y, x + width, y + height, hovered ? MmmUi.accentHover() : MmmUi.INSET);
        context.drawBorder(x, y, width, height, hovered ? MmmUi.accent() : MmmUi.BORDER_SOFT);
        int textX = x + Math.max(3, (width - this.textRenderer.getWidth(label)) / 2);
        context.drawText(this.textRenderer, Text.literal(label), textX, y + 6, hovered ? MmmUi.TEXT : MmmUi.MUTED, false);
        this.clickTargets.add(new ClickTarget(x, y, width, height, action));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && MmmUi.handleMmmScreensSidebarClick(this, this.parent, mouseX, mouseY, "SETTINGS"))
        {
            return true;
        }
        if (button == 0)
        {
            for (ClickTarget target : List.copyOf(this.clickTargets))
            {
                if (target.contains(mouseX, mouseY))
                {
                    target.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount)
    {
        Layout layout = layout();
        if (mouseY >= layout.listTop() && mouseY < layout.statusY())
        {
            int maxScroll = Math.max(0, this.blocks.size() - visibleRows(layout));
            this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
        {
            addBlock();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE)
        {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close()
    {
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean shouldPause()
    {
        return MmmUi.shouldPauseGame();
    }

    @Override
    public void renderBackground(DrawContext context)
    {
    }

    private int visibleRows(Layout layout)
    {
        return Math.max(1, (layout.statusY() - layout.listTop() - 6) / (ROW_HEIGHT + ROW_GAP));
    }

    private Layout layout()
    {
        int availableWidth = Math.max(1, MmmUi.contentWidth(this.width) - 12);
        int panelWidth = Math.min(620, availableWidth);
        int panelX = MmmUi.centerContentX(this.width, panelWidth);
        int panelY = MmmUi.TOP_BAR_HEIGHT + 8;
        int panelHeight = Math.max(1, this.height - panelY - 8);
        int contentX = panelX + 12;
        int contentY = panelY + 12;
        int contentWidth = Math.max(1, panelWidth - 24);
        int inputY = contentY + 42;
        int listTop = inputY + FIELD_HEIGHT + 10;
        int statusY = panelY + panelHeight - 22;
        return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, contentY, contentWidth, inputY, listTop, statusY);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight, int contentX, int contentY,
                          int contentWidth, int inputY, int listTop, int statusY)
    {
    }

    private record ClickTarget(int x, int y, int width, int height, Runnable action)
    {
        private boolean contains(double mouseX, double mouseY)
        {
            return PerimeterBlockListScreen.contains(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }
}
