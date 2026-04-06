package com.rubixmod.hud;

import com.rubixmod.bestiary.BestiaryData;
import com.rubixmod.bestiary.BestiaryTierUpHandler;
import com.rubixmod.config.RubixConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class BestiaryHud {

    // Constants used by HudEditScreen for drag/resize bounds
    public static final int HUD_WIDTH     = 140;
    public static final int HUD_HEIGHT    = 120;
    public static final int ALERTS_WIDTH  = 200;
    public static final int ALERTS_HEIGHT = 60;

    private static final int COLOR_ORANGE  = 0xFFFFAA00;
    private static final int COLOR_WHITE   = 0xFFFFFFFF;
    private static final int COLOR_GRAY    = 0xFF888888;
    private static final int COLOR_GREEN   = 0xFF55FF55;
    private static final int COLOR_BG      = 0xCC111111;
    private static final int COLOR_BORDER  = 0xFFFFAA00;
    private static final int COLOR_BAR_BG  = 0xFF333333;
    private static final int COLOR_BAR_FG  = 0xFF55AA00;

    /** Called from HudRenderCallback every frame. */
    public static void render(GuiGraphics g, float scale) {
        RubixConfig cfg = RubixConfig.get();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        if (cfg.hudEnabled) {
            renderBestiaryInfo(g, font);
        }

        if (cfg.bestiaryAlertsEnabled) {
            renderTierUpPopups(g, font, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }

    /** Renders the bestiary HUD box. Also called directly by HudEditScreen. */
    public static void renderBestiaryInfo(GuiGraphics g, Font font) {
        RubixConfig cfg = RubixConfig.get();
        List tracked = cfg.trackedMobs;
        if (tracked.isEmpty()) return;

        int x       = (int) cfg.bestiaryHudX;
        int y       = (int) cfg.bestiaryHudY;
        int padding = 6;
        int barW    = HUD_WIDTH - padding * 2;
        int barH    = 5;
        int titleH  = 14;
        int rowH    = 22;
        int boxH    = titleH + tracked.size() * rowH + padding;

        // Background + border
        g.fill(x, y, x + HUD_WIDTH, y + boxH, COLOR_BG);
        g.fill(x, y, x + HUD_WIDTH, y + 1, COLOR_BORDER);
        g.fill(x, y + boxH - 1, x + HUD_WIDTH, y + boxH, COLOR_BORDER);
        g.fill(x, y, x + 1, y + boxH, COLOR_BORDER);
        g.fill(x + HUD_WIDTH - 1, y, x + HUD_WIDTH, y + boxH, COLOR_BORDER);

        g.drawString(font, "Bestiary", x + padding, y + 3, COLOR_ORANGE, false);
        g.fill(x + padding, y + titleH - 2, x + HUD_WIDTH - padding, y + titleH - 1, 0x66FFAA00);

        int rowY = y + titleH;

        for (int i = 0; i < tracked.size(); i++) {
            String key = (String) tracked.get(i);
            int ry = rowY + i * rowH;

            String[] parts = BestiaryData.parseTrackedKey(key);
            long current = -1;
            long max = -1;
            String displayName = key;

            if (parts != null) {
                long[] kills = BestiaryData.getKills(parts[0], parts[1]);
                displayName = parts[1];
                if (kills != null) {
                    current = kills[0];
                    max     = kills[1];
                }
            }

            // Truncate name if too wide
            String nameDisplay = displayName;
            while (font.width(nameDisplay) > barW - 2 && nameDisplay.length() > 3) {
                nameDisplay = nameDisplay.substring(0, nameDisplay.length() - 1);
            }
            g.drawString(font, nameDisplay, x + padding, ry + 2, COLOR_WHITE, false);

            if (current < 0) {
                g.drawString(font, "Open /bestiary", x + padding, ry + 11, COLOR_GRAY, false);
            } else if (max > 0 && current >= max) {
                g.drawString(font, "DONE", x + padding, ry + 11, COLOR_GREEN, false);
            } else {
                int barX = x + padding;
                int barY = ry + 12;
                g.fill(barX, barY, barX + barW, barY + barH, COLOR_BAR_BG);
                if (max > 0) {
                    int filled = (int) Math.min(barW, (current * barW) / max);
                    if (filled > 0) g.fill(barX, barY, barX + filled, barY + barH, COLOR_BAR_FG);
                    String countText = current + "/" + max;
                    g.drawString(font, countText,
                            barX + barW / 2 - font.width(countText) / 2,
                            barY + barH + 2, COLOR_GRAY, false);
                }
            }
        }
    }

    /** Renders tier-up popup alerts. Also called directly by HudEditScreen. */
    public static void renderTierUpPopups(GuiGraphics g, Font font, int screenW, int screenH) {
        RubixConfig cfg = RubixConfig.get();
        ArrayList popups = BestiaryTierUpHandler.getActivePopups();
        if (popups.isEmpty()) return;

        int popupW = ALERTS_WIDTH;
        int popupH = 28;
        int gap    = 4;

        // Anchor around cfg.alertsX, cfg.alertsY
        int startX = (int) cfg.alertsX - popupW / 2;
        int startY = (int) cfg.alertsY - (popupH + gap) * popups.size() / 2;

        for (int i = 0; i < popups.size(); i++) {
            BestiaryTierUpHandler.TierUpEntry entry =
                    (BestiaryTierUpHandler.TierUpEntry) popups.get(i);

            long age = System.currentTimeMillis() - entry.timestamp;
            float alpha = age < 4500 ? 1.0f : Math.max(0f, 1.0f - (age - 4500) / 500f);
            int a = (int) (alpha * 0xFF) & 0xFF;

            int py = startY + i * (popupH + gap);

            g.fill(startX, py, startX + popupW, py + popupH,
                    (a << 24) | 0x111111);
            g.fill(startX, py, startX + popupW, py + 1,
                    (a << 24) | 0xFFAA00);
            g.fill(startX, py + popupH - 1, startX + popupW, py + popupH,
                    (a << 24) | 0xFFAA00);
            g.fill(startX, py, startX + 1, py + popupH,
                    (a << 24) | 0xFFAA00);
            g.fill(startX + popupW - 1, py, startX + popupW, py + popupH,
                    (a << 24) | 0xFFAA00);

            String line1 = entry.mobName + " reached Tier " + entry.newTier + "!";
            String line2 = "+" + entry.tiersGained + " tier" + (entry.tiersGained > 1 ? "s" : "");

            int textColor = (a << 24) | 0xFFAA00;
            int subColor  = (a << 24) | 0xAAAAAA;

            g.drawString(font, line1, startX + 6, py + 5, textColor, false);
            g.drawString(font, line2, startX + 6, py + 16, subColor, false);
        }
    }
}