package com.rubixmod.gui;

import com.rubixmod.config.RubixConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class RubixScreen extends Screen {

    private static final int COLOR_BG        = 0xFF1A1A1A;
    private static final int COLOR_SIDEBAR    = 0xFF141414;
    private static final int COLOR_HEADER_BG  = 0xFF1F1F1F;
    private static final int COLOR_DIVIDER    = 0xFF333333;
    private static final int COLOR_WHITE      = 0xFFFFFFFF;
    private static final int COLOR_GRAY       = 0xFF888888;
    private static final int COLOR_SELECTED   = 0xFFFFAA00;
    private static final int COLOR_GREEN      = 0xFF55FF55;
    private static final int COLOR_RED        = 0xFFFF5555;

    private static final int SIDEBAR_WIDTH = 160;
    private static final int HEADER_HEIGHT = 50;

    private static final String MOD_VERSION = FabricLoader.getInstance()
            .getModContainer("rubixmod")
            .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString())
            .orElse("v1.0.0");

    private int selectedTab = 0;
    private final String[] TAB_NAMES = { "Bestiary", "Dungeons", "API" };

    private double lastMouseX, lastMouseY;
    private boolean wasPressed = false;

    private BestiaryHudEditorScreen editorOverlay = null;

    private String searchQuery = "";
    private EditBox searchBox;

    // Descriptor for each searchable setting
    private record Setting(String label, String description, String tabName) {}

    private static final List<Setting> ALL_SETTINGS = List.of(
            new Setting("Bestiary HUD",          "Shows bestiary completion info on screen",                  "Bestiary"),
            new Setting("Auto Track",             "HUD shows mobs you're actively killing (tab list)",        "Bestiary"),
            new Setting("Bestiary HUD Editor",    "Choose which mobs to track on the HUD",                   "Bestiary"),
            new Setting("Bestiary Alerts",        "Shows popups when you reach a new tier",                  "Bestiary"),
            new Setting("HUD Progress Display",   "Show kills toward max, or just toward the next tier",     "Bestiary"),
            new Setting("Bat Death Alert",        "Shows BAT KILLED title when you kill a bat in Catacombs", "Dungeons"),
            new Setting("Hypixel API Key",        "Configure your Hypixel API key",                          "API")
    );

    public RubixScreen() {
        super(Component.literal("RubixMod"));
    }

    @Override
    protected void init() {
        clearWidgets();
        RubixConfig cfg = RubixConfig.get();

        // Search box — right side of header, in the content area
        int searchW = Math.min(300, width - SIDEBAR_WIDTH - 40);
        searchBox = new EditBox(font, SIDEBAR_WIDTH + 20, 15, searchW, 20, Component.literal("Search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setHint(Component.literal("Search settings..."));
        addRenderableWidget(searchBox);

        // Persistent bottom-left HUD Layout button
        addRenderableWidget(Button.builder(
                        Component.literal("Edit HUD Layout"),
                        btn -> minecraft.setScreen(new HudEditScreen()))
                .bounds(10, height - 28, 110, 16)
                .build());

        boolean searching = !searchQuery.trim().isEmpty();

        if (searching) {
            addSearchResultButtons(cfg);
        } else if (selectedTab == 0) {
            addBestiaryButtons(cfg, HEADER_HEIGHT + 20);
        } else if (selectedTab == 1) {
            addDungeonsButtons(cfg, HEADER_HEIGHT + 20);
        }
    }

    // ── Button helpers ────────────────────────────────────────────────────────

    private void addBestiaryButtons(RubixConfig cfg, int contentY) {
        addRenderableWidget(Button.builder(
                        cfg.hudEnabled ? on() : off(),
                        btn -> { cfg.hudEnabled = !cfg.hudEnabled; RubixConfig.save(); rebuildWidgets(); })
                .bounds(width - 80, contentY + 22, 60, 16).build());

        addRenderableWidget(Button.builder(
                        cfg.hudAutoTrack ? on() : off(),
                        btn -> { cfg.hudAutoTrack = !cfg.hudAutoTrack; RubixConfig.save(); rebuildWidgets(); })
                .bounds(width - 80, contentY + 52, 60, 16).build());

        addRenderableWidget(Button.builder(
                        Component.literal("Edit"), btn -> openEditor())
                .bounds(width - 80, contentY + 82, 60, 16).build());

        addRenderableWidget(Button.builder(
                        cfg.bestiaryAlertsEnabled ? on() : off(),
                        btn -> { cfg.bestiaryAlertsEnabled = !cfg.bestiaryAlertsEnabled; RubixConfig.save(); rebuildWidgets(); })
                .bounds(width - 80, contentY + 112, 60, 16).build());

        addRenderableWidget(Button.builder(
                        cfg.hudPerTierMode ? perTier() : maxKills(),
                        btn -> { cfg.hudPerTierMode = !cfg.hudPerTierMode; RubixConfig.save(); rebuildWidgets(); })
                .bounds(width - 90, contentY + 142, 70, 16).build());
    }

    private void addDungeonsButtons(RubixConfig cfg, int contentY) {
        addRenderableWidget(Button.builder(
                        cfg.batDeathAlertEnabled ? on() : off(),
                        btn -> { cfg.batDeathAlertEnabled = !cfg.batDeathAlertEnabled; RubixConfig.save(); rebuildWidgets(); })
                .bounds(width - 80, contentY + 22, 60, 16).build());
    }

    private void addSearchResultButtons(RubixConfig cfg) {
        List<Setting> results = getSearchResults();
        int startY = HEADER_HEIGHT + 30;
        for (int i = 0; i < results.size(); i++) {
            addButtonForSetting(cfg, results.get(i), startY + i * 30);
        }
    }

    private void addButtonForSetting(RubixConfig cfg, Setting s, int rowY) {
        int btnY = rowY + 6;
        switch (s.label()) {
            case "Bestiary HUD" -> addRenderableWidget(Button.builder(
                            cfg.hudEnabled ? on() : off(),
                            btn -> { cfg.hudEnabled = !cfg.hudEnabled; RubixConfig.save(); rebuildAndRefocus(); })
                    .bounds(width - 80, btnY, 60, 16).build());
            case "Auto Track" -> addRenderableWidget(Button.builder(
                            cfg.hudAutoTrack ? on() : off(),
                            btn -> { cfg.hudAutoTrack = !cfg.hudAutoTrack; RubixConfig.save(); rebuildAndRefocus(); })
                    .bounds(width - 80, btnY, 60, 16).build());
            case "Bestiary HUD Editor" -> addRenderableWidget(Button.builder(
                            Component.literal("Edit"), btn -> openEditor())
                    .bounds(width - 80, btnY, 60, 16).build());
            case "Bestiary Alerts" -> addRenderableWidget(Button.builder(
                            cfg.bestiaryAlertsEnabled ? on() : off(),
                            btn -> { cfg.bestiaryAlertsEnabled = !cfg.bestiaryAlertsEnabled; RubixConfig.save(); rebuildAndRefocus(); })
                    .bounds(width - 80, btnY, 60, 16).build());
            case "HUD Progress Display" -> addRenderableWidget(Button.builder(
                            cfg.hudPerTierMode ? perTier() : maxKills(),
                            btn -> { cfg.hudPerTierMode = !cfg.hudPerTierMode; RubixConfig.save(); rebuildAndRefocus(); })
                    .bounds(width - 90, btnY, 70, 16).build());
            case "Bat Death Alert" -> addRenderableWidget(Button.builder(
                            cfg.batDeathAlertEnabled ? on() : off(),
                            btn -> { cfg.batDeathAlertEnabled = !cfg.batDeathAlertEnabled; RubixConfig.save(); rebuildAndRefocus(); })
                    .bounds(width - 80, btnY, 60, 16).build());
        }
    }

    private void rebuildAndRefocus() {
        rebuildWidgets();
        if (searchBox != null) setFocused(searchBox);
    }

    private List<Setting> getSearchResults() {
        String q = searchQuery.trim().toLowerCase();
        List<Setting> results = new ArrayList<>();
        for (Setting s : ALL_SETTINGS) {
            if (s.label().toLowerCase().contains(q) || s.description().toLowerCase().contains(q)
                    || s.tabName().toLowerCase().contains(q)) {
                results.add(s);
            }
        }
        return results;
    }

    // ── Label helpers ─────────────────────────────────────────────────────────

    private static Component on()       { return Component.literal("ON").withStyle(s -> s.withColor(0x55FF55)); }
    private static Component off()      { return Component.literal("OFF").withStyle(s -> s.withColor(0xFF5555)); }
    private static Component perTier()  { return Component.literal("Per Tier").withStyle(s -> s.withColor(0x55FFFF)); }
    private static Component maxKills() { return Component.literal("Max Kills").withStyle(s -> s.withColor(0xFFAA00)); }

    // ── Editor ────────────────────────────────────────────────────────────────

    private void openEditor() {
        editorOverlay = new BestiaryHudEditorScreen(() -> {
            editorOverlay = null;
            wasPressed = true;
        });
        editorOverlay.setup(minecraft, width, height);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Backgrounds
        g.fill(0, 0, width, height, COLOR_BG);
        g.fill(0, 0, SIDEBAR_WIDTH, height, COLOR_SIDEBAR);
        g.fill(0, 0, width, HEADER_HEIGHT, COLOR_HEADER_BG);

        // Dividers — vertical starts below header so it doesn't cut through title
        g.fill(0, HEADER_HEIGHT, width, HEADER_HEIGHT + 1, COLOR_DIVIDER);
        g.fill(SIDEBAR_WIDTH, HEADER_HEIGHT, SIDEBAR_WIDTH + 1, height, COLOR_DIVIDER);

        // Header text
        g.drawString(font, "RubixMod", 12, 14, COLOR_SELECTED);
        g.drawString(font, MOD_VERSION, 12, 27, COLOR_GRAY);

        // Sidebar tabs
        int tabY = HEADER_HEIGHT + 20;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean isSelected = i == selectedTab;
            boolean isHovered = mouseX >= 0 && mouseX <= SIDEBAR_WIDTH
                    && mouseY >= tabY - 4 && mouseY <= tabY + 12;
            int color = isSelected ? COLOR_SELECTED : (isHovered ? COLOR_WHITE : COLOR_GRAY);
            int tw = font.width(TAB_NAMES[i]);
            g.drawString(font, TAB_NAMES[i], (SIDEBAR_WIDTH - tw) / 2, tabY, color);
            if (isSelected) {
                g.fill(0, tabY - 2, 2, tabY + 10, COLOR_SELECTED);
            }
            tabY += 24;
        }

        // Content area
        int contentX = SIDEBAR_WIDTH + 20;
        int contentY = HEADER_HEIGHT + 20;
        RubixConfig cfg = RubixConfig.get();
        boolean searching = !searchQuery.trim().isEmpty();

        if (searching) {
            renderSearchResults(g, contentX, contentY);
        } else if (selectedTab == 0) {
            g.drawString(font, "Bestiary", contentX, contentY, COLOR_WHITE);
            g.fill(contentX, contentY + 12, width - 20, contentY + 13, COLOR_DIVIDER);
            g.drawString(font, "Bestiary HUD",        contentX, contentY + 22, COLOR_WHITE);
            g.drawString(font, "Shows bestiary completion info on screen",        contentX, contentY + 33, COLOR_GRAY);
            g.drawString(font, "Auto Track",           contentX, contentY + 52, COLOR_WHITE);
            g.drawString(font, "HUD shows mobs you're actively killing (tab list)", contentX, contentY + 63, COLOR_GRAY);
            g.drawString(font, "Bestiary HUD Editor",  contentX, contentY + 82, COLOR_WHITE);
            g.drawString(font, "Choose which mobs to track on the HUD",            contentX, contentY + 93, COLOR_GRAY);
            g.drawString(font, "Bestiary Alerts",      contentX, contentY + 112, COLOR_WHITE);
            g.drawString(font, "Shows popups when you reach a new tier",           contentX, contentY + 123, COLOR_GRAY);
            g.drawString(font, "HUD Progress Display", contentX, contentY + 142, COLOR_WHITE);
            g.drawString(font, "Show kills toward max, or just toward the next tier", contentX, contentY + 153, COLOR_GRAY);

        } else if (selectedTab == 1) {
            g.drawString(font, "Dungeons", contentX, contentY, COLOR_WHITE);
            g.fill(contentX, contentY + 12, width - 20, contentY + 13, COLOR_DIVIDER);
            g.drawString(font, "Bat Death Alert", contentX, contentY + 22, COLOR_WHITE);
            g.drawString(font, "Shows BAT KILLED title when you kill a bat in Catacombs", contentX, contentY + 33, COLOR_GRAY);

        } else if (selectedTab == 2) {
            g.drawString(font, "API", contentX, contentY, COLOR_WHITE);
            g.fill(contentX, contentY + 12, width - 20, contentY + 13, COLOR_DIVIDER);
            if (cfg.hasApiKey()) {
                g.drawString(font, "Hypixel API Key", contentX, contentY + 26, COLOR_WHITE);
                g.drawString(font, "API key is configured", contentX, contentY + 37, COLOR_GREEN);
            } else {
                g.drawString(font, "Hypixel API Key", contentX, contentY + 26, COLOR_WHITE);
                g.drawString(font, "No API key set — add hypixelApiKey to rubixmod.json", contentX, contentY + 37, COLOR_RED);
            }
        }

        // Bottom-left label
        g.drawString(font, "HUD Layout", 10, height - 42, COLOR_GRAY);

        super.render(g, mouseX, mouseY, delta);

        if (editorOverlay != null) {
            editorOverlay.render(g, mouseX, mouseY, delta);
        }
    }

    private void renderSearchResults(GuiGraphics g, int contentX, int contentY) {
        List<Setting> results = getSearchResults();
        int startY = contentY + 10;

        if (results.isEmpty()) {
            g.drawString(font, "No results found for \"" + searchQuery.trim() + "\"", contentX, startY, COLOR_GRAY);
            return;
        }

        for (int i = 0; i < results.size(); i++) {
            Setting s = results.get(i);
            int rowY = startY + i * 30;
            g.drawString(font, s.label(), contentX, rowY, COLOR_WHITE);
            // Tab badge right after the label
            int badgeX = contentX + font.width(s.label()) + 6;
            g.drawString(font, "[" + s.tabName() + "]", badgeX, rowY, COLOR_SELECTED);
            g.drawString(font, s.description(), contentX, rowY + 11, COLOR_GRAY);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (editorOverlay != null) { editorOverlay.mouseMoved(mouseX, mouseY); return; }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void tick() {
        super.tick();
        if (editorOverlay != null) { editorOverlay.tick(); return; }

        // Detect search box changes and rebuild so buttons match results
        if (searchBox != null) {
            String current = searchBox.getValue();
            if (!current.equals(searchQuery)) {
                searchQuery = current;
                rebuildWidgets();
                setFocused(searchBox);
                searchBox.moveCursorToEnd(false);
            }
        }

        long window = GLFW.glfwGetCurrentContext();
        boolean pressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (pressed && !wasPressed) {
            int tabY = HEADER_HEIGHT + 20;
            for (int i = 0; i < TAB_NAMES.length; i++) {
                if (lastMouseX >= 0 && lastMouseX <= SIDEBAR_WIDTH
                        && lastMouseY >= tabY - 4 && lastMouseY <= tabY + 12) {
                    selectedTab = i;
                    rebuildWidgets();
                    break;
                }
                tabY += 24;
            }
        }
        wasPressed = pressed;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (editorOverlay != null) return editorOverlay.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
