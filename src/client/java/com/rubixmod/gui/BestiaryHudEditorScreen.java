package com.rubixmod.gui;

import com.rubixmod.bestiary.BestiaryData;
import com.rubixmod.config.RubixConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class BestiaryHudEditorScreen extends Screen {

    private static final int BG_OUTER             = 0xE0111111;
    private static final int BG_INNER             = 0xE0191919;
    private static final int BG_ROW               = 0xFF1E1E1E;
    private static final int BG_ROW_HOVER         = 0xFF2A2A2A;
    private static final int BG_ROW_DRAG          = 0xFF333355;
    private static final int COLOR_ORANGE         = 0xFFFFAA00;
    private static final int COLOR_WHITE          = 0xFFFFFFFF;
    private static final int COLOR_GRAY           = 0xFF888888;
    private static final int COLOR_RED            = 0xFFFF5555;
    private static final int COLOR_BORDER         = 0xFF333333;
    private static final int COLOR_DROPDOWN_BG    = 0xFF222222;
    private static final int COLOR_DROPDOWN_HOVER = 0xFF2E2E2E;

    private static final int POPUP_WIDTH  = 440;
    private static final int POPUP_HEIGHT = 320;
    private static final int ROW_HEIGHT   = 20;
    private static final int PADDING      = 10;
    private static final int HEADER_H     = 24;
    private static final int FOOTER_H     = 30;

    private int popupX, popupY, popupW, popupH;
    private int scrollOffset = 0;
    private int draggingIndex = -1;
    private int dragCurrentY = 0;
    private double lastMouseX, lastMouseY;
    private boolean wasPressed = false;
    private int cooldown = 10;

    private boolean dropdownOpen = false;
    private final ArrayList expandedCategories = new ArrayList();
    private int dropdownScrollOffset = 0;

    private int ddBtnX, ddBtnY, ddBtnW, ddBtnH;
    private final ArrayList dropdownItems = new ArrayList();

    private final Runnable onCloseCallback;
    private Font myFont;

    public BestiaryHudEditorScreen(Runnable onCloseCallback) {
        super(Component.literal("Bestiary HUD Editor"));
        this.onCloseCallback = onCloseCallback;
    }

    public void setup(Minecraft mc, int w, int h) {
        this.myFont = mc.font;
        this.width = w;
        this.height = h;

        popupW = POPUP_WIDTH;
        popupH = POPUP_HEIGHT;
        popupX = (w - popupW) / 2;
        popupY = (h - popupH) / 2;
        ddBtnW = 110;
        ddBtnH = 16;
        ddBtnX = popupX + popupW - PADDING - ddBtnW;
        ddBtnY = popupY + HEADER_H + 4;
        cooldown = 10;
        wasPressed = true;
    }

    @Override
    protected void init() {
        popupW = POPUP_WIDTH;
        popupH = POPUP_HEIGHT;
        popupX = (width - popupW) / 2;
        popupY = (height - popupH) / 2;
        ddBtnW = 110;
        ddBtnH = 16;
        ddBtnX = popupX + popupW - PADDING - ddBtnW;
        ddBtnY = popupY + HEADER_H + 4;
        myFont = font;
        cooldown = 10;
        wasPressed = true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (myFont == null) return;

        g.fill(0, 0, width, height, 0x88000000);
        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, BG_OUTER);
        drawBorder(g, popupX, popupY, popupX + popupW, popupY + popupH, COLOR_ORANGE);
        g.fill(popupX, popupY, popupX + popupW, popupY + HEADER_H, 0xFF1A1A1A);
        g.drawString(myFont, "Bestiary HUD Editor", popupX + PADDING, popupY + 7, COLOR_ORANGE);

        // Hint text
        g.drawString(myFont, "Open /bestiary to populate mob data", popupX + PADDING, popupY + HEADER_H + 6, 0xFF666666);

        int closeX = popupX + popupW - 16;
        int closeY = popupY + 4;
        boolean overClose = mouseX >= closeX && mouseX <= closeX + 12
                && mouseY >= closeY && mouseY <= closeY + 14;
        g.drawString(myFont, "x", closeX, closeY + 2, overClose ? COLOR_RED : COLOR_GRAY);

        boolean overDd = mouseX >= ddBtnX && mouseX <= ddBtnX + ddBtnW
                && mouseY >= ddBtnY && mouseY <= ddBtnY + ddBtnH;
        g.fill(ddBtnX, ddBtnY, ddBtnX + ddBtnW, ddBtnY + ddBtnH,
                overDd ? COLOR_DROPDOWN_HOVER : COLOR_DROPDOWN_BG);
        drawBorder(g, ddBtnX, ddBtnY, ddBtnX + ddBtnW, ddBtnY + ddBtnH, COLOR_ORANGE);
        g.drawCenteredString(myFont, "Add Mob [v]", ddBtnX + ddBtnW / 2, ddBtnY + 4, COLOR_ORANGE);

        int listX = popupX + PADDING;
        int listY = popupY + HEADER_H + 24;
        int listW = popupW - PADDING * 2;
        int listH = popupH - HEADER_H - FOOTER_H - 24;

        g.fill(listX, listY, listX + listW, listY + listH, BG_INNER);
        drawBorder(g, listX, listY, listX + listW, listY + listH, COLOR_BORDER);
        g.enableScissor(listX, listY, listX + listW, listY + listH);

        List tracked = RubixConfig.get().trackedMobs;

        for (int i = 0; i < tracked.size(); i++) {
            int rowY = listY + i * ROW_HEIGHT - scrollOffset;
            if (rowY + ROW_HEIGHT < listY || rowY > listY + listH) continue;

            boolean isHovered = mouseX >= listX && mouseX <= listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && draggingIndex == -1;
            boolean isDragging = draggingIndex == i;

            int rowBg = isDragging ? BG_ROW_DRAG : (isHovered ? BG_ROW_HOVER : BG_ROW);
            g.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT - 1, rowBg);
            g.drawString(myFont, "=", listX + 4, rowY + 6, COLOR_GRAY);
            g.drawString(myFont, (String) tracked.get(i), listX + 18, rowY + 6, COLOR_WHITE);

            if (isHovered) {
                g.drawString(myFont, "X", listX + listW - 14, rowY + 6, COLOR_RED);
            }
        }

        if (draggingIndex >= 0 && draggingIndex < tracked.size()) {
            int ghostY = dragCurrentY - ROW_HEIGHT / 2;
            g.fill(listX, ghostY, listX + listW, ghostY + ROW_HEIGHT - 1, BG_ROW_DRAG);
            g.drawString(myFont, "=", listX + 4, ghostY + 6, COLOR_GRAY);
            g.drawString(myFont, (String) tracked.get(draggingIndex), listX + 18, ghostY + 6, COLOR_ORANGE);
        }

        g.disableScissor();

        if (tracked.isEmpty()) {
            g.drawCenteredString(myFont, "No mobs tracked. Use 'Add Mob' to add some!",
                    listX + listW / 2, listY + listH / 2 - 4, COLOR_GRAY);
        }

        int footerY = popupY + popupH - FOOTER_H;
        g.fill(popupX, footerY, popupX + popupW, popupY + popupH, 0xFF1A1A1A);
        g.drawCenteredString(myFont, "Drag = to reorder  |  Hover + X to remove",
                popupX + popupW / 2, footerY + 8, COLOR_GRAY);
        g.drawString(myFont, tracked.size() + "/10 mobs", popupX + PADDING, footerY + 8, COLOR_ORANGE);

        if (dropdownOpen) {
            renderDropdown(g, mouseX, mouseY, listX, ddBtnY + ddBtnH, listW);
        }
    }

    private void renderDropdown(GuiGraphics g, int mouseX, int mouseY, int x, int startY, int w) {
        dropdownItems.clear();
        List tracked = RubixConfig.get().trackedMobs;

        // Build dropdown from actual BestiaryData categories
        Set categories = BestiaryData.getCategories();
        Iterator catIt = categories.iterator();
        while (catIt.hasNext()) {
            String cat = (String) catIt.next();
            Set mobs = BestiaryData.getMobsInCategory(cat);

            // Check if any mob in this category is not yet tracked
            boolean hasUntracked = false;
            Iterator mobIt = mobs.iterator();
            while (mobIt.hasNext()) {
                String mob = (String) mobIt.next();
                String key = cat + " > " + mob;
                if (!tracked.contains(key)) { hasUntracked = true; break; }
            }
            if (!hasUntracked) continue;

            dropdownItems.add(new String[]{"category", cat});

            if (expandedCategories.contains(cat)) {
                Iterator mobIt2 = mobs.iterator();
                while (mobIt2.hasNext()) {
                    String mob = (String) mobIt2.next();
                    String key = cat + " > " + mob;
                    if (!tracked.contains(key)) {
                        dropdownItems.add(new String[]{"mob", key, mob});
                    }
                }
            }
        }

        if (dropdownItems.isEmpty()) {
            dropdownItems.add(new String[]{"info", "Open /bestiary first!"});
        }

        int maxVisible = 14;
        int ddH = Math.min(dropdownItems.size(), maxVisible) * ROW_HEIGHT + 4;
        int ddY = startY + 2;

        int maxScroll = Math.max(0, (dropdownItems.size() - maxVisible) * ROW_HEIGHT);
        dropdownScrollOffset = Math.max(0, Math.min(dropdownScrollOffset, maxScroll));

        g.fill(x, ddY, x + w, ddY + ddH, COLOR_DROPDOWN_BG);
        drawBorder(g, x, ddY, x + w, ddY + ddH, COLOR_ORANGE);
        g.enableScissor(x, ddY, x + w, ddY + ddH);

        for (int i = 0; i < dropdownItems.size(); i++) {
            String[] item = (String[]) dropdownItems.get(i);
            int itemY = ddY + 2 + i * ROW_HEIGHT - dropdownScrollOffset;
            if (itemY + ROW_HEIGHT < ddY || itemY > ddY + ddH) continue;

            boolean hovered = mouseX >= x && mouseX <= x + w
                    && mouseY >= itemY && mouseY < itemY + ROW_HEIGHT;

            if ("category".equals(item[0])) {
                g.fill(x, itemY, x + w, itemY + ROW_HEIGHT - 1, hovered ? 0xFF252525 : 0xFF1E1E1E);
                String arrow = expandedCategories.contains(item[1]) ? "[-] " : "[+] ";
                g.drawString(myFont, arrow + item[1], x + 6, itemY + 6, COLOR_ORANGE);
            } else if ("mob".equals(item[0])) {
                g.fill(x, itemY, x + w, itemY + ROW_HEIGHT - 1, hovered ? COLOR_DROPDOWN_HOVER : COLOR_DROPDOWN_BG);
                // item[2] is the short mob name, item[1] is the full "Cat > Mob" key
                g.drawString(myFont, "  " + item[2], x + 16, itemY + 6, COLOR_WHITE);
            } else {
                // info row
                g.fill(x, itemY, x + w, itemY + ROW_HEIGHT - 1, COLOR_DROPDOWN_BG);
                g.drawString(myFont, item[1], x + 6, itemY + 6, COLOR_GRAY);
            }
        }

        g.disableScissor();
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
        if (draggingIndex >= 0) dragCurrentY = (int) mouseY;
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            wasPressed = true;
            return;
        }

        long window = GLFW.glfwGetCurrentContext();
        boolean pressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        int mx = (int) lastMouseX;
        int my = (int) lastMouseY;

        int listX = popupX + PADDING;
        int listY = popupY + HEADER_H + 24;
        int listW = popupW - PADDING * 2;
        int listH = popupH - HEADER_H - FOOTER_H - 24;

        if (pressed && !wasPressed) {
            int closeX = popupX + popupW - 16;
            int closeY = popupY + 4;
            if (mx >= closeX && mx <= closeX + 12 && my >= closeY && my <= closeY + 14) {
                closeEditor(); wasPressed = true; return;
            }

            if (mx >= ddBtnX && mx <= ddBtnX + ddBtnW && my >= ddBtnY && my <= ddBtnY + ddBtnH) {
                dropdownOpen = !dropdownOpen; wasPressed = true; return;
            }

            if (dropdownOpen) {
                int ddY = ddBtnY + ddBtnH + 2;
                if (mx >= listX && mx <= listX + listW) {
                    for (int i = 0; i < dropdownItems.size(); i++) {
                        int itemY = ddY + 2 + i * ROW_HEIGHT - dropdownScrollOffset;
                        if (my >= itemY && my < itemY + ROW_HEIGHT) {
                            String[] item = (String[]) dropdownItems.get(i);
                            if ("category".equals(item[0])) {
                                if (expandedCategories.contains(item[1])) expandedCategories.remove(item[1]);
                                else expandedCategories.add(item[1]);
                            } else if ("mob".equals(item[0])) {
                                List tracked = RubixConfig.get().trackedMobs;
                                String key = item[1]; // "Category > MobName"
                                if (tracked.size() < 10 && !tracked.contains(key)) {
                                    tracked.add(key);
                                    RubixConfig.save();
                                }
                                dropdownOpen = false;
                            }
                            wasPressed = true; return;
                        }
                    }
                }
                dropdownOpen = false; wasPressed = true; return;
            }

            if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
                List tracked = RubixConfig.get().trackedMobs;
                int index = (my - listY + scrollOffset) / ROW_HEIGHT;
                if (index >= 0 && index < tracked.size()) {
                    int xBtnX = listX + listW - 14;
                    if (mx >= xBtnX) {
                        tracked.remove(index);
                        RubixConfig.save();
                        wasPressed = true; return;
                    }
                    draggingIndex = index;
                    dragCurrentY = my;
                    wasPressed = true; return;
                }
            }

            if (mx < popupX || mx > popupX + popupW || my < popupY || my > popupY + popupH) {
                closeEditor(); wasPressed = true; return;
            }
        }

        if (!pressed && draggingIndex >= 0) {
            List tracked = RubixConfig.get().trackedMobs;
            int targetIndex = (dragCurrentY - listY + scrollOffset) / ROW_HEIGHT;
            targetIndex = Math.max(0, Math.min(targetIndex, tracked.size() - 1));
            if (targetIndex != draggingIndex) {
                String mob = (String) tracked.remove(draggingIndex);
                tracked.add(targetIndex, mob);
                RubixConfig.save();
            }
            draggingIndex = -1;
        }

        wasPressed = pressed;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        int listX = popupX + PADDING;
        int listY = popupY + HEADER_H + 24;
        int listW = popupW - PADDING * 2;
        int listH = popupH - HEADER_H - FOOTER_H - 24;

        List tracked = RubixConfig.get().trackedMobs;
        int maxScroll = Math.max(0, tracked.size() * ROW_HEIGHT - listH);

        if (dropdownOpen) {
            dropdownScrollOffset -= (int) (vAmount * ROW_HEIGHT);
            dropdownScrollOffset = Math.max(0, dropdownScrollOffset);
            return true;
        }

        if (mouseX >= listX && mouseX <= listX + listW
                && mouseY >= listY && mouseY <= listY + listH) {
            scrollOffset -= (int) (vAmount * ROW_HEIGHT);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }

        return false;
    }

    private void closeEditor() {
        RubixConfig.save();
        onCloseCallback.run();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}