package com.rubixmod.bestiary;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BestiaryMenuReader {

    private static final Pattern OVERALL_PROGRESS_PATTERN = Pattern.compile("Overall Progress:");
    private static final Pattern FRACTION_PATTERN = Pattern.compile("^([\\d,.]+[kKmM]?)/([\\d,.]+[kKmM]?)$");
    private static final Pattern KILLS_PATTERN = Pattern.compile("(?:Kills|Catches):\\s*([\\d,]+)");

    /** Parse a kill count that may use k/m suffix (e.g. "4k" → 4000, "1.5k" → 1500). */
    private static long parseCount(String s) {
        s = s.replace(",", "").trim();
        if (s.endsWith("k") || s.endsWith("K")) {
            return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1_000);
        }
        if (s.endsWith("m") || s.endsWith("M")) {
            return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1_000_000);
        }
        return Long.parseLong(s);
    }

    // Roman numeral suffix: covers I through XXIX
    private static final Pattern ROMAN_SUFFIX = Pattern.compile(
            "\\s+(X{0,2}(?:IX|IV|V?I{0,3}))$"
    );

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen)) return;
            AbstractContainerScreen containerScreen = (AbstractContainerScreen) screen;

            String rawTitle = containerScreen.getTitle().getString();
            String cleanTitle = rawTitle.replaceAll("\u00a7.", "").trim();

            String category = extractCategory(cleanTitle);
            if (category == null) return;

            final String finalCategory = category;
            final int[] ticksWaited  = {0};
            final int[] lastNonEmpty = {-1};
            final boolean[] hasRead  = {false};
            ScreenEvents.afterTick(screen).register(s -> {
                if (hasRead[0]) return;
                if (++ticksWaited[0] < 3) return; // minimum wait for first packets
                if (!(s instanceof AbstractContainerScreen)) return;
                AbstractContainerScreen cs = (AbstractContainerScreen) s;

                int nonEmpty = 0;
                for (Object item : cs.getMenu().getItems()) {
                    if (!((ItemStack) item).isEmpty()) nonEmpty++;
                }

                boolean stable = (nonEmpty > 0 && nonEmpty == lastNonEmpty[0]);
                lastNonEmpty[0] = nonEmpty;

                // Read once slot count has been stable for 1 tick,
                // or after 20 ticks as an absolute fallback.
                if (stable || ticksWaited[0] >= 20) {
                    readItems(cs, finalCategory);
                    hasRead[0] = true;
                }
            });
        });
    }

    private static String extractCategory(String clean) {
        // Strip leading page indicator like "(1/2) "
        clean = clean.replaceAll("^\\(\\d+/\\d+\\)\\s*", "").trim();

        // Top-level Bestiary screen — skip
        if (clean.equals("Bestiary")) return null;

        // Normalize: replace arrow characters Hypixel uses with " > "
        String normalized = clean
                .replace("\u279c", ">")   // ➜ (heavy round-tipped arrow — Hypixel's actual arrow)
                .replace("\u27a4", ">")   // ➤ (black curved arrow)
                .replace("\u2192", ">")   // → (standard right arrow)
                .replaceAll("\\s*>\\s*", " > ")
                .trim();

        // "Bestiary > Your Island" -> "Your Island"
        if (normalized.startsWith("Bestiary > ")) {
            String sub = normalized.substring("Bestiary > ".length()).trim();
            // Fix truncated category names
            if (sub.startsWith("Mythological")) return "Mythological Creatures";
            // "Bestiary > Fishing" is the subcategory selector — skip it
            if (sub.equals("Fishing")) return null;
            return sub;
        }

        // "Fishing > Lava" or "Fishing > Fishing" -> keep as-is (actual mob screens)
        if (normalized.startsWith("Fishing > ")) {
            return normalized;
        }

        return null;
    }

    /**
     * Counts how many items in the inventory have kill data (Overall Progress or Kills line)
     * in their lore. Used to detect when the server has finished sending all item data.
     */
    private static int countItemsWithKillData(AbstractContainerScreen screen) {
        int count = 0;
        for (Object obj : screen.getMenu().getItems()) {
            ItemStack stack = (ItemStack) obj;
            if (stack.isEmpty()) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            for (Component line : lore.lines()) {
                String text = line.getString().replaceAll("\u00a7.", "").trim();
                if (OVERALL_PROGRESS_PATTERN.matcher(text).find()
                        || KILLS_PATTERN.matcher(text).find()) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static boolean readItems(AbstractContainerScreen screen, String category) {
        ArrayList slots = new ArrayList(screen.getMenu().getItems());
        boolean savedAny = false;
        List savedNames  = new ArrayList();
        List skippedNames = new ArrayList();

        // Only scan the chest portion — getItems() includes player's 36 inventory slots at the end.
        int containerSize = Math.max(0, slots.size() - 36);

        // UI decoration names to ignore
        java.util.Set uiNames = new java.util.HashSet(java.util.Arrays.asList(
                "next page", "previous page", "go back", "close", "info"
        ));

        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = (ItemStack) slots.get(i);
            if (stack.isEmpty()) continue;

            String rawName = stack.getHoverName().getString().replaceAll("\u00a7.", "").trim();
            if (rawName.isEmpty()) continue;
            if (uiNames.contains(rawName.toLowerCase())) continue;

            // Strip Roman numeral tier suffix: "Oasis Rabbit XIV" -> "Oasis Rabbit"
            String mobName = ROMAN_SUFFIX.matcher(rawName).replaceAll("").trim();
            if (mobName.isEmpty()) mobName = rawName;

            // Read lore directly from the data component — more reliable than getTooltipLines
            ItemLore itemLore = stack.get(DataComponents.LORE);
            if (itemLore == null) {
                skippedNames.add(mobName + " (no lore)");
                continue;
            }
            List<Component> lore = itemLore.lines();

            long currentKills = -1;
            long maxKills = -1;
            boolean nextLineIsFraction = false;
            List loreLines = new ArrayList(); // for skip diagnostics

            for (int j = 0; j < lore.size(); j++) {
                Component line = lore.get(j);
                String lineStr = line.getString().replaceAll("\u00a7.", "").trim();
                if (!lineStr.isEmpty()) loreLines.add(lineStr);

                if (nextLineIsFraction) {
                    Matcher frac = FRACTION_PATTERN.matcher(lineStr);
                    if (frac.find()) {
                        try {
                            currentKills = parseCount(frac.group(1));
                            maxKills = parseCount(frac.group(2));
                        } catch (NumberFormatException ignored) {}
                    }
                    nextLineIsFraction = false;
                    continue;
                }

                if (OVERALL_PROGRESS_PATTERN.matcher(lineStr).find()) {
                    Matcher inlineFrac = Pattern.compile("([\\d,.]+[kKmM]?)/([\\d,.]+[kKmM]?)").matcher(lineStr);
                    if (inlineFrac.find()) {
                        try {
                            currentKills = parseCount(inlineFrac.group(1));
                            maxKills = parseCount(inlineFrac.group(2));
                        } catch (NumberFormatException ignored) {}
                    } else {
                        nextLineIsFraction = true;
                    }
                    continue;
                }

                if (currentKills < 0) {
                    Matcher killsMatcher = KILLS_PATTERN.matcher(lineStr);
                    if (killsMatcher.find()) {
                        try {
                            currentKills = Long.parseLong(killsMatcher.group(1).replace(",", ""));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (currentKills >= 0 && maxKills > 0) {
                String canonicalCategory = resolveCanonicalCategory(mobName, category);
                BestiaryData.saveMob(canonicalCategory, mobName, currentKills, maxKills);
                savedAny = true;
                savedNames.add(mobName);
            } else {
                skippedNames.add(mobName + " [" + loreLines + "]");
            }
        }

        if (savedAny) {
            BestiaryData.save();
        }

        // Check for a next page arrow (container slots only)
        boolean hasNextPage = false;
        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = (ItemStack) slots.get(i);
            if (stack.isEmpty()) continue;
            if (stack.getHoverName().getString().replaceAll("\u00a7.", "").trim()
                    .equalsIgnoreCase("Next Page")) {
                hasNextPage = true;
                break;
            }
        }

        return savedAny;
    }

    /**
     * Returns the canonical category name from BestiaryMobList for the given mob.
     * Always prefers the screen-derived category when the mob belongs there,
     * so mobs that appear in multiple categories (e.g. Enderman in "Your Island"
     * and "The End") are stored separately and correctly.
     */
    private static String resolveCanonicalCategory(String mobName, String fallback) {
        // If the mob exists in the category we're currently scanning, trust the screen title.
        // This is the primary path and correctly handles duplicate mob names across categories.
        if (mobExistsInCategory(mobName, fallback)) return fallback;

        // Mob not in the expected category — search all to handle any Hypixel title quirks.
        for (Object catObj : BestiaryMobList.CATEGORIES.keySet()) {
            String cat = (String) catObj;
            if (cat.equals("Fishing")) continue;
            List mobs = (List) BestiaryMobList.CATEGORIES.get(cat);
            if (mobs == null) continue;
            for (Object mob : mobs) {
                if (((String) mob).equalsIgnoreCase(mobName)) return cat;
            }
        }
        // Check fishing subcategories
        for (Object subKeyObj : BestiaryMobList.FISHING_SUBCATEGORY_KEYS) {
            String subKey = (String) subKeyObj;
            List mobs = (List) BestiaryMobList.FISHING_SUBCATEGORIES.get(subKey);
            if (mobs == null) continue;
            for (Object mob : mobs) {
                if (((String) mob).equalsIgnoreCase(mobName)) return subKey;
            }
        }
        return fallback;
    }

    /** Returns true if mobName is listed under the given category in BestiaryMobList. */
    private static boolean mobExistsInCategory(String mobName, String category) {
        List mobs = (List) BestiaryMobList.CATEGORIES.get(category);
        if (mobs != null) {
            for (Object mob : mobs) {
                if (((String) mob).equalsIgnoreCase(mobName)) return true;
            }
        }
        List fishMobs = (List) BestiaryMobList.FISHING_SUBCATEGORIES.get(category);
        if (fishMobs != null) {
            for (Object mob : fishMobs) {
                if (((String) mob).equalsIgnoreCase(mobName)) return true;
            }
        }
        return false;
    }
}