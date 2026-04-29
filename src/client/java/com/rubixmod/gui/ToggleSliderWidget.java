package com.rubixmod.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A pill-style toggle slider — fully custom rendering, no vanilla button chrome.
 *  • OFF → thumb on the left,  red
 *  • ON  → thumb on the right, green
 *
 * Click handling is done via {@link #handleClick(double, double)} which
 * RubixScreen calls from its GLFW polling loop (the same pattern used
 * for tab selection in that screen).
 */
public class ToggleSliderWidget extends AbstractWidget {

    private static final int W = 40;
    private static final int H = 14;
    private static final int THUMB_W = 16;
    private static final int THUMB_H = 10;

    private static final int COLOR_TRACK_ON   = 0xFF112211;
    private static final int COLOR_TRACK_OFF  = 0xFF221111;
    private static final int COLOR_BORDER     = 0xFF444444;
    private static final int COLOR_THUMB_ON   = 0xFF55FF55;
    private static final int COLOR_THUMB_OFF  = 0xFFFF5555;
    private static final int COLOR_HOVER      = 0x22FFFFFF;

    private boolean value;
    private final Consumer<Boolean> onChange;

    public ToggleSliderWidget(int x, int y, boolean value, Consumer<Boolean> onChange) {
        super(x, y, W, H, Component.empty());
        this.value = value;
        this.onChange = onChange;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY();

        // Track background (tinted by state)
        g.fill(x, y, x + W, y + H, value ? COLOR_TRACK_ON : COLOR_TRACK_OFF);

        // Border (1 px, all sides)
        g.fill(x,       y,       x + W, y + 1,     COLOR_BORDER);
        g.fill(x,       y + H-1, x + W, y + H,     COLOR_BORDER);
        g.fill(x,       y,       x + 1, y + H,     COLOR_BORDER);
        g.fill(x + W-1, y,       x + W, y + H,     COLOR_BORDER);

        // Thumb (vertically centred, left = OFF, right = ON)
        int thumbY = y + (H - THUMB_H) / 2;
        int thumbX = value ? x + W - THUMB_W - 2 : x + 2;
        g.fill(thumbX, thumbY, thumbX + THUMB_W, thumbY + THUMB_H,
                value ? COLOR_THUMB_ON : COLOR_THUMB_OFF);

        // Hover tint
        if (isHovered()) g.fill(x + 1, y + 1, x + W - 1, y + H - 1, COLOR_HOVER);
    }

    // ── Click handling ────────────────────────────────────────────────────────

    /**
     * Call this from the screen's GLFW mouse-press polling loop.
     * Returns true if the click was inside this widget (consumed).
     */
    public boolean handleClick(double mouseX, double mouseY) {
        if (mouseX >= getX() && mouseX < getX() + W && mouseY >= getY() && mouseY < getY() + H) {
            value = !value;
            onChange.accept(value);
            return true;
        }
        return false;
    }

    // ── Narration ─────────────────────────────────────────────────────────────

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(value ? "On" : "Off"));
    }
}
