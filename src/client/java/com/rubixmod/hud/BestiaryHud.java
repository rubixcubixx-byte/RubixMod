package com.rubixmod.hud;

import com.rubixmod.bestiary.BestiaryData;
import com.rubixmod.bestiary.BestiaryTierUpHandler;
import com.rubixmod.bestiary.LiveMobTracker;
import com.rubixmod.config.RubixConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class BestiaryHud {

    // Width is fixed; height is dynamic — use getHudHeight() for accurate bounds
    public static final int HUD_WIDTH    = 140;
    public static final int ALERTS_WIDTH = 200;
    public static final int ALERTS_HEIGHT = 60;

    // Layout constants
    private static final int PADDING  = 6;
    private static final int TITLE_H  = 16;  // includes 2px gap after separator
    private static final int CARD_H   = 26;
    private static final int CARD_GAP = 3;

    private static final int COLOR_ORANGE  = 0xFFFFAA00;
    private static final int COLOR_WHITE   = 0xFFFFFFFF;
    private static final int COLOR_GRAY    = 0xFF888888;
    private static final int COLOR_BG      = 0xCC111111;
    private static final int COLOR_BORDER  = 0xFFFFAA00;
    private static final int COLOR_CARD_BG = 0xFF1C1C1C;
    private static final int COLOR_CARD_BD = 0xFF3A3A3A;
    private static final int COLOR_BAR_BG  = 0xFF2A2A2A;
    private static final int COLOR_BAR_FG  = 0xFF55AA00;

    /** Placeholder key used when the HUD editor has nothing to show. */
    private static final String PREVIEW_KEY = "__preview__ > Example Mob";

    /**
     * Standard Hypixel Skyblock bestiary tier thresholds (cumulative kills to START each tier).
     * Index 0 = start of Tier 1 (0 kills), Index 1 = start of Tier 2 (25 kills), etc.
     * Most mobs follow this pattern; a few bosses/special mobs may differ slightly.
     */
    private static final long[] TIER_STARTS = {0, 10, 100, 250, 500, 1000, 2500, 5000, 10000};

    /** Returns the actual pixel height of the HUD for a given tracked mob count. */
    public static int getHudHeight(int trackedCount) {
        if (trackedCount == 0) return TITLE_H + PADDING;
        return TITLE_H + trackedCount * (CARD_H + CARD_GAP) - CARD_GAP + PADDING;
    }

    /** Called from HudRenderCallback every frame. */
    public static void render(GuiGraphics g, float scale) {
        RubixConfig cfg = RubixConfig.get();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        if (cfg.hudEnabled) {
            renderBestiaryInfo(g, font, false);
        }

        if (cfg.bestiaryAlertsEnabled) {
            renderTierUpPopups(g, font, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), false);
        }
    }

    /**
     * Renders the bestiary HUD box.
     * Also called directly by HudEditScreen with preview=true.
     *
     * @param preview  When true: show a placeholder card if no mobs are tracked,
     *                 so the HUD box stays visible and draggable in the editor.
     */
    public static void renderBestiaryInfo(GuiGraphics g, Font font, boolean preview) {
        RubixConfig cfg = RubixConfig.get();
        List tracked = cfg.hudAutoTrack ? LiveMobTracker.getActiveMobs() : cfg.trackedMobs;

        if (tracked.isEmpty()) {
            if (!preview) return;
            // In the editor, fall back to the pinned list; if that's also empty use a placeholder
            tracked = cfg.trackedMobs.isEmpty()
                    ? java.util.Arrays.asList(PREVIEW_KEY)
                    : cfg.trackedMobs;
        }

        int x      = (int) cfg.bestiaryHudX;
        int y      = (int) cfg.bestiaryHudY;
        float s    = cfg.bestiaryHudScale;
        int boxH   = getHudHeight(tracked.size());
        int cardX  = x + PADDING;
        int cardW  = HUD_WIDTH - PADDING * 2;

        // Scale the entire HUD around its top-left corner
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        g.pose().translate(-x, -y);

        // Outer background + border
        g.fill(x, y, x + HUD_WIDTH, y + boxH, COLOR_BG);
        g.fill(x, y,               x + HUD_WIDTH, y + 1,      COLOR_BORDER);
        g.fill(x, y + boxH - 1,   x + HUD_WIDTH, y + boxH,   COLOR_BORDER);
        g.fill(x, y,               x + 1,         y + boxH,   COLOR_BORDER);
        g.fill(x + HUD_WIDTH - 1, y,              x + HUD_WIDTH, y + boxH, COLOR_BORDER);

        // Title
        g.drawString(font, "Bestiary", x + PADDING, y + 3, COLOR_ORANGE, false);
        g.fill(x + PADDING, y + TITLE_H - 3, x + HUD_WIDTH - PADDING, y + TITLE_H - 2, 0x55FFAA00);

        int rowY = y + TITLE_H;

        for (int i = 0; i < tracked.size(); i++) {
            String key = (String) tracked.get(i);
            int cardY = rowY + i * (CARD_H + CARD_GAP);

            String[] parts = BestiaryData.parseTrackedKey(key);
            long current = -1;
            long max     = -1;
            String displayName = key;

            if (parts != null) {
                long[] kills = BestiaryData.getKills(parts[0], parts[1]);
                displayName = parts[1];
                if (kills != null) {
                    current = kills[0];
                    max     = kills[1];
                }
            }

            // Card background + border
            g.fill(cardX,             cardY,          cardX + cardW,     cardY + CARD_H,     COLOR_CARD_BG);
            g.fill(cardX,             cardY,          cardX + cardW,     cardY + 1,           COLOR_CARD_BD);
            g.fill(cardX,             cardY + CARD_H - 1, cardX + cardW, cardY + CARD_H,     COLOR_CARD_BD);
            g.fill(cardX,             cardY,          cardX + 1,         cardY + CARD_H,      COLOR_CARD_BD);
            g.fill(cardX + cardW - 1, cardY,          cardX + cardW,     cardY + CARD_H,      COLOR_CARD_BD);

            // Mob name line — in per-tier mode, show "T4 → T5" right-aligned on the same row
            int nameLineW = cardW - 8;
            String nameDisplay = displayName;

            if (cfg.hudPerTierMode && current >= 0 && !(max > 0 && current >= max)) {
                // Calculate tier for the name line label
                int tn = 1;
                for (int t = TIER_STARTS.length - 1; t >= 1; t--) {
                    if (current >= TIER_STARTS[t]) { tn = t + 1; break; }
                }
                String tierLabel = toRoman(tn) + " \u2192 " + toRoman(tn + 1);
                int tierLabelW = font.width(tierLabel);
                int nameMaxW   = nameLineW - tierLabelW - 6; // 6px gap between name and label

                while (font.width(nameDisplay) > nameMaxW && nameDisplay.length() > 3) {
                    nameDisplay = nameDisplay.substring(0, nameDisplay.length() - 1);
                }
                g.drawString(font, nameDisplay, cardX + 4, cardY + 3, COLOR_WHITE, false);
                // Place tier label immediately after the mob name with a 3px gap
                g.drawString(font, tierLabel,
                        cardX + 4 + font.width(nameDisplay) + 3, cardY + 3, COLOR_WHITE, false);
            } else {
                while (font.width(nameDisplay) > nameLineW && nameDisplay.length() > 3) {
                    nameDisplay = nameDisplay.substring(0, nameDisplay.length() - 1);
                }
                g.drawString(font, nameDisplay, cardX + 4, cardY + 3, COLOR_WHITE, false);
            }

            // Progress bar — sits at the bottom of the card
            int barX = cardX + 4;
            int barY = cardY + CARD_H - 12;
            int barW = cardW - 8;
            int barH = 10;

            if (current < 0) {
                // No data scanned yet
                g.fill(barX, barY, barX + barW, barY + barH, COLOR_BAR_BG);
                String msg = "Scan /bestiary";
                g.drawString(font, msg,
                        barX + barW / 2 - font.width(msg) / 2,
                        barY + 2, COLOR_GRAY, false);

            } else if (max > 0 && current >= max) {
                // Completed — full green bar, "DONE" inside
                g.fill(barX, barY, barX + barW, barY + barH, COLOR_BAR_FG);
                String done = "DONE";
                g.drawString(font, done,
                        barX + barW / 2 - font.width(done) / 2,
                        barY + 2, COLOR_WHITE, false);

            } else if (cfg.hudPerTierMode) {
                // Per-tier mode: show progress within the current tier
                int tierNum = 1;
                long tierStart = 0;
                long tierEnd = max > 0 ? max : TIER_STARTS[TIER_STARTS.length - 1];
                for (int t = TIER_STARTS.length - 1; t >= 1; t--) {
                    if (current >= TIER_STARTS[t]) {
                        tierNum   = t + 1;
                        tierStart = TIER_STARTS[t];
                        tierEnd   = (t + 1 < TIER_STARTS.length) ? TIER_STARTS[t + 1] : (max > 0 ? max : tierStart + 100);
                        break;
                    }
                }
                long tierCurrent = current - tierStart;
                long tierMax     = tierEnd - tierStart;

                g.fill(barX, barY, barX + barW, barY + barH, COLOR_BAR_BG);
                if (tierMax > 0) {
                    int filled = (int) Math.min(barW, (tierCurrent * barW) / tierMax);
                    if (filled > 0) g.fill(barX, barY, barX + filled, barY + barH, COLOR_BAR_FG);
                }
                String tierText = tierCurrent + "/" + tierMax;
                g.drawString(font, tierText,
                        barX + barW / 2 - font.width(tierText) / 2,
                        barY + 2, COLOR_WHITE, false);

            } else {
                // Max Kills mode (default): show overall progress
                g.fill(barX, barY, barX + barW, barY + barH, COLOR_BAR_BG);
                if (max > 0) {
                    int filled = (int) Math.min(barW, (current * barW) / max);
                    if (filled > 0) g.fill(barX, barY, barX + filled, barY + barH, COLOR_BAR_FG);
                }
                String countText = current + "/" + max;
                g.drawString(font, countText,
                        barX + barW / 2 - font.width(countText) / 2,
                        barY + 2, COLOR_WHITE, false);
            }
        }

        g.pose().popMatrix();
    }

    /** Converts a positive integer to a Roman numeral string (supports 1–39, covers all bestiary tiers). */
    private static String toRoman(int n) {
        if (n <= 0 || n >= 40) return String.valueOf(n);
        String[] tens = {"", "X", "XX", "XXX"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return tens[n / 10] + ones[n % 10];
    }

    /**
     * Renders tier-up popup alerts.
     * Also called directly by HudEditScreen with preview=true.
     *
     * @param preview  When true: use raw popup list (ignore expiry) and force full alpha,
     *                 so the preview stays permanently visible in the editor.
     */
    public static void renderTierUpPopups(GuiGraphics g, Font font, int screenW, int screenH, boolean preview) {
        RubixConfig cfg = RubixConfig.get();
        ArrayList popups = preview
                ? BestiaryTierUpHandler.getRawPopups()
                : BestiaryTierUpHandler.getActivePopups();
        if (popups.isEmpty()) return;

        // In preview mode always show at full opacity; otherwise fade based on newest entry age
        int a;
        if (preview) {
            a = 0xFF;
        } else {
            BestiaryTierUpHandler.TierUpEntry newest =
                    (BestiaryTierUpHandler.TierUpEntry) popups.get(popups.size() - 1);
            long age = System.currentTimeMillis() - newest.timestamp;
            float alpha = age < 4500 ? 1.0f : Math.max(0f, 1.0f - (age - 4500) / 500f);
            a = (int) (alpha * 0xFF) & 0xFF;
            if (a <= 0) return;
        }

        // Accumulate total XP — 1 per tier gained
        int totalXP = 0;
        for (int i = 0; i < popups.size(); i++) {
            totalXP += ((BestiaryTierUpHandler.TierUpEntry) popups.get(i)).tiersGained;
        }

        int lineH   = 11; // font height (8px) + gap
        int centerX = (int) cfg.alertsX;
        int centerY = (int) cfg.alertsY;
        float s     = cfg.alertsScale;
        // Total height = XP line + one line per popup; center around alertsY
        int totalH  = (1 + popups.size()) * lineH;
        int startY  = centerY - totalH / 2;

        // Scale around the anchor point
        g.pose().pushMatrix();
        g.pose().translate(centerX, centerY);
        g.pose().scale(s, s);
        g.pose().translate(-centerX, -centerY);

        // "+X Skyblock XP" in cyan on top
        String xpLine  = "+" + totalXP + " Skyblock XP";
        int    cyanCol = (a << 24) | 0x55FFFF;
        g.drawString(font, xpLine, centerX - font.width(xpLine) / 2, startY, cyanCol, true);

        // One green line per tier-up below
        int greenCol = (a << 24) | 0x55FF55;
        for (int i = 0; i < popups.size(); i++) {
            BestiaryTierUpHandler.TierUpEntry entry =
                    (BestiaryTierUpHandler.TierUpEntry) popups.get(i);
            String line = entry.mobName + " reached Tier " + entry.newTier + "!";
            g.drawString(font, line,
                    centerX - font.width(line) / 2,
                    startY + (i + 1) * lineH,
                    greenCol, true);
        }

        g.pose().popMatrix();
    }
}
