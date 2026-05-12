package com.mmm.gui;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.mmm.Reference;
import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.config.Hotkeys;
import com.mmm.hud.SessionHistoryScreen;
import com.mmm.hud.SummaryScreen;
import com.mmm.tracker.MiningStats;
import com.mmm.ui.MmmUi;
import com.mmm.ui.MmmSettingsScreen;
import com.mmm.ui.PlayerProfileScreen;
import com.mmm.ui.ProjectManagerScreen;
import com.mmm.ui.WebsiteLinkScreen;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigResettable;
import fi.dy.masa.malilib.config.options.BooleanHotkeyGuiWrapper;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonKeybind;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class GuiConfigs extends GuiConfigsBase
{
    public static ImmutableList<FeatureToggle> TWEAK_LIST = FeatureToggle.VALUES;
    private static final int LIST_Y = MmmUi.TOP_BAR_HEIGHT + 12;
    private static final int SIDEBAR_FIRST_ROW_Y = MmmUi.TOP_BAR_HEIGHT + 44;
    private static final int SIDEBAR_ROW_STEP = 31;
    private static ConfigGuiTab tab = ConfigGuiTab.TWEAKS;

    public GuiConfigs()
    {
        super(MmmUi.contentLeft(), LIST_Y, Reference.MOD_ID, null, Reference.MOD_NAME + " %s", String.format("%s", Reference.MOD_VERSION));
    }

    public static GuiConfigs createForTab(String tabName, Screen parent)
    {
        if (tabName != null)
        {
            for (ConfigGuiTab configTab : ConfigGuiTab.values())
            {
                if (configTab.name().equalsIgnoreCase(tabName))
                {
                    tab = configTab;
                    break;
                }
            }
        }

        GuiConfigs gui = new GuiConfigs();
        gui.setParent(parent);
        return gui;
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.clearOptions();

        int y = SIDEBAR_FIRST_ROW_Y;
        this.createSettingsButton(y);
        y += SIDEBAR_ROW_STEP;
        this.createSidebarButton(y, ConfigGuiTab.TWEAKS);
        y += SIDEBAR_ROW_STEP;
        this.createSidebarButton(y, ConfigGuiTab.HOTKEYS);
        y += SIDEBAR_ROW_STEP;
        this.createSidebarButton(y, ConfigGuiTab.PROJECTS);
        y += SIDEBAR_ROW_STEP;
        this.createSidebarButton(y, ConfigGuiTab.PROFILE);
        y += SIDEBAR_ROW_STEP;
        this.createSidebarButton(y, ConfigGuiTab.WEBSITE_LINK);
        y += SIDEBAR_ROW_STEP;
        this.createSidebarButton(y, ConfigGuiTab.HISTORY);
        y += SIDEBAR_ROW_STEP;
        this.createSidebarButton(y, ConfigGuiTab.SUMMARY);
    }

    @Override
    protected WidgetListConfigOptions createListWidget(int listX, int listY)
    {
        return new MmmConfigListWidget(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this.getConfigWidth(), 0.0F, this.useKeybindSearch(), this);
    }

    @Override
    protected void drawScreenBackground(DrawContext context, int mouseX, int mouseY)
    {
        MmmUi.backdrop(context, this.width, this.height);
        MmmUi.drawMmmTopBar(context, this.textRenderer, this.width);
        context.fill(0, MmmUi.TOP_BAR_HEIGHT, MmmUi.SIDEBAR_WIDTH, this.height, 0xE9080808);
        context.drawBorder(0, MmmUi.TOP_BAR_HEIGHT, MmmUi.SIDEBAR_WIDTH, this.height - MmmUi.TOP_BAR_HEIGHT, MmmUi.BORDER);
        MmmUi.drawSectionHeading(context, this.textRenderer, "MMM SCREENS", 12, MmmUi.TOP_BAR_HEIGHT + 16, MmmUi.SIDEBAR_WIDTH - 24);
        int bottomY = this.height - 42;
        context.drawBorder(12, bottomY, MmmUi.SIDEBAR_WIDTH - 24, 28, MmmUi.BORDER_SOFT);
        MmmUi.drawTextWithin(context, this.textRenderer, "MMM MOD", 20, bottomY + 7, MmmUi.SIDEBAR_WIDTH - 40, MmmUi.TEXT, false);
        MmmUi.drawTextWithin(context, this.textRenderer, Reference.MOD_VERSION, 20, bottomY + 18, MmmUi.SIDEBAR_WIDTH - 40, MmmUi.MUTED, false);
    }

    @Override
    protected void drawTitle(DrawContext context, int mouseX, int mouseY, float partialTicks)
    {
    }

    @Override
    public void drawContents(DrawContext context, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(context, mouseX, mouseY, partialTicks);
    }

    @Override
    protected int getConfigWidth()
    {
        return tab == ConfigGuiTab.GENERIC ? 180 : 260;
    }

    @Override
    protected boolean useKeybindSearch()
    {
        return tab == ConfigGuiTab.TWEAKS || tab == ConfigGuiTab.HOTKEYS;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs()
    {
        List<? extends IConfigBase> configs;

        if (tab == ConfigGuiTab.GENERIC)
        {
            configs = Configs.Generic.OPTIONS;
        }
        else if (tab == ConfigGuiTab.TWEAKS)
        {
            List<ConfigOptionWrapper> wrappers = new ArrayList<>();
            for (FeatureToggle toggle : TWEAK_LIST)
            {
                wrappers.addAll(ConfigOptionWrapper.createFor(List.of(wrapConfig(toggle))));
                if (toggle == FeatureToggle.TWEAK_PERIMETER_WALL_DIG_HELPER)
                {
                    wrappers.addAll(ConfigOptionWrapper.createFor(List.of(Configs.Generic.PERIMETER_OUTLINE_BLOCKS_LIST)));
                }
            }
            return wrappers;
        }
        else if (tab == ConfigGuiTab.HOTKEYS)
        {
            configs = Hotkeys.HOTKEY_LIST;
        }
        else
        {
            return Collections.emptyList();
        }

        return ConfigOptionWrapper.createFor(configs);
    }

    protected BooleanHotkeyGuiWrapper wrapConfig(FeatureToggle config)
    {
        return new BooleanHotkeyGuiWrapper(config.getName(), config, config.getKeybind());
    }

    private void createSettingsButton(int y)
    {
        ButtonGeneric button = new MmmSidebarButton(12, y, MmmUi.SIDEBAR_WIDTH - 24, 24, "Settings", false);
        this.addButton(button, new SettingsButtonListener(this));
    }

    private void createSidebarButton(int y, ConfigGuiTab configTab)
    {
        ButtonGeneric button = new MmmSidebarButton(12, y, MmmUi.SIDEBAR_WIDTH - 24, 24, configTab.getDisplayName(), tab == configTab);
        button.setEnabled(tab != configTab || configTab != ConfigGuiTab.TWEAKS && configTab != ConfigGuiTab.HOTKEYS);
        this.addButton(button, new TabButtonListener(configTab, this));
    }

    private static class MmmConfigListWidget extends WidgetListConfigOptions
    {
        private MmmConfigListWidget(int x, int y, int width, int height, int configWidth, float zLevel, boolean useKeybindSearch, GuiConfigsBase parent)
        {
            super(x, y, width, height, configWidth, zLevel, useKeybindSearch, parent);
        }

        @Override
        public void drawContents(DrawContext context, int mouseX, int mouseY, float partialTicks)
        {
            if (this.widgetSearchBar != null)
            {
                MmmUi.fieldShell(context, this.widgetSearchBar.getX() - 2, this.widgetSearchBar.getY() - 2, this.widgetSearchBar.getWidth() + 4, this.widgetSearchBar.getHeight() + 4, this.widgetSearchBar.hasFilter());
            }

            super.drawContents(context, mouseX, mouseY, partialTicks);
        }

        @Override
        protected WidgetConfigOption createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ConfigOptionWrapper entry)
        {
            return new MmmConfigOptionWidget(x, y, this.browserEntryWidth, this.browserEntryHeight, this.maxLabelWidth, this.configWidth, entry, listIndex, this.parent, this);
        }
    }

    private static class MmmConfigOptionWidget extends WidgetConfigOption
    {
        private MmmConfigOptionWidget(int x, int y, int width, int height, int maxNameLength, int configWidth, ConfigOptionWrapper wrapper, int listIndex, GuiConfigsBase host, WidgetListConfigOptionsBase<?, ?> parent)
        {
            super(x, y, width, height, maxNameLength, configWidth, wrapper, listIndex, host, parent);
            this.styleGeneratedButtons();
        }

        @Override
        protected void addConfigButtonEntry(int x, int y, IConfigResettable config, ButtonBase button)
        {
            this.styleButton(button);
            super.addConfigButtonEntry(x, y, config, button);
        }

        @Override
        protected void addKeybindResetButton(int x, int y, IKeybind keybind, ConfigButtonKeybind button)
        {
            this.styleButton(button);
            super.addKeybindResetButton(x, y, keybind, button);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean selected)
        {
            RowBounds bounds = this.getContentBounds();
            MmmUi.card(context, bounds.x(), this.y + 1, bounds.width(), Math.max(1, this.height - 3), MmmUi.CARD, MmmUi.BORDER_SOFT);
            this.drawStyledButtonShells(context, mouseX, mouseY);
            super.render(context, mouseX, mouseY, selected);
        }

        @Override
        protected GuiTextFieldGeneric createTextField(int x, int y, int width, int height)
        {
            GuiTextFieldGeneric field = super.createTextField(x + 5, y + 1, Math.max(32, width - 10), Math.max(12, height - 2));
            field.setCentered(false);
            field.setDrawsBackground(false);
            return field;
        }

        private void styleGeneratedButtons()
        {
            for (WidgetBase widget : this.subWidgets)
            {
                if (widget instanceof ButtonBase button)
                {
                    this.styleButton(button);
                }
            }
        }

        private void styleButton(ButtonBase button)
        {
            if (button instanceof ButtonGeneric generic)
            {
                generic.setRenderDefaultBackground(false);
                generic.setTextCentered(true);
            }
        }

        private void drawStyledButtonShells(DrawContext context, int mouseX, int mouseY)
        {
            for (WidgetBase widget : this.subWidgets)
            {
                if (widget instanceof ButtonBase button && button.getWidth() > 0 && button.getHeight() > 0)
                {
                    boolean hovered = button.isMouseOver(mouseX, mouseY);
                    MmmUi.card(context, button.getX(), button.getY(), button.getWidth(), button.getHeight(), hovered ? MmmUi.CARD : MmmUi.INSET, hovered ? MmmUi.ACCENT_BRIGHT : MmmUi.BORDER);
                }
            }

            if (this.textField != null && this.textField.getTextField() != null)
            {
                GuiTextFieldGeneric field = this.textField.getTextField();
                int x = field.getX();
                int y = field.getY();
                int width = field.getWidth();
                MmmUi.card(context, x - 5, y - 2, width + 10, 18, MmmUi.INSET, field.isFocusedWrapper() ? MmmUi.ACCENT_BRIGHT : MmmUi.BORDER);
            }
        }

        private RowBounds getContentBounds()
        {
            int minX = this.x;
            int maxX = this.x;

            for (WidgetBase widget : this.subWidgets)
            {
                minX = Math.min(minX, widget.getX());
                maxX = Math.max(maxX, widget.getX() + widget.getWidth());
            }

            if (this.textField != null && this.textField.getTextField() != null)
            {
                GuiTextFieldGeneric field = this.textField.getTextField();
                minX = Math.min(minX, field.getX());
                maxX = Math.max(maxX, field.getX() + field.getWidth());
            }

            int rowX = Math.max(this.x - 8, minX - 8);
            int rowRight = Math.min(this.x + this.width, Math.max(rowX + 96, maxX + 8));
            return new RowBounds(rowX, Math.max(1, rowRight - rowX));
        }

        private record RowBounds(int x, int width)
        {
        }
    }

    private static class MmmSidebarButton extends ButtonGeneric
    {
        private final boolean selected;

        private MmmSidebarButton(int x, int y, int width, int height, String label, boolean selected)
        {
            super(x, y, width, height, label);
            this.selected = selected;
            this.setRenderDefaultBackground(false);
            this.setTextCentered(false);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean selected)
        {
            if (!this.visible)
            {
                return;
            }

            boolean hovered = this.enabled && this.isMouseOver(mouseX, mouseY);
            int fill = this.selected ? 0x33E00000 : hovered ? 0x22E00000 : MmmUi.INSET;
            int border = this.selected || hovered ? MmmUi.ACCENT : MmmUi.BORDER_SOFT;
            MmmUi.card(context, this.x, this.y, this.width, this.height, fill, border);
            MmmUi.drawTextWithin(context, this.textRenderer, this.displayString, this.x + 8, this.y + 8, this.width - 16, this.selected ? MmmUi.TEXT : MmmUi.MUTED, false);
        }
    }

    private static class SettingsButtonListener implements IButtonActionListener
    {
        private final GuiConfigs parent;

        private SettingsButtonListener(GuiConfigs parent)
        {
            this.parent = parent;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            MinecraftClient.getInstance().setScreen(new MmmSettingsScreen(this.parent));
        }
    }

    private static class TabButtonListener implements IButtonActionListener
    {
        private final ConfigGuiTab tab;
        private final GuiConfigs parent;

        private TabButtonListener(ConfigGuiTab tab, GuiConfigs parent)
        {
            this.tab = tab;
            this.parent = parent;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            if (this.tab == ConfigGuiTab.PROJECTS)
            {
                MinecraftClient.getInstance().setScreen(new ProjectManagerScreen(this.parent));
                return;
            }

            if (this.tab == ConfigGuiTab.PROFILE)
            {
                MinecraftClient.getInstance().setScreen(new PlayerProfileScreen(this.parent));
                return;
            }

            if (this.tab == ConfigGuiTab.WEBSITE_LINK)
            {
                MinecraftClient.getInstance().setScreen(new WebsiteLinkScreen(this.parent));
                return;
            }

            if (this.tab == ConfigGuiTab.SUMMARY)
            {
                MinecraftClient.getInstance().setScreen(new SummaryScreen(MiningStats.getCurrentSession(), this.parent));
                return;
            }

            if (this.tab == ConfigGuiTab.HISTORY)
            {
                MinecraftClient.getInstance().setScreen(new SessionHistoryScreen(this.parent));
                return;
            }

            GuiConfigs.tab = this.tab;
            this.parent.reCreateListWidget();
            this.parent.getListWidget().resetScrollbarPosition();
            this.parent.initGui();
        }
    }

    private enum ConfigGuiTab
    {
        GENERIC("Generic"),
        TWEAKS("Feature Toggles"),
        HOTKEYS("Hotkeys"),
        PROJECTS("Projects"),
        PROFILE("Profile"),
        WEBSITE_LINK("Website Link"),
        SUMMARY("Summary"),
        HISTORY("History");

        private final String displayName;

        ConfigGuiTab(String displayName)
        {
            this.displayName = displayName;
        }

        public String getDisplayName()
        {
            return StringUtils.translate(this.displayName);
        }
    }
}
