package com.rubixmod.gui;

import com.rubixmod.config.RubixConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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

    private int selectedTab = 0;
    private final String[] TAB_NAMES = { "Bestiary", "Dungeons", "API" };

    private double lastMouseX, lastMouseY;
    private boolean wasPressed = false;

    private BestiaryHudEditorScreen editorOverlay = null;

    public RubixScreen() {
        super(Component.literal("RubixMod"));
    }

    @Override
    protected void init() {
        clearWidgets();
        RubixConfig cfg = RubixConfig.get();
        int contentY = HEADER_HEIGHT + 20;

        if (selectedTab == 0) {
            addRenderableWidget(Button.builder(
                            cfg.hudEnabled
                                    ? Component.literal("ON").withStyle(s -> s.withColor(0x55FF55))
                                    : Component.literal("OFF").withStyle(s -> s.withColor(0xFF5555)),
                            btn -> {
                                cfg.hudEnabled = !cfg.hudEnabled;
                                RubixConfig.save();
                                rebuildWidgets();
                            })
                    .bounds(width - 80, contentY + 22, 60, 16)
                    .build());

            addRenderableWidget(Button.builder(
                            Component.literal("Edit"),
                            btn -> openEditor())
                    .bounds(width - 80, contentY + 47, 60, 16)
                    .build());

            addRenderableWidget(Button.builder(
                            cfg.bestiaryAlertsEnabled
                                    ? Component.literal("ON").withStyle(s -> s.withColor(0x55FF55))
                                    : Component.literal("OFF").withStyle(s -> s.withColor(0xFF5555)),
                            btn -> {
                                cfg.bestiaryAlertsEnabled = !cfg.bestiaryAlertsEnabled;
                                RubixConfig.save();
                                rebuildWidgets();
                            })
                    .bounds(width - 80, contentY + 82, 60, 16)
                    .build());

            addRenderableWidget(Button.builder(
                            Component.literal("Edit Layout"),
                            btn -> minecraft.setScreen(new HudEditScreen()))
                    .bounds(width - 100, contentY + 112, 80, 16)
                    .build());

        } else if (selectedTab == 1) {
            addRenderableWidget(Button.builder(
                            cfg.batDeathAlertEnabled
                                    ? Component.literal("ON").withStyle(s -> s.withColor(0x55FF55))
                                    : Component.literal("OFF").withStyle(s -> s.withColor(0xFF5555)),
                            btn -> {
                                cfg.batDeathAlertEnabled = !cfg.batDeathAlertEnabled;
                                RubixConfig.save();
                                rebuildWidgets();
                            })
                    .bounds(width - 80, contentY + 22, 60, 16)
                    .build());
        }
    }

    private void openEditor() {
        editorOverlay = new BestiaryHudEditorScreen(() -> {
            editorOverlay = null;
            wasPressed = true;
        });
        editorOverlay.setup(minecraft, width, height);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, COLOR_BG);
        g.fill(0, 0, SIDEBAR_WIDTH, height, COLOR_SIDEBAR);
        g.fill(0, 0, width, HEADER_HEIGHT, COLOR_HEADER_BG);
        g.fill(0, HEADER_HEIGHT, width, HEADER_HEIGHT + 1, COLOR_DIVIDER);
        g.fill(SIDEBAR_WIDTH, 0, SIDEBAR_WIDTH + 1, height, COLOR_DIVIDER);

        g.drawString(font, "RubixMod", 12, 12, COLOR_SELECTED);
        g.drawString(font, "v1.0.0  —  Hypixel Skyblock client mod", 12, 26, COLOR_GRAY);

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

        int contentX = SIDEBAR_WIDTH + 20;
        int contentY = HEADER_HEIGHT + 20;
        RubixConfig cfg = RubixConfig.get();

        if (selectedTab == 0) {
            g.drawString(font, "Bestiary", contentX, contentY, COLOR_WHITE);
            g.fill(contentX, contentY + 12, width - 20, contentY + 13, COLOR_DIVIDER);
            g.drawString(font, "Bestiary HUD", contentX, contentY + 22, COLOR_WHITE);
            g.drawString(font, "Shows bestiary completion info on screen", contentX, contentY + 33, COLOR_GRAY);
            g.drawString(font, "Bestiary HUD Editor", contentX, contentY + 47, COLOR_WHITE);
            g.drawString(font, "Choose which mobs to track on the HUD", contentX, contentY + 58, COLOR_GRAY);
            g.drawString(font, "Bestiary Alerts", contentX, contentY + 82, COLOR_WHITE);
            g.drawString(font, "Shows popups when you reach a new tier", contentX, contentY + 93, COLOR_GRAY);
            g.drawString(font, "HUD Layout", contentX, contentY + 112, COLOR_WHITE);
            g.drawString(font, "Drag and resize HUD elements", contentX, contentY + 123, COLOR_GRAY);

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

        super.render(g, mouseX, mouseY, delta);

        // Render editor overlay on top of everything
        if (editorOverlay != null) {
            editorOverlay.render(g, mouseX, mouseY, delta);
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (editorOverlay != null) {
            editorOverlay.mouseMoved(mouseX, mouseY);
            return;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void tick() {
        super.tick();
        if (editorOverlay != null) {
            editorOverlay.tick();
            return;
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
        if (editorOverlay != null) {
            return editorOverlay.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}