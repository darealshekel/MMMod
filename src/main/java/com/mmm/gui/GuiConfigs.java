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
    public static ImmutableList<FeatureToggle> TWEAK_LIST = buildFeatureToggleList();
    private static final int LIST_Y = MmmUi.TOP_BAR_HEIGHT + 12;
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

        int y = MmmUi.sidebarStartY(this.height);
        int rowHeight = MmmUi.sidebarRowHeight(this.height);
        int rowStep = rowHeight + MmmUi.sidebarRowGap(this.height);
        int sidebarWidth = MmmUi.sidebarWidth(this.width);
        this.createSettingsButton(y, rowHeight, sidebarWidth);
        y += rowStep;
        this.createSidebarButton(y, rowHeight, sidebarWidth, ConfigGuiTab.TWEAKS);
        y += rowStep;
        this.createSidebarButton(y, rowHeight, sidebarWidth, ConfigGuiTab.HOTKEYS);
        y += rowStep;
        this.createSidebarButton(y, rowHeight, sidebarWidth, ConfigGuiTab.PROJECTS);
        y += rowStep;
        this.createSidebarButton(y, rowHeight, sidebarWidth, ConfigGuiTab.PROFILE);
        y += rowStep;
        this.createSidebarButton(y, rowHeight, sidebarWidth, ConfigGuiTab.WEBSITE_LINK);
        y += rowStep;
        this.createSidebarButton(y, rowHeight, sidebarWidth, ConfigGuiTab.HISTORY);
        y += rowStep;
        this.createSidebarButton(y, rowHeight, sidebarWidth, ConfigGuiTab.SUMMARY);
    }

    @Override
    protected WidgetListConfigOptions createListWidget(int listX, int listY)
    {
        int responsiveX = MmmUi.contentLeft(this.width);
        int responsiveWidth = Math.max(1, this.width - responsiveX - MmmUi.pagePad(this.width));
        return new MmmConfigListWidget(responsiveX, listY, responsiveWidth, this.getBrowserHeight(), Math.min(this.getConfigWidth(), Math.max(120, responsiveWidth - 36)), 0.0F, this.useKeybindSearch(), this);
    }

    @Override
    protected void drawScreenBackground(DrawContext context, int mouseX, int mouseY)
    {
        MmmUi.backdrop(context, this.width, this.height);
        MmmUi.drawMmmTopBar(context, this.textRenderer, this.width);
        int sidebarWidth = MmmUi.sidebarWidth(this.width);
        int sidePad = sidebarWidth < 120 ? 8 : 12;
        context.fill(0, MmmUi.TOP_BAR_HEIGHT, sidebarWidth, this.height, 0xE9080808);
        context.drawBorder(0, MmmUi.TOP_BAR_HEIGHT, sidebarWidth, Math.max(1, this.height - MmmUi.TOP_BAR_HEIGHT), MmmUi.BORDER);
        MmmUi.drawSectionHeading(context, this.textRenderer, "MMM SCREENS", sidePad, MmmUi.TOP_BAR_HEIGHT + (this.height < 360 ? 8 : 16), sidebarWidth - sidePad * 2);
        if (MmmUi.sidebarFooterVisible(this.height))
        {
            int bottomY = this.height - 42;
            context.drawBorder(sidePad, bottomY, sidebarWidth - sidePad * 2, 28, MmmUi.BORDER_SOFT);
            MmmUi.drawTextWithin(context, this.textRenderer, "MMM MOD", sidePad + 8, bottomY + 7, sidebarWidth - sidePad * 2 - 16, MmmUi.TEXT, false);
            MmmUi.drawTextWithin(context, this.textRenderer, Reference.MOD_VERSION, sidePad + 8, bottomY + 18, sidebarWidth - sidePad * 2 - 16, MmmUi.MUTED, false);
        }
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

    private void createSettingsButton(int y, int height, int sidebarWidth)
    {
        int sidePad = sidebarWidth < 120 ? 8 : 12;
        ButtonGeneric button = new MmmSidebarButton(sidePad, y, sidebarWidth - sidePad * 2, height, "Settings", false);
        this.addButton(button, new SettingsButtonListener(this));
    }

    private static ImmutableList<FeatureToggle> buildFeatureToggleList()
    {
        ImmutableList.Builder<FeatureToggle> builder = ImmutableList.builder();
        for (FeatureToggle toggle : FeatureToggle.VALUES)
        {
            if (!isHudContentToggle(toggle))
            {
                builder.add(toggle);
            }
        }
        return builder.build();
    }

    private static boolean isHudContentToggle(FeatureToggle toggle)
    {
        return switch (toggle)
        {
            case TWEAK_HUD,
                 TWEAK_DAILY_GOAL,
                 TWEAK_NOTIFICATIONS,
                 TWEAK_SOUND_ALERTS,
                 TWEAK_HUD_PROJECT,
                 TWEAK_HUD_TOTAL_MINED,
                 TWEAK_HUD_GOAL_PROGRESS,
                 TWEAK_HUD_BLOCKS_PER_HOUR,
                 TWEAK_HUD_ETA,
                 TWEAK_HUD_BOUNDING_BOX,
                 TWEAK_HUD_SPEED_GRAPH -> true;
            default -> false;
        };
    }

    private void createSidebarButton(int y, int height, int sidebarWidth, ConfigGuiTab configTab)
    {
        int sidePad = sidebarWidth < 120 ? 8 : 12;
        String label = sidebarWidth < 120 ? configTab.getCompactDisplayName() : configTab.getDisplayName();
        ButtonGeneric button = new MmmSidebarButton(sidePad, y, sidebarWidth - sidePad * 2, height, label, tab == configTab);
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
        public void render(int mouseX, int mouseY, boolean selected, DrawContext context)
        {
            RowBounds bounds = this.getContentBounds();
            MmmUi.card(context, bounds.x(), this.y + 1, bounds.width(), Math.max(1, this.height - 3), MmmUi.CARD, MmmUi.BORDER_SOFT);
            this.drawStyledButtonShells(context, mouseX, mouseY);
            super.render(mouseX, mouseY, selected, context);
        }

        @Override
        protected GuiTextFieldGeneric createTextField(int x, int y, int width, int height)
        {
            GuiTextFieldGeneric field = super.createTextField(x + 5, y + 1, Math.max(32, width - 10), Math.max(12, height - 2));

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
                    MmmUi.card(context, button.getX(), button.getY(), button.getWidth(), button.getHeight(), hovered ? MmmUi.CARD : MmmUi.INSET, hovered ? MmmUi.accent() : MmmUi.BORDER);
                }
            }

            if (this.textField != null && this.textField.getTextField() != null)
            {
                GuiTextFieldGeneric field = this.textField.getTextField();
                int x = field.getX();
                int y = field.getY();
                int width = field.getWidth();
                MmmUi.card(context, x - 5, y - 2, width + 10, 18, MmmUi.INSET, field.isFocusedWrapper() ? MmmUi.accent() : MmmUi.BORDER);
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
        public void render(int mouseX, int mouseY, boolean selected, DrawContext context)
        {
            if (!this.visible)
            {
                return;
            }

            boolean hovered = this.enabled && this.isMouseOver(mouseX, mouseY);
            int fill = this.selected ? MmmUi.accentSoft() : hovered ? MmmUi.accentHover() : MmmUi.INSET;
            int border = this.selected || hovered ? MmmUi.accent() : MmmUi.BORDER_SOFT;
            MmmUi.card(context, this.x, this.y, this.width, this.height, fill, border);
            int textY = this.y + Math.max(2, (this.height - 8) / 2);
            MmmUi.drawTextWithin(context, this.textRenderer, this.displayString, this.x + 8, textY, this.width - 16, this.selected ? MmmUi.TEXT : MmmUi.MUTED, false);
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
        GENERIC("Generic", "Generic"),
        TWEAKS("Feature Toggles", "Toggles"),
        HOTKEYS("Hotkeys", "Hotkeys"),
        PROJECTS("Projects", "Projects"),
        PROFILE("Profile", "Profile"),
        WEBSITE_LINK("Website Link", "Link"),
        SUMMARY("Summary", "Summary"),
        HISTORY("History", "History");

        private final String displayName;
        private final String compactDisplayName;

        ConfigGuiTab(String displayName, String compactDisplayName)
        {
            this.displayName = displayName;
            this.compactDisplayName = compactDisplayName;
        }

        public String getDisplayName()
        {
            return StringUtils.translate(this.displayName);
        }

        public String getCompactDisplayName()
        {
            return StringUtils.translate(this.compactDisplayName);
        }
    }
}
