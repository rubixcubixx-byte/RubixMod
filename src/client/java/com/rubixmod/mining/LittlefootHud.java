package com.rubixmod.mining;

import com.rubixmod.config.RubixConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws "LITTLEFOOT!" in large bold orange text at the centre of the screen
 * for {@link #POPUP_DURATION_MS} milliseconds after a detection.
 *
 * Animated symbols flank both the local and remote popup headers.
 * Also draws a secondary popup when another RubixMod user finds one in party chat.
 */
public class LittlefootHud {

    private static final Component LOCAL_TEXT = Component.literal("LITTLEFOOT!")
            .withStyle(s -> s.withBold(true));

    private static final float SCALE    = 3.0f;
    private static final int[] PALETTE  = { 0xFFFF8800, 0xFFFFAA00, 0xFFFFCC00, 0xFFFFAA00 };
    private static final long  PULSE_MS = 400;

    /** How long (ms) popups stay on-screen after a detection event. */
    private static final long POPUP_DURATION_MS = 4_000;

    // ── Animated flanking symbols ─────────────────────────────────────────────
    // Cycles through different star/spark shapes to create a "twinkling" effect.
    private static final String[] ANIM_FRAMES = { "✦", "✧", "★", "✧" };
    private static final long     ANIM_MS     = 175; // ms per frame
    private static final int      SYM_GAP     = 4;   // pixels between symbol and text

    /** Returns the current animation frame symbol. */
    private static String symFrame(long now) {
        return ANIM_FRAMES[(int) ((now / ANIM_MS) % ANIM_FRAMES.length)];
    }

    /**
     * Draws [sym]  [text]  [sym] centred horizontally at the given y,
     * with a drop-shadow. Symbol and text share the same colour.
     */
    private static void drawFlanked(GuiGraphics g, Minecraft mc,
                                    Component text, String sym,
                                    int scaledW, int y, int color) {
        int textW  = mc.font.width(text);
        int symW   = mc.font.width(sym);
        int totalW = symW + SYM_GAP + textW + SYM_GAP + symW;
        int startX = (scaledW - totalW) / 2;

        int symRightX = startX + symW + SYM_GAP + textW + SYM_GAP;
        int textX     = startX + symW + SYM_GAP;

        // Shadows
        g.drawString(mc.font, sym,  startX + 1, y + 1, 0xBB000000, false);
        g.drawString(mc.font, text, textX  + 1, y + 1, 0xBB000000, false);
        g.drawString(mc.font, sym,  symRightX + 1, y + 1, 0xBB000000, false);
        // Main
        g.drawString(mc.font, sym,  startX,   y, color, false);
        g.drawString(mc.font, text, textX,    y, color, false);
        g.drawString(mc.font, sym,  symRightX, y, color, false);
    }

    public static void render(GuiGraphics g) {
        if (!RubixConfig.get().littlefootTrackerEnabled) return;

        Minecraft mc  = Minecraft.getInstance();
        int screenW   = mc.getWindow().getGuiScaledWidth();
        int screenH   = mc.getWindow().getGuiScaledHeight();
        long now      = System.currentTimeMillis();
        int color     = PALETTE[(int) ((now / PULSE_MS) % PALETTE.length)];
        String sym    = symFrame(now);

        // ── Local detection popup ─────────────────────────────────────────────
        if (now - LittlefootTracker.getLastDetectionTime() <= POPUP_DURATION_MS) {
            g.pose().pushMatrix();
            g.pose().scale(SCALE, SCALE);

            int scaledW = (int) (screenW / SCALE);
            int scaledH = (int) (screenH / SCALE);
            int y       = (scaledH - mc.font.lineHeight) / 2;

            drawFlanked(g, mc, LOCAL_TEXT, sym, scaledW, y, color);

            g.pose().popMatrix();
        }

        // ── Remote detection popup (another party member found one) ───────────
        String finder = LittlefootTracker.getRemoteFinderName();
        if (finder != null && now - LittlefootTracker.getRemoteDetectionTime() <= POPUP_DURATION_MS) {
            Component headerText = Component.literal("LITTLEFOOT FOUND!")
                    .withStyle(s -> s.withBold(true));
            Component subText = Component.literal(finder + " found one!")
                    .withStyle(s -> s.withBold(false));

            float remoteScale = 2.5f;
            g.pose().pushMatrix();
            g.pose().scale(remoteScale, remoteScale);

            int scaledW = (int) (screenW / remoteScale);
            int scaledH = (int) (screenH / remoteScale);
            int lineH   = mc.font.lineHeight + 2;
            int baseY   = (scaledH - lineH * 2) / 2 - 14;

            // Header with flanking symbols
            drawFlanked(g, mc, headerText, sym, scaledW, baseY, color);

            // Sub-text (player name) — centred, no symbols
            int subW = mc.font.width(subText);
            int subX = (scaledW - subW) / 2;
            g.drawString(mc.font, subText, subX + 1, baseY + lineH + 1, 0xBB000000, false);
            g.drawString(mc.font, subText, subX,     baseY + lineH,     0xFFFFFFFF, false);

            g.pose().popMatrix();
        }
    }
}
