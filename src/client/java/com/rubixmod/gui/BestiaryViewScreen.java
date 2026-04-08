package com.rubixmod.gui;

import com.rubixmod.bestiary.BestiaryData;
import com.rubixmod.bestiary.BestiaryMobList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

public class BestiaryViewScreen extends Screen {

    // ── Colours (matching BestiaryHud) ────────────────────────────────────────
    private static final int C_BG         = 0xFF111111;
    private static final int C_HEADER_BG  = 0xFF1A1A1A;
    private static final int C_CARD_BG    = 0xFF1C1C1C;
    private static final int C_CARD_BD    = 0xFF3A3A3A;
    private static final int C_CARD_BD_LOCKED = 0xFF2A2A2A;
    private static final int C_BAR_BG     = 0xFF2A2A2A;
    private static final int C_BAR_FG     = 0xFF55AA00;
    private static final int C_BAR_LOCKED = 0xFF333333;
    private static final int C_ACCENT_LOW  = 0xFF884444; // < 40%
    private static final int C_ACCENT_MID  = 0xFF887700; // 40–74%
    private static final int C_ACCENT_HIGH = 0xFF3A7A20; // 75–99%
    private static final int C_ORANGE     = 0xFFFFAA00;
    private static final int C_WHITE      = 0xFFFFFFFF;
    private static final int C_GRAY       = 0xFF888888;
    private static final int C_GRAY_DIM   = 0xFF555555;
    private static final int C_DIVIDER    = 0xFF333333;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int HEADER_H = 30;
    private static final int PADDING  = 10;
    private static final int CARD_W   = 160;
    private static final int CARD_H   = 38;
    private static final int CARD_GAP = 4;
    private static final int CAT_H    = 20;
    private static final int CAT_PRE  = 12;

    // ── State ─────────────────────────────────────────────────────────────────
    private int scroll        = 0;
    private int totalContentH = 0;

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
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        categories.clear();
        catMobs.clear();

        // Build ordered category list from BestiaryMobList (canonical order),
        // then append any scanned categories not in the static list.
        Set<String> allCats = new LinkedHashSet<>();
        for (Object c : BestiaryMobList.CATEGORIES.keySet()) {
            String s = (String) c;
            if (!s.equals("Fishing")) allCats.add(s); // skip placeholder
        }
        for (Object s : BestiaryMobList.FISHING_SUBCATEGORY_KEYS) allCats.add((String) s);
        for (Object c : BestiaryData.getCategories()) allCats.add((String) c);

        for (String cat : allCats) {
            // Case-insensitive merge: lowercase key → display name.
            // BestiaryMobList provides canonical names; BestiaryData keys override
            // so that getKills() lookups always use the exact stored key.
            Map<String, String> mobKeyMap = new LinkedHashMap<>();
            List known = (List) BestiaryMobList.CATEGORIES.get(cat);
            if (known == null) known = (List) BestiaryMobList.FISHING_SUBCATEGORIES.get(cat);
            if (known != null) {
                for (Object m : known) {
                    String s = (String) m;
                    mobKeyMap.put(s.toLowerCase(), s);
                }
            }
            // Scanned data keys override — these are what getKills() needs exactly
            for (Object m : BestiaryData.getMobsInCategory(cat)) {
                String s = (String) m;
                mobKeyMap.put(s.toLowerCase(), s);
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

    private int computeTotalHeight() {
        int cols = columns();
        int h = PADDING;
        for (String cat : categories) {
            List<String> mobs = catMobs.getOrDefault(cat, List.of());
            if (mobs.isEmpty()) continue;
            h += CAT_PRE + CAT_H;
            int rows = (mobs.size() + cols - 1) / cols;
            h += rows * (CARD_H + CARD_GAP) - CARD_GAP + PADDING;
        }
        return h + PADDING;
    }

    private int columns() {
        return Math.max(1, (width - PADDING * 2 + CARD_GAP) / (CARD_W + CARD_GAP));
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, C_BG);

        // Header
        g.fill(0, 0, width, HEADER_H, C_HEADER_BG);
        g.fill(0, HEADER_H, width, HEADER_H + 1, C_DIVIDER);
        g.drawString(font, "Bestiary Overview", PADDING, 11, C_ORANGE);
        String hint = "Scroll  ·  ESC to close";
        g.drawString(font, hint, width - font.width(hint) - PADDING, 11, C_GRAY);

        // Scrollable content
        int listTop   = HEADER_H + 1;
        int visH      = height - listTop;
        int maxScroll = Math.max(0, totalContentH - visH);
        scroll = clamp(scroll, 0, maxScroll);

        g.enableScissor(0, listTop, width, height);
        renderContent(g, listTop);
        g.disableScissor();

        if (totalContentH > visH) drawScrollBar(g, width - 6, listTop, 4, visH, maxScroll);

        super.render(g, mouseX, mouseY, delta);
    }

    private void renderContent(GuiGraphics g, int listTop) {
        int cols = columns();
        int y = listTop - scroll + PADDING;

        for (String cat : categories) {
            List<String> mobs = catMobs.getOrDefault(cat, List.of());
            if (mobs.isEmpty()) continue;

            y += CAT_PRE;
            if (y + CAT_H > listTop && y < height) renderCategoryHeader(g, cat, y, cols);
            y += CAT_H;

            int rows = (mobs.size() + cols - 1) / cols;
            for (int i = 0; i < mobs.size(); i++) {
                int col   = i % cols;
                int row   = i / cols;
                int cardX = PADDING + col * (CARD_W + CARD_GAP);
                int cardY = y + row * (CARD_H + CARD_GAP);
                if (cardY + CARD_H > listTop && cardY < height) {
                    renderCard(g, cat, mobs.get(i), cardX, cardY);
                }
            }
            y += rows * (CARD_H + CARD_GAP) - CARD_GAP + PADDING;
        }
    }

    // ── Category header ───────────────────────────────────────────────────────

    private void renderCategoryHeader(GuiGraphics g, String cat, int y, int cols) {
        String name  = displayName(cat);
        int gridW    = cols * (CARD_W + CARD_GAP) - CARD_GAP;
        int textW    = font.width(name);
        int textX    = PADDING + (gridW - textW) / 2;
        int lineY    = y + CAT_H / 2;

        g.fill(PADDING,           lineY, textX - 5,       lineY + 1, C_DIVIDER);
        g.fill(textX + textW + 5, lineY, PADDING + gridW, lineY + 1, C_DIVIDER);
        g.drawString(font, name, textX, y + (CAT_H - 8) / 2, C_ORANGE);
    }

    // ── Mob card ──────────────────────────────────────────────────────────────

    private void renderCard(GuiGraphics g, String cat, String mob, int x, int y) {
        long[] kills  = BestiaryData.getKills(cat, mob);
        boolean hasData = kills != null;
        long cur = hasData ? kills[0] : 0;
        long max = hasData ? kills[1] : 0;
        boolean maxed = hasData && max > 0 && cur >= max;

        // Card background
        g.fill(x, y, x + CARD_W, y + CARD_H, C_CARD_BG);

        // Border — dimmer for locked cards
        int bd = hasData ? C_CARD_BD : C_CARD_BD_LOCKED;
        g.fill(x,              y,              x + CARD_W, y + 1,          bd);
        g.fill(x,              y + CARD_H - 1, x + CARD_W, y + CARD_H,     bd);
        g.fill(x,              y,              x + 1,      y + CARD_H,     bd);
        g.fill(x + CARD_W - 1, y,              x + CARD_W, y + CARD_H,     bd);

        // Left accent stripe — colour-coded by progress state
        int accent = !hasData ? C_CARD_BD_LOCKED
                   : maxed   ? C_ORANGE
                   : max > 0 && (float) cur / max >= 0.75f ? C_ACCENT_HIGH
                   : max > 0 && (float) cur / max >= 0.40f ? C_ACCENT_MID
                   : C_ACCENT_LOW;
        g.fill(x, y, x + 3, y + CARD_H, accent);

        // Mob name
        int nameColor = maxed ? C_ORANGE : (hasData ? C_WHITE : C_GRAY_DIM);
        String name = truncate(mob, CARD_W - 8);
        g.drawString(font, name, x + 4, y + 3, nameColor, false);

        // Progress bar
        int barX = x + 4;
        int barY = y + CARD_H - 14;
        int barW = CARD_W - 8;
        int barH = 10;

        if (!hasData) {
            // Not yet unlocked / scanned
            g.fill(barX, barY, barX + barW, barY + barH, C_BAR_LOCKED);
            String msg = "Not unlocked";
            g.drawString(font, msg,
                    barX + barW / 2 - font.width(msg) / 2, barY + 2, C_GRAY_DIM, false);

        } else if (maxed) {
            // Gold bar + white MAX
            g.fill(barX, barY, barX + barW, barY + barH, C_ORANGE);
            String done = "MAX";
            g.drawString(font, done,
                    barX + barW / 2 - font.width(done) / 2, barY + 2, C_WHITE, false);

        } else {
            // Partial progress
            g.fill(barX, barY, barX + barW, barY + barH, C_BAR_BG);
            if (max > 0) {
                int filled = (int) Math.min(barW, cur * barW / max);
                if (filled > 0) g.fill(barX, barY, barX + filled, barY + barH, C_BAR_FG);
            }
            String countText = fmt(cur) + " / " + fmt(max);
            g.drawString(font, countText,
                    barX + barW / 2 - font.width(countText) / 2, barY + 2, C_WHITE, false);
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
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        int visH      = height - HEADER_H - 1;
        int maxScroll = Math.max(0, totalContentH - visH);
        scroll = clamp(scroll + (va > 0 ? -24 : 24), 0, maxScroll);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String displayName(String cat) {
        return cat.startsWith("Bestiary > ") ? cat.substring(11) : cat;
    }

    private String truncate(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "…") > maxW)
            s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 10_000)    return String.format("%.1fk", n / 1_000.0);
        if (n >= 1_000)     return String.format("%,d", n);
        return String.valueOf(n);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
