package com.rubixmod.bestiary;

import com.rubixmod.config.RubixConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BestiaryMenuReader {

    private static final Pattern OVERALL_PROGRESS_PATTERN = Pattern.compile("Overall Progress:");
    private static final Pattern FRACTION_PATTERN = Pattern.compile("^([\\d,.]+[kKmM]?)/([\\d,.]+[kKmM]?)$");
    private static final Pattern KILLS_PATTERN = Pattern.compile("(?:Kills|Catches):\\s*([\\d,]+)");
    private static final Pattern CAP_TIER_PATTERN = Pattern.compile("Capped at Tier\\s+([IVXLCDM]+)");

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

    // Roman numeral tier suffix stripper: covers I through XXIX
    private static final Pattern ROMAN_SUFFIX = Pattern.compile(
            "\\s+(X{0,2}(?:IX|IV|V?I{0,3}))$"
    );

    /** Converts a roman numeral string to an int. Returns 0 on invalid input. */
    private static int fromRoman(String roman) {
        int result = 0, prev = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int curr;
            switch (roman.charAt(i)) {
                case 'I': curr = 1;    break;
                case 'V': curr = 5;    break;
                case 'X': curr = 10;   break;
                case 'L': curr = 50;   break;
                case 'C': curr = 100;  break;
                case 'D': curr = 500;  break;
                case 'M': curr = 1000; break;
                default:  return 0;
            }
            result += (curr < prev) ? -curr : curr;
            prev = curr;
        }
        return result;
    }

    /**
     * Scan buffer: category -> (mobName -> [currentKills, maxKills, capTier]).
     * Accumulates across multiple pages of the same category; only committed once
     * the last page is detected (no "Next Page" button present).
     */
    private static final HashMap<String, LinkedHashMap<String, long[]>> scanBuffer = new HashMap<>();

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen)) return;
            AbstractContainerScreen containerScreen = (AbstractContainerScreen) screen;

            String rawTitle = containerScreen.getTitle().getString();
            String cleanTitle = rawTitle.replaceAll("§.", "").trim();

            // Parse the page indicator (e.g. "(1/3)") before extractCategory strips it.
            // No indicator at all means it's a single-page screen (counts as first + last page).
            Matcher pageMatcher = Pattern.compile("^\\((\\d+)/(\\d+)\\)").matcher(cleanTitle);
            final boolean isFirstPage;
            if (pageMatcher.find()) {
                isFirstPage = Integer.parseInt(pageMatcher.group(1)) == 1;
            } else {
                isFirstPage = true;
            }

            String category = extractCategory(cleanTitle);
            if (category == null) return;

            // Clear the buffer whenever we start scanning a category from page 1,
            // so a fresh visit always reflects the current state of the bestiary.
            if (isFirstPage) {
                scanBuffer.remove(category);
            }

            final String finalCategory = category;
            final int[] ticksWaited  = {0};
            final int[] lastNonEmpty = {-1};
            final boolean[] hasRead  = {false};
            ScreenEvents.afterTick(screen).register(s -> {
                if (hasRead[0]) return;
                if (++ticksWaited[0] < 3) return;
                if (!(s instanceof AbstractContainerScreen)) return;
                AbstractContainerScreen cs = (AbstractContainerScreen) s;

                int nonEmpty = 0;
                for (Object item : cs.getMenu().getItems()) {
                    if (!((ItemStack) item).isEmpty()) nonEmpty++;
                }

                boolean stable = (nonEmpty > 0 && nonEmpty == lastNonEmpty[0]);
                lastNonEmpty[0] = nonEmpty;

                if (stable || ticksWaited[0] >= 20) {
                    boolean hasNextPage = readItemsToBuffer(cs, finalCategory);
                    if (!hasNextPage) {
                        // Last page scanned — replace stored data with exactly what we found.
                        // This adds new mobs, updates existing ones, and removes deleted ones.
                        commitScan(finalCategory);
                    }
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

        // Normalize arrow characters Hypixel uses
        String normalized = clean
                .replace("➜", ">")   // ➜
                .replace("➤", ">")   // ➤
                .replace("→", ">")   // →
                .replaceAll("\\s*>\\s*", " > ")
                .trim();

        // "Bestiary > Your Island" -> "Your Island"
        if (normalized.startsWith("Bestiary > ")) {
            String sub = normalized.substring("Bestiary > ".length()).trim();
            if (sub.startsWith("Mythological")) return "Mythological Creatures";
            if (sub.equals("Fishing")) return null; // subcategory selector, not a mob screen
            return sub;
        }

        // "Fishing > Lava" etc. -> keep as-is
        if (normalized.startsWith("Fishing > ")) {
            return normalized;
        }

        return null;
    }

    /**
     * Reads all mob items from the screen into the scan buffer for the given category.
     * Returns true if a "Next Page" button was found (more pages remain to be scanned).
     */
    private static boolean readItemsToBuffer(AbstractContainerScreen screen, String category) {
        ArrayList slots = new ArrayList(screen.getMenu().getItems());
        // Only scan the chest portion — getItems() appends player's 36 inventory slots at the end
        int containerSize = Math.max(0, slots.size() - 36);

        java.util.Set uiNames = new java.util.HashSet(java.util.Arrays.asList(
                "next page", "previous page", "go back", "close", "info"
        ));

        LinkedHashMap buffer = (LinkedHashMap) scanBuffer.computeIfAbsent(
                category, k -> new LinkedHashMap<>());

        boolean hasNextPage = false;

        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = (ItemStack) slots.get(i);
            if (stack.isEmpty()) continue;

            String rawName = stack.getHoverName().getString().replaceAll("§.", "").trim();
            if (rawName.isEmpty()) continue;

            if (rawName.equalsIgnoreCase("Next Page")) { hasNextPage = true; continue; }
            if (uiNames.contains(rawName.toLowerCase())) continue;

            // Strip Roman numeral tier suffix: "Oasis Rabbit XIV" -> "Oasis Rabbit"
            String mobName = ROMAN_SUFFIX.matcher(rawName).replaceAll("").trim();
            if (mobName.isEmpty()) mobName = rawName;

            ItemLore itemLore = stack.get(DataComponents.LORE);
            if (itemLore == null) continue;
            List<Component> lore = itemLore.lines();

            long currentKills = -1;
            long maxKills = -1;
            long capTier = -1;
            boolean nextLineIsFraction = false;

            for (Component line : lore) {
                String lineStr = line.getString().replaceAll("§.", "").trim();

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

                // Parse "Capped at Tier XV" to store the mob's max tier number
                if (capTier < 0) {
                    Matcher capMatcher = CAP_TIER_PATTERN.matcher(lineStr);
                    if (capMatcher.find()) {
                        int t = fromRoman(capMatcher.group(1).trim());
                        if (t > 0) capTier = t;
                    }
                }
            }

            if (currentKills >= 0 && maxKills > 0) {
                buffer.put(mobName, new long[]{currentKills, maxKills, capTier});
            }
        }

        return hasNextPage;
    }

    /**
     * Commits the accumulated scan buffer for a category to BestiaryData.
     * Replaces the stored mob list with exactly what was scanned across all pages:
     * new mobs are added, existing ones are updated, removed ones are deleted.
     * Any tracked HUD mobs that were removed are also cleaned from the config.
     */
    private static void commitScan(String category) {
        LinkedHashMap buffer = (LinkedHashMap) scanBuffer.remove(category);
        if (buffer == null || buffer.isEmpty()) return;

        // Replace stored data; returns the set of mob names that no longer exist
        Set removed = BestiaryData.replaceCategory(category, buffer);
        BestiaryData.save();

        // Remove any tracked-mob HUD entries for mobs no longer in this category
        if (!removed.isEmpty()) {
            List trackedMobs = RubixConfig.get().trackedMobs;
            boolean changed = false;
            for (Object mob : removed) {
                if (trackedMobs.remove(category + " > " + mob)) changed = true;
            }
            if (changed) RubixConfig.save();
        }
    }
}
