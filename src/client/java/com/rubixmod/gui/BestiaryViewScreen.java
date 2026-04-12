package com.rubixmod.gui;

import com.rubixmod.bestiary.BestiaryData;
import com.rubixmod.bestiary.BestiaryMobList;
import com.rubixmod.config.RubixConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class BestiaryViewScreen extends Screen {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final int C_BG             = 0xFF111111;
    private static final int C_HEADER_BG      = 0xFF1A1A1A;
    private static final int C_FILTER_BG      = 0xFF161616;
    private static final int C_CARD_BG        = 0xFF2D2D2D;
    private static final int C_CARD_BD        = 0xFF484848;
    private static final int C_CARD_BD_LOCKED = 0xFF2A2A2A;
    private static final int C_BAR_BG         = 0xFF111111;
    private static final int C_BAR_FG         = 0xFF55AA00;
    private static final int C_BAR_LOCKED     = 0xFF333333;
    private static final int C_ORANGE         = 0xFFFFAA00;
    private static final int C_WHITE          = 0xFFFFFFFF;
    private static final int C_GRAY           = 0xFF888888;
    private static final int C_GRAY_DIM       = 0xFF555555;
    private static final int C_DIVIDER        = 0xFF333333;
    private static final int C_CAT_PANEL      = 0xFF181818;
    private static final int C_CAT_HEADER_BG  = 0xFF202020;

    // ── Static layout ─────────────────────────────────────────────────────────
    private static final int HEADER_H  = 30;
    private static final int FILTER_H  = 28;   // search + hide MAX
    private static final int PILL_H    = 24;   // height of each category pill
    private static final int PILL_HPAD = 10;   // horizontal padding inside pill
    private static final int PILL_GAP  = 4;    // gap between pills
    private static final int PILL_VPAD = 5;    // top + bottom padding in the icon bar
    private static final int PADDING   = 10;
    private static final int CARD_W    = 160;
    private static final int CARD_H    = 38;
    private static final int CARD_GAP  = 4;
    private static final int CAT_H     = 24;
    private static final int CAT_PRE   = 20;
    private static final int CAT_PAD   = 6;

    // CONTENT_TOP is dynamic (depends on whether icons wrap to 2 rows)
    // computed by getContentTop()

    // ── State ─────────────────────────────────────────────────────────────────
    private int scroll        = 0;
    private int totalContentH = 0;
    private String searchQuery = "";
    private EditBox searchBox;

    private double lastMouseX, lastMouseY;
    private boolean wasPressed = false;

    /** Screen-space bounds of each rendered category header (for collapse clicks). */
    private final Map<String, int[]> categoryHeaderBounds = new LinkedHashMap<>();

    /** Screen-space bounds of each category icon (for filter clicks). */
    private final Map<String, int[]> iconBounds = new LinkedHashMap<>();

    /** Category currently under the mouse (for tooltip). */
    private String hoveredIcon = null;

    private final List<String>              categories = new ArrayList<>();
    private final Map<String, List<String>> catMobs    = new LinkedHashMap<>();

    public BestiaryViewScreen() {
        super(Component.literal("Bestiary"));
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        loadData();
        scroll = 0;

        int searchW = Math.min(240, width / 2 - PADDING * 2);
        int filterBarTop = HEADER_H + 1;
        searchBox = new EditBox(font,
                PADDING, filterBarTop + (FILTER_H - 18) / 2,
                searchW, 18,
                Component.literal("Search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setHint(Component.literal("Search mobs..."));
        addRenderableWidget(searchBox);

        RubixConfig cfg = RubixConfig.get();
        addRenderableWidget(Button.builder(
                hideMaxLabel(cfg.bestiaryHideMax),
                btn -> {
                    cfg.bestiaryHideMax = !cfg.bestiaryHideMax;
                    RubixConfig.save();
                    totalContentH = computeTotalHeight();
                    scroll = 0;
                    rebuildWidgets();
                })
                .bounds(PADDING + searchW + 8, filterBarTop + (FILTER_H - 16) / 2, 110, 16)
                .build());
    }

    private static Component hideMaxLabel(boolean on) {
        return on
                ? Component.literal("Hide MAX: ON").withStyle(s -> s.withColor(0x55FF55))
                : Component.literal("Hide MAX: OFF").withStyle(s -> s.withColor(0xFF5555));
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void loadData() {
        categories.clear();
        catMobs.clear();

        Set<String> allCats = new LinkedHashSet<>();
        for (Object c : BestiaryMobList.CATEGORIES.keySet()) {
            String s = (String) c;
            if (!s.equals("Fishing")) allCats.add(s);
        }
        for (Object s : BestiaryMobList.FISHING_SUBCATEGORY_KEYS) allCats.add((String) s);
        for (Object c : BestiaryData.getCategories()) {
            String s = (String) c;
            if (!s.equals("API")) allCats.add(s);
        }

        for (String cat : allCats) {
            Map<String, String> mobKeyMap = new LinkedHashMap<>();
            for (Object m : BestiaryData.getMobsInCategory(cat)) {
                String s = (String) m;
                mobKeyMap.put(normalize(s), s);
            }
            List known = (List) BestiaryMobList.CATEGORIES.get(cat);
            if (known == null) known = (List) BestiaryMobList.FISHING_SUBCATEGORIES.get(cat);
            if (known != null) {
                for (Object m : known) {
                    String s = (String) m;
                    mobKeyMap.putIfAbsent(normalize(s), s);
                }
            }
            if (!mobKeyMap.isEmpty()) {
                List<String> sorted = new ArrayList<>(mobKeyMap.values());
                sorted.sort(String::compareToIgnoreCase);
                catMobs.put(cat, sorted);
                categories.add(cat);
            }
        }

        categories.sort(String::compareToIgnoreCase);
        totalContentH = computeTotalHeight();
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    /** Y coordinate where scrollable content begins (below all fixed bars). */
    private int getContentTop() {
        int rows   = getIconBarRows();
        int barH   = rows * (PILL_H + PILL_GAP) - PILL_GAP + PILL_VPAD * 2;
        return HEADER_H + 1 + FILTER_H + 1 + barH + 1;
    }

    private int getIconBarRows() {
        if (categories.isEmpty()) return 1;
        int availW = width - PADDING * 2;
        int x = 0, rows = 1;
        for (String cat : categories) {
            int w = pillWidth(cat);
            if (x > 0 && x + PILL_GAP + w > availW) {
                rows++;
                x = w;
            } else {
                x += (x > 0 ? PILL_GAP : 0) + w;
            }
        }
        return rows;
    }

    /** Width of a labeled pill for the given category. */
    private int pillWidth(String cat) {
        return font.width(displayName(cat)) + PILL_HPAD * 2;
    }

    private int columns() {
        return Math.max(1, (width - PADDING * 2 + CARD_GAP) / (CARD_W + CARD_GAP));
    }

    // ── Filtering helpers ─────────────────────────────────────────────────────

    private boolean isMobVisible(String cat, String mob) {
        if (!searchQuery.trim().isEmpty()) {
            if (!mob.toLowerCase().contains(searchQuery.trim().toLowerCase())) return false;
        }
        if (RubixConfig.get().bestiaryHideMax) {
            long[] kills = BestiaryData.getKills(cat, mob);
            if (kills != null && kills[1] > 0 && kills[0] >= kills[1]) return false;
        }
        return true;
    }

    private List<String> getVisibleMobs(String cat) {
        List<String> all = catMobs.getOrDefault(cat, List.of());
        boolean filtering = !searchQuery.trim().isEmpty() || RubixConfig.get().bestiaryHideMax;
        if (!filtering) return all;
        List<String> visible = new ArrayList<>();
        for (String mob : all) {
            if (isMobVisible(cat, mob)) visible.add(mob);
        }
        return visible;
    }

    private List<String> getFilteredCategories() {
        RubixConfig cfg = RubixConfig.get();
        boolean searching = !searchQuery.trim().isEmpty();
        List<String> result = new ArrayList<>();
        for (String cat : categories) {
            // Pill filter is bypassed when the user is actively searching
            if (!searching && cfg.hiddenCategories.contains(cat)) continue;
            if (!getVisibleMobs(cat).isEmpty()) result.add(cat);
        }
        return result;
    }

    // ── Height computation ────────────────────────────────────────────────────

    private int computeTotalHeight() {
        int cols = columns();
        int h = PADDING;
        RubixConfig cfg = RubixConfig.get();
        for (String cat : getFilteredCategories()) {
            List<String> mobs = getVisibleMobs(cat);
            if (mobs.isEmpty()) continue;
            boolean collapsed = cfg.collapsedCategories.contains(cat);
            h += CAT_PRE + CAT_H;
            if (!collapsed) {
                int rows = (mobs.size() + cols - 1) / cols;
                h += CAT_PAD + rows * (CARD_H + CARD_GAP) - CARD_GAP + CAT_PAD;
            }
        }
        return h + PADDING;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Sync search box
        if (searchBox != null) {
            String cur = searchBox.getValue();
            if (!cur.equals(searchQuery)) {
                searchQuery = cur;
                totalContentH = computeTotalHeight();
                scroll = 0;
            }
        }

        int contentTop = getContentTop();

        g.fill(0, 0, width, height, C_BG);

        // Header
        g.fill(0, 0, width, HEADER_H, C_HEADER_BG);
        g.fill(0, HEADER_H, width, HEADER_H + 1, C_DIVIDER);
        g.drawString(font, "Bestiary Overview", PADDING, 11, C_ORANGE);
        String hint = "Click category to collapse  ·  Scroll  ·  ESC";
        g.drawString(font, hint, width - font.width(hint) - PADDING, 11, C_GRAY);

        // Filter bar (search + hide MAX)
        g.fill(0, HEADER_H + 1, width, HEADER_H + 1 + FILTER_H, C_FILTER_BG);
        g.fill(0, HEADER_H + 1 + FILTER_H, width, HEADER_H + 1 + FILTER_H + 1, C_DIVIDER);

        // Icon filter bar
        renderIconBar(g, mouseX, mouseY);
        g.fill(0, contentTop - 1, width, contentTop, C_DIVIDER);

        // Scrollable content
        int visH      = height - contentTop;
        int maxScroll = Math.max(0, totalContentH - visH);
        scroll = clamp(scroll, 0, maxScroll);

        g.enableScissor(0, contentTop, width, height);
        categoryHeaderBounds.clear();
        renderContent(g, contentTop);
        g.disableScissor();

        if (totalContentH > visH) drawScrollBar(g, width - 6, contentTop, 4, visH, maxScroll);

        super.render(g, mouseX, mouseY, delta);

    }

    // ── Icon filter bar ───────────────────────────────────────────────────────

    private void renderIconBar(GuiGraphics g, int mouseX, int mouseY) {
        int barTop = HEADER_H + 1 + FILTER_H + 1;
        int rows   = getIconBarRows();
        int barH   = rows * (PILL_H + PILL_GAP) - PILL_GAP + PILL_VPAD * 2;

        g.fill(0, barTop, width, barTop + barH, C_FILTER_BG);

        iconBounds.clear();
        hoveredIcon = null;

        RubixConfig cfg    = RubixConfig.get();
        int availW         = width - PADDING * 2;
        int x              = PADDING;
        int iy             = barTop + PILL_VPAD;
        int rowNum         = 0;

        for (String cat : categories) {
            int w = pillWidth(cat);

            // Wrap to next row if no room
            if (x > PADDING && x + w > PADDING + availW) {
                rowNum++;
                x  = PADDING;
                iy = barTop + PILL_VPAD + rowNum * (PILL_H + PILL_GAP);
            }

            boolean hidden  = cfg.hiddenCategories.contains(cat);
            boolean hovered = mouseX >= x && mouseX < x + w
                           && mouseY >= iy && mouseY < iy + PILL_H;
            if (hovered) hoveredIcon = cat;

            iconBounds.put(cat, new int[]{x, iy, w, PILL_H});
            drawPill(g, x, iy, w, PILL_H, hidden, hovered, getCategoryColor(cat), displayName(cat));

            x += w + PILL_GAP;
        }
    }

    /** Draws a rounded labeled pill for one category. */
    private void drawPill(GuiGraphics g, int x, int y, int w, int h,
                          boolean inactive, boolean hovered, int catColor, String label) {
        int r = 4;
        int fill, border, textColor;

        if (inactive) {
            fill      = hovered ? 0xFF484848 : 0xFF2D2D2D;
            border    = hovered ? 0xFF686868 : 0xFF484848;
            textColor = hovered ? 0xFFAAAAAA : 0xFF666666;
        } else {
            // Active: dark fill tinted by category color, full-color border
            fill      = hovered ? brighten(darken(catColor, 0.3f), 0.15f) : darken(catColor, 0.3f);
            border    = hovered ? brighten(catColor, 0.4f) : catColor;
            textColor = hovered ? C_WHITE : brighten(catColor, 0.3f);
        }

        // Rounded fill (cross-fill technique, radius r)
        g.fill(x + r, y,         x + w - r, y + h,         fill);
        g.fill(x,     y + r,     x + r,     y + h - r,     fill);
        g.fill(x + w - r, y + r, x + w,     y + h - r,     fill);

        // Border edges
        g.fill(x + r,     y,         x + w - r, y + 1,         border);
        g.fill(x + r,     y + h - 1, x + w - r, y + h,         border);
        g.fill(x,         y + r,     x + 1,     y + h - r,     border);
        g.fill(x + w - 1, y + r,     x + w,     y + h - r,     border);

        // Corner bevel pixels
        g.fill(x + r - 1, y + 1,         x + r,         y + r,         border);
        g.fill(x + w - r, y + 1,         x + w - r + 1, y + r,         border);
        g.fill(x + r - 1, y + h - r,     x + r,         y + h - 1,     border);
        g.fill(x + w - r, y + h - r,     x + w - r + 1, y + h - 1,     border);

        // Centered label
        int tx = x + w / 2 - font.width(label) / 2;
        int ty = y + (h - 8) / 2;
        g.drawString(font, label, tx, ty, textColor, false);
    }

    // ── Content ───────────────────────────────────────────────────────────────

    private void renderContent(GuiGraphics g, int listTop) {
        int cols  = columns();
        int gridW = cols * (CARD_W + CARD_GAP) - CARD_GAP;
        int y     = listTop - scroll + PADDING;
        RubixConfig cfg = RubixConfig.get();

        for (String cat : getFilteredCategories()) {
            List<String> mobs = getVisibleMobs(cat);
            if (mobs.isEmpty()) continue;

            boolean collapsed = cfg.collapsedCategories.contains(cat);
            int panelX = PADDING - 4;
            int panelW = gridW + 8;

            int cardsH   = 0;
            int sectionH = CAT_H;
            if (!collapsed) {
                int rows = (mobs.size() + cols - 1) / cols;
                cardsH   = rows * (CARD_H + CARD_GAP) - CARD_GAP;
                sectionH += CAT_PAD + cardsH + CAT_PAD;
            }

            y += CAT_PRE;

            if (!collapsed && y + sectionH > listTop && y < height) {
                g.fill(panelX, y, panelX + panelW, y + sectionH, C_CAT_PANEL);
            }

            if (y + CAT_H > listTop && y < height) {
                renderCategoryHeader(g, cat, y, panelX, panelW, mobs.size(), collapsed);
                categoryHeaderBounds.put(cat, new int[]{panelX, y, panelW, CAT_H});
            }
            y += CAT_H;

            if (!collapsed) {
                y += CAT_PAD;
                for (int i = 0; i < mobs.size(); i++) {
                    int col   = i % cols;
                    int row   = i / cols;
                    int cardX = PADDING + col * (CARD_W + CARD_GAP);
                    int cardY = y + row * (CARD_H + CARD_GAP);
                    if (cardY + CARD_H > listTop && cardY < height) {
                        renderCard(g, cat, mobs.get(i), cardX, cardY);
                    }
                }
                y += cardsH + CAT_PAD;
            }
        }
    }

    // ── Category header ───────────────────────────────────────────────────────

    private void renderCategoryHeader(GuiGraphics g, String cat, int y,
                                      int panelX, int panelW,
                                      int visibleMobCount, boolean collapsed) {
        int catColor = getCategoryColor(cat);
        g.fill(panelX, y, panelX + panelW, y + CAT_H, C_CAT_HEADER_BG);
        g.fill(panelX, y, panelX + panelW, y + 2, catColor);
        g.fill(panelX, y + CAT_H - 1, panelX + panelW, y + CAT_H, C_DIVIDER);

        String indicator = collapsed ? "\u25BA" : "\u25BC";
        int indicatorX = panelX + panelW - font.width(indicator) - 6;
        int textBaseY  = y + (CAT_H - 8) / 2;
        g.drawString(font, indicator, indicatorX, textBaseY, C_GRAY);

        String countStr = visibleMobCount + " mobs";
        g.drawString(font, countStr, indicatorX - font.width(countStr) - 6, textBaseY, C_GRAY);

        String name = displayName(cat);
        Component boldName = Component.literal(name).withStyle(s -> s.withBold(true));
        int textW = font.width(boldName);

        float titleScale = 1.15f;
        g.pose().pushMatrix();
        g.pose().translate(panelX + panelW / 2f, y + CAT_H / 2f);
        g.pose().scale(titleScale, titleScale);
        g.pose().translate(-(panelX + panelW / 2f), -(y + CAT_H / 2f));
        g.drawString(font, boldName, panelX + panelW / 2 - textW / 2, textBaseY, catColor, false);
        g.pose().popMatrix();
    }

    // ── Mob card ─────────────────────────────────────────────────────────────

    private void renderCard(GuiGraphics g, String cat, String mob, int x, int y) {
        long[] kills  = BestiaryData.getKills(cat, mob);
        boolean hasData = kills != null;
        long cur = hasData ? kills[0] : 0;
        long max = hasData ? kills[1] : 0;
        boolean maxed = hasData && max > 0 && cur >= max;

        // Rounded bubble card (3px corner radius via cross-fill + bevel corners)
        int r = 3;
        g.fill(x + r, y,     x + CARD_W - r, y + CARD_H,     C_CARD_BG);
        g.fill(x,     y + r, x + r,          y + CARD_H - r, C_CARD_BG);
        g.fill(x + CARD_W - r, y + r, x + CARD_W, y + CARD_H - r, C_CARD_BG);

        int bd = hasData ? C_CARD_BD : C_CARD_BD_LOCKED;
        g.fill(x + r, y,              x + CARD_W - r, y + 1,          bd);
        g.fill(x + r, y + CARD_H - 1, x + CARD_W - r, y + CARD_H,    bd);
        g.fill(x,     y + r,          x + 1,          y + CARD_H - r, bd);
        g.fill(x + CARD_W - 1, y + r, x + CARD_W,    y + CARD_H - r, bd);

        // Corner bevel pixels
        g.fill(x + r - 1, y + 1,          x + r,         y + r,         bd);
        g.fill(x + CARD_W - r, y + 1,     x + CARD_W - r + 1, y + r,   bd);
        g.fill(x + r - 1, y + CARD_H - r, x + r,         y + CARD_H - 1, bd);
        g.fill(x + CARD_W - r, y + CARD_H - r, x + CARD_W - r + 1, y + CARD_H - 1, bd);

        // Mob name
        int nameColor = maxed ? C_ORANGE : (hasData ? C_WHITE : C_GRAY_DIM);
        String name = truncate(mob, CARD_W - 8);
        g.drawString(font, name, x + 4, y + 3, nameColor, false);

        // Progress bar
        int barX = x + 4;
        int barY = y + CARD_H - 14;
        int barW = CARD_W - 8;

        if (!hasData) {
            g.fill(barX, barY, barX + barW, barY + 10, C_BAR_LOCKED);
            String msg = "Not unlocked";
            g.drawString(font, msg, barX + barW / 2 - font.width(msg) / 2, barY + 2, C_GRAY_DIM, false);
        } else if (maxed) {
            g.fill(barX, barY, barX + barW, barY + 10, C_ORANGE);
            String done = "MAX";
            g.drawString(font, done, barX + barW / 2 - font.width(done) / 2, barY + 2, C_WHITE, false);
        } else {
            g.fill(barX, barY, barX + barW, barY + 10, C_BAR_BG);
            if (max > 0) {
                int filled = (int) Math.min(barW, cur * barW / max);
                if (filled > 0) g.fill(barX, barY, barX + filled, barY + 10, C_BAR_FG);
            }
            String countText = fmt(cur) + " / " + fmt(max);
            g.drawString(font, countText, barX + barW / 2 - font.width(countText) / 2, barY + 2, C_WHITE, false);
        }
    }

    // ── Scroll bar ────────────────────────────────────────────────────────────

    private void drawScrollBar(GuiGraphics g, int x, int top, int w, int visH, int maxScroll) {
        g.fill(x, top, x + w, top + visH, 0xFF222222);
        int thumbH = Math.max(20, visH * visH / Math.max(visH, totalContentH));
        float frac = maxScroll > 0 ? (float) scroll / maxScroll : 0f;
        int thumbY = top + (int) ((visH - thumbH) * frac);
        g.fill(x, thumbY, x + w, thumbY + thumbH, 0xFF666666);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        int contentTop = getContentTop();
        int visH       = height - contentTop;
        int maxScroll  = Math.max(0, totalContentH - visH);
        scroll = clamp(scroll + (va > 0 ? -24 : 24), 0, maxScroll);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        long window  = GLFW.glfwGetCurrentContext();
        boolean pressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (pressed && !wasPressed) {
            int contentTop = getContentTop();

            // Icon filter bar clicks (above contentTop)
            for (Map.Entry<String, int[]> entry : iconBounds.entrySet()) {
                int[] b = entry.getValue();
                if (lastMouseX >= b[0] && lastMouseX < b[0] + b[2]
                        && lastMouseY >= b[1] && lastMouseY < b[1] + b[3]) {
                    String cat = entry.getKey();
                    RubixConfig cfg = RubixConfig.get();
                    if (cfg.hiddenCategories.contains(cat)) {
                        cfg.hiddenCategories.remove(cat);
                    } else {
                        cfg.hiddenCategories.add(cat);
                    }
                    RubixConfig.save();
                    totalContentH = computeTotalHeight();
                    int visH = height - contentTop;
                    scroll = clamp(scroll, 0, Math.max(0, totalContentH - visH));
                    wasPressed = true;
                    return;
                }
            }

            // Category header collapse clicks (inside content area)
            if (lastMouseY >= contentTop) {
                for (Map.Entry<String, int[]> entry : categoryHeaderBounds.entrySet()) {
                    int[] b = entry.getValue();
                    if (lastMouseX >= b[0] && lastMouseX < b[0] + b[2]
                            && lastMouseY >= b[1] && lastMouseY < b[1] + b[3]) {
                        String cat = entry.getKey();
                        RubixConfig cfg = RubixConfig.get();
                        if (cfg.collapsedCategories.contains(cat)) {
                            cfg.collapsedCategories.remove(cat);
                        } else {
                            cfg.collapsedCategories.add(cat);
                        }
                        RubixConfig.save();
                        totalContentH = computeTotalHeight();
                        int visH = height - contentTop;
                        scroll = clamp(scroll, 0, Math.max(0, totalContentH - visH));
                        wasPressed = true;
                        return;
                    }
                }
            }
        }
        wasPressed = pressed;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Category colors ───────────────────────────────────────────────────────

    static int getCategoryColor(String cat) {
        String name = displayName(cat);
        if (cat.startsWith("Fishing")) return 0xFF55AAFF;
        switch (name) {
            case "Crimson Isle":        return 0xFFFF4444;
            case "Kuudra":              return 0xFFCC2233;
            case "Crystal Hollows":     return 0xFF9977CC;
            case "Deep Caverns":        return 0xFF9977CC;
            case "Dwarven Mines":       return 0xFF9977CC;
            case "The End":             return 0xFFAA55FF;
            case "Spider's Den":        return 0xFFBB7711;
            case "Hub":                 return 0xFF77BBFF;
            case "Your Island":         return 0xFF55FF88;
            case "The Farming Islands": return 0xFFCCFF44;
            case "The Catacombs":       return 0xFFFF8800;
            case "The Park":            return 0xFF33BB55;
            case "Mythological Creatures": return 0xFFFFDD00;
            case "Jerry":               return 0xFF00DDFF;
            case "Spooky Festival":     return 0xFFFF6622;
            case "Galatea":             return 0xFF77FFEE;
            case "The Garden":          return 0xFF99FF44;
            default:                    return 0xFFFFAA00;
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    static String displayName(String cat) {
        return cat.startsWith("Bestiary > ") ? cat.substring(11)
             : cat.startsWith("Fishing > ")  ? cat.substring(9)
             : cat;
    }

    private String truncate(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "\u2026") > maxW)
            s = s.substring(0, s.length() - 1);
        return s + "\u2026";
    }

    private static String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 10_000)    return String.format("%.1fk", n / 1_000.0);
        if (n >= 1_000)     return String.format("%,d", n);
        return String.valueOf(n);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Darkens an ARGB colour by a factor (0.0 = black, 1.0 = original). */
    private static int darken(int color, float f) {
        int a = (color >> 24) & 0xFF;
        int r = (int)(((color >> 16) & 0xFF) * f);
        int g = (int)(((color >>  8) & 0xFF) * f);
        int b = (int)(( color        & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Brightens an ARGB colour by a factor (0.0 = no change, 1.0 = white). */
    private static int brighten(int color, float f) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int)(((color >> 16) & 0xFF) * (1 + f)));
        int g = Math.min(255, (int)(((color >>  8) & 0xFF) * (1 + f)));
        int b = Math.min(255, (int)(( color        & 0xFF) * (1 + f)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static final java.util.regex.Pattern ROMAN_SUFFIX =
            java.util.regex.Pattern.compile("\\s+(X{0,2}(?:IX|IV|V?I{0,3}))$");

    private static String normalize(String s) {
        s = ROMAN_SUFFIX.matcher(s).replaceAll("").trim();
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
