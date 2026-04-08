package com.rubixmod.gui;

import com.rubixmod.bestiary.BestiaryTierUpHandler;
import com.rubixmod.config.RubixConfig;
import com.rubixmod.hud.BestiaryHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HudEditScreen extends Screen {

    private static final int COLOR_HOVERED = 0x8055FF55;
    private static final int COLOR_NORMAL  = 0x80FFFFFF;
    private static final int COLOR_BORDER  = 0xFFFFAA00;
    private static final int COLOR_GRAY    = 0xFF888888;
    private static final int COLOR_WHITE   = 0xFFFFFFFF;
    private static final int COLOR_DISABLED = 0x40888888;

    private int dragging = 0;
    private int dragOffsetX, dragOffsetY;
    private int hovered = 0;
    private double lastMouseX, lastMouseY;

    public HudEditScreen() {
        super(Component.literal("Edit HUD"));
        // Only add fake popups if alerts are enabled
        BestiaryTierUpHandler.setPreviewMode(true);
        RubixConfig cfg = RubixConfig.get();
        if (cfg.bestiaryAlertsEnabled && BestiaryTierUpHandler.getActivePopups().isEmpty()) {
            BestiaryTierUpHandler.onTierUp("Zombie", 3, 1);
            BestiaryTierUpHandler.onTierUp("Spider", 5, 1);
        }
    }

    @Override
    protected void init() {
        RubixConfig cfg = RubixConfig.get();
        if (cfg.alertsX < 0) cfg.alertsX = width / 2f;
        if (cfg.alertsY < 0) cfg.alertsY = height / 3f;

        addRenderableWidget(Button.builder(
                        Component.literal("Done"),
                        btn -> onClose())
                .bounds(width / 2 - 40, height - 28, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        RubixConfig cfg = RubixConfig.get();

        hovered = 0;
        if (cfg.hudEnabled && isOverHud(mouseX, mouseY, cfg)) hovered = 1;
        else if (cfg.bestiaryAlertsEnabled && isOverAlerts(mouseX, mouseY, cfg)) hovered = 2;

        // --- Bestiary HUD ---
        if (cfg.hudEnabled) {
            int hudX = (int) cfg.bestiaryHudX;
            int hudY = (int) cfg.bestiaryHudY;
            int hudW = (int) (BestiaryHud.HUD_WIDTH * cfg.bestiaryHudScale);
            int hudH = (int) (BestiaryHud.getHudHeight(cfg.trackedMobs.size()) * cfg.bestiaryHudScale);

            g.fill(hudX - 2, hudY - 2, hudX + hudW + 2, hudY + hudH + 2,
                    hovered == 1 ? COLOR_HOVERED : COLOR_NORMAL);
            drawBorder(g, hudX - 2, hudY - 2, hudX + hudW + 2, hudY + hudH + 2, COLOR_BORDER);
            BestiaryHud.renderBestiaryInfo(g, font, true);
            g.drawString(font, "Bestiary HUD", hudX, hudY - 14, COLOR_BORDER);

            if (hovered == 1) {
                g.drawCenteredString(font, String.format("Scale: %.1fx", cfg.bestiaryHudScale), width / 2, 20, COLOR_WHITE);
            }
        }

        // --- Bestiary Alerts ---
        if (cfg.bestiaryAlertsEnabled) {
            int ax = (int) cfg.alertsX;
            int ay = (int) cfg.alertsY;
            int aw = (int) (BestiaryHud.ALERTS_WIDTH * cfg.alertsScale);
            int ah = (int) (BestiaryHud.ALERTS_HEIGHT * cfg.alertsScale);

            g.fill(ax - aw / 2 - 2, ay - ah / 2 - 2, ax + aw / 2 + 2, ay + ah / 2 + 2,
                    hovered == 2 ? COLOR_HOVERED : COLOR_NORMAL);
            drawBorder(g, ax - aw / 2 - 2, ay - ah / 2 - 2, ax + aw / 2 + 2, ay + ah / 2 + 2, COLOR_BORDER);
            BestiaryHud.renderTierUpPopups(g, font, width, height, true);
            g.drawString(font, "Bestiary Alerts",
                    ax - font.width("Bestiary Alerts") / 2, ay - ah / 2 - 14, COLOR_BORDER);

            if (hovered == 2) {
                g.drawCenteredString(font, String.format("Scale: %.1fx", cfg.alertsScale), width / 2, 20, COLOR_WHITE);
            }
        }

        // --- Nothing enabled message ---
        if (!cfg.hudEnabled && !cfg.bestiaryAlertsEnabled) {
            g.drawCenteredString(font, "No HUD elements are enabled.", width / 2, height / 2 - 10, COLOR_GRAY);
            g.drawCenteredString(font, "Enable them in /rubix first.", width / 2, height / 2 + 4, COLOR_GRAY);
        }

        g.drawCenteredString(font, "Drag to move  |  Scroll to resize", width / 2, 8, COLOR_GRAY);

        super.render(g, mouseX, mouseY, delta);
    }

    private void drawBorder(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (dragging > 0) {
            RubixConfig cfg = RubixConfig.get();
            if (dragging == 1) {
                cfg.bestiaryHudX = (float) (mouseX - dragOffsetX);
                cfg.bestiaryHudY = (float) (mouseY - dragOffsetY);
                cfg.bestiaryHudX = Math.max(0, Math.min(cfg.bestiaryHudX, width - BestiaryHud.HUD_WIDTH));
                cfg.bestiaryHudY = Math.max(0, Math.min(cfg.bestiaryHudY, height - BestiaryHud.getHudHeight(cfg.trackedMobs.size())));
            } else {
                cfg.alertsX = (float) (mouseX - dragOffsetX);
                cfg.alertsY = (float) (mouseY - dragOffsetY);
                cfg.alertsX = Math.max(0, Math.min(cfg.alertsX, width));
                cfg.alertsY = Math.max(0, Math.min(cfg.alertsY, height));
            }
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        RubixConfig cfg = RubixConfig.get();
        float delta = verticalAmount > 0 ? 0.1f : -0.1f;
        if (cfg.hudEnabled && isOverHud((int) mouseX, (int) mouseY, cfg)) {
            cfg.bestiaryHudScale = Math.max(0.5f, Math.min(3.0f, cfg.bestiaryHudScale + delta));
            return true;
        }
        if (cfg.bestiaryAlertsEnabled && isOverAlerts((int) mouseX, (int) mouseY, cfg)) {
            cfg.alertsScale = Math.max(0.5f, Math.min(3.0f, cfg.alertsScale + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void tick() {
        super.tick();
        long window = GLFW.glfwGetCurrentContext();
        boolean pressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        RubixConfig cfg = RubixConfig.get();
        if (pressed && dragging == 0) {
            if (cfg.hudEnabled && isOverHud((int) lastMouseX, (int) lastMouseY, cfg)) {
                dragging = 1;
                dragOffsetX = (int) (lastMouseX - cfg.bestiaryHudX);
                dragOffsetY = (int) (lastMouseY - cfg.bestiaryHudY);
            } else if (cfg.bestiaryAlertsEnabled && isOverAlerts((int) lastMouseX, (int) lastMouseY, cfg)) {
                dragging = 2;
                dragOffsetX = (int) (lastMouseX - cfg.alertsX);
                dragOffsetY = (int) (lastMouseY - cfg.alertsY);
            }
        } else if (!pressed) {
            dragging = 0;
        }
    }

    @Override
    public void onClose() {
        BestiaryTierUpHandler.setPreviewMode(false);
        BestiaryTierUpHandler.clear();
        RubixConfig.save();
        super.onClose();
    }

    private boolean isOverHud(int mx, int my, RubixConfig cfg) {
        int x = (int) cfg.bestiaryHudX;
        int y = (int) cfg.bestiaryHudY;
        int w = (int) (BestiaryHud.HUD_WIDTH * cfg.bestiaryHudScale);
        int h = (int) (BestiaryHud.getHudHeight(cfg.trackedMobs.size()) * cfg.bestiaryHudScale);
        return mx >= x - 2 && mx <= x + w + 2 && my >= y - 2 && my <= y + h + 2;
    }

    private boolean isOverAlerts(int mx, int my, RubixConfig cfg) {
        int ax = (int) cfg.alertsX;
        int ay = (int) cfg.alertsY;
        int aw = (int) (BestiaryHud.ALERTS_WIDTH * cfg.alertsScale);
        int ah = (int) (BestiaryHud.ALERTS_HEIGHT * cfg.alertsScale);
        return mx >= ax - aw / 2 - 2 && mx <= ax + aw / 2 + 2
                && my >= ay - ah / 2 - 2 && my <= ay + ah / 2 + 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}