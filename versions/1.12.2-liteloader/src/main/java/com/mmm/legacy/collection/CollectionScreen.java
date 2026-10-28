package com.mmm.legacy.collection;

import com.mmm.legacy.LegacyDrawContext;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Feather screen/input bridge local to the collection and leaderboard port. */
public abstract class CollectionScreen extends Screen {
    protected final Screen parent;
    protected final LegacyDrawContext draw = new LegacyDrawContext();
    protected TextFieldWidget searchField;
    protected final List<CollectionSlotButton> slots = new ArrayList<>();
    private boolean captured, repeat;
    private LegacyDrawContext.Clip clip;
    protected CollectionScreen(Screen parent) { this.parent = parent; }
    @Override public void init() {
        if (!captured) { captured = true; repeat = Keyboard.areRepeatEventsEnabled(); }
        Keyboard.enableRepeatEvents(true);
        buttons.clear(); slots.clear();
    }
    protected void rebuild() { init(); }
    protected void fit(String value, int x, int y, int available, int color) {
        textRenderer.draw(textRenderer.trim(value, Math.max(0, available)), x, y, color, false);
    }
    protected void right(String value, int x, int y, int available, int color) {
        String text = textRenderer.trim(value, Math.max(0, available));
        fit(text, x - textRenderer.getWidth(text), y, available, color);
    }
    protected void center(String value, int x, int y, int available, int color) {
        String text = textRenderer.trim(value, Math.max(0, available));
        fit(text, x + (available - textRenderer.getWidth(text)) / 2, y, available, color);
    }
    protected CollectionSlotButton slot(int x, int y, int size, String name, ItemStack icon,
            boolean book, Runnable action, Supplier<String> badge, Supplier<List<String>> tooltip) {
        var slot = new CollectionSlotButton(buttons.size(), x, y, size, name, icon, book, action, badge, tooltip);
        slots.add(slot); return addButton(slot);
    }
    protected ButtonWidget button(int x, int y, int width, String label, Runnable action) {
        var button = new ActionButton(buttons.size(), x, y, width, textRenderer.trim(label, Math.max(0, width - 6)), action);
        return addButton(button);
    }
    protected TextFieldWidget search(int x, int y, int width, String value) {
        var field = new TextFieldWidget(1000, textRenderer, x, y, width, 18);
        field.setMaxLength(64); field.setText(value); return field;
    }
    protected void searchChanged(String value) {}
    @Override protected void buttonClicked(ButtonWidget button) {
        if (button instanceof CollectionSlotButton slot) slot.press();
        else if (button instanceof ActionButton action) action.action.run();
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        if (searchField != null && searchField.isVisible()) searchField.mouseClicked(x, y, button);
        super.mouseClicked(x, y, button);
    }
    @Override protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) { close(); return; }
        if (searchField != null && searchField.isVisible() && searchField.isFocused()) {
            String before = searchField.getText();
            searchField.keyPressed(c, key);
            if (!before.equals(searchField.getText())) searchChanged(searchField.getText());
        } else if (key == minecraft.options.inventoryKey.getKeyCode()) close();
    }
    @Override public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scroll(Mouse.getEventX() * width / minecraft.width,
                height - Mouse.getEventY() * height / minecraft.height - 1, Integer.signum(wheel));
    }
    protected void scroll(int x, int y, int direction) {}
    protected void scissor(int x, int y, int right, int bottom) {
        clip = draw.clip(x, y, right, bottom);
    }
    protected void endScissor() { if (clip != null) { clip.close(); clip = null; } }
    @Override public void render(int mx, int my, float delta) {
        if (searchField != null && searchField.isVisible()) searchField.render();
        super.render(mx, my, delta);
        for (var slot : slots) if (slot.contains(mx, my)) {
            renderTooltip(slot.details.get(), mx, my); break;
        }
    }
    @Override public void tick() { if (searchField != null) searchField.tick(); }
    public void close() { minecraft.openScreen(parent); }
    @Override public void removed() { if (captured) Keyboard.enableRepeatEvents(repeat); captured = false; }
    @Override public boolean shouldPauseGame() { return minecraft != null && minecraft.isSingleplayer(); }

    private static final class ActionButton extends ButtonWidget {
        final Runnable action;
        ActionButton(int id, int x, int y, int width, String label, Runnable action) {
            super(id, x, y, Math.max(1, width), 20, label); this.action = action;
        }
    }
}
