package com.rubixmod.bestiary;

import com.rubixmod.RubixMod;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BestiaryMenuReader {

    private static final Pattern OVERALL_PROGRESS_PATTERN = Pattern.compile("Overall Progress:");
    private static final Pattern FRACTION_PATTERN = Pattern.compile("^([\\d,]+)/([\\d,]+)$");
    private static final Pattern KILLS_PATTERN = Pattern.compile("Kills:\\s*([\\d,]+)");

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

            // Log every chest screen title so we can see what Hypixel sends
            RubixMod.LOGGER.info("RubixMod: Container screen opened: '{}'", cleanTitle);

            String category = extractCategory(cleanTitle);
            if (category == null) return;

            RubixMod.LOGGER.info("RubixMod: Bestiary category detected: '{}'", category);

            final String finalCategory = category;
            ScreenEvents.afterTick(screen).register(s -> {
                if (!(s instanceof AbstractContainerScreen)) return;
                readItems((AbstractContainerScreen) s, finalCategory);
            });
        });
    }

    private static String extractCategory(String clean) {
        // Strip leading page indicator like "(1/2) "
        clean = clean.replaceAll("^\\(\\d+/\\d+\\)\\s*", "").trim();

        // Top-level Bestiary screen — skip
        if (clean.equals("Bestiary")) return null;

        // Normalize: replace the ➜ arrow (and any surrounding whitespace) with " > "
        // Also handle any other arrow-like characters Hypixel might use
        String normalized = clean
                .replace("\u27a4", ">")   // ➜
                .replace("\u2192", ">")   // →
                .replaceAll("\\s*>\\s*", " > ")
                .trim();

        // "Bestiary > Your Island" -> "Your Island"
        if (normalized.startsWith("Bestiary > ")) {
            String sub = normalized.substring("Bestiary > ".length()).trim();
            // Fix truncated category names
            if (sub.startsWith("Mythological")) return "Mythological Creatures";
            return sub;
        }

        // "Fishing > Lava" -> "Fishing > Lava" (keep as-is, already normalized)
        if (normalized.startsWith("Fishing > ")) {
            return normalized;
        }

        return null;
    }

    private static void readItems(AbstractContainerScreen screen, String category) {
        ArrayList slots = new ArrayList(screen.getMenu().getItems());
        boolean savedAny = false;

        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = (ItemStack) slots.get(i);
            if (stack.isEmpty()) continue;

            String rawName = stack.getHoverName().getString().replaceAll("\u00a7.", "").trim();
            if (rawName.isEmpty()) continue;

            // Strip Roman numeral tier suffix: "Oasis Rabbit XIV" -> "Oasis Rabbit"
            String mobName = ROMAN_SUFFIX.matcher(rawName).replaceAll("").trim();
            if (mobName.isEmpty()) mobName = rawName;

            ArrayList lore = new ArrayList(stack.getTooltipLines(
                    net.minecraft.world.item.Item.TooltipContext.EMPTY,
                    null,
                    TooltipFlag.NORMAL
            ));

            long currentKills = -1;
            long maxKills = -1;
            boolean nextLineIsFraction = false;

            for (int j = 0; j < lore.size(); j++) {
                net.minecraft.network.chat.Component line =
                        (net.minecraft.network.chat.Component) lore.get(j);
                String lineStr = line.getString().replaceAll("\u00a7.", "").trim();

                if (nextLineIsFraction) {
                    Matcher frac = FRACTION_PATTERN.matcher(lineStr);
                    if (frac.find()) {
                        try {
                            currentKills = Long.parseLong(frac.group(1).replace(",", ""));
                            maxKills = Long.parseLong(frac.group(2).replace(",", ""));
                        } catch (NumberFormatException ignored) {}
                    }
                    nextLineIsFraction = false;
                    continue;
                }

                if (OVERALL_PROGRESS_PATTERN.matcher(lineStr).find()) {
                    Matcher inlineFrac = Pattern.compile("([\\d,]+)/([\\d,]+)").matcher(lineStr);
                    if (inlineFrac.find()) {
                        try {
                            currentKills = Long.parseLong(inlineFrac.group(1).replace(",", ""));
                            maxKills = Long.parseLong(inlineFrac.group(2).replace(",", ""));
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
                BestiaryData.saveMob(category, mobName, currentKills, maxKills);
                savedAny = true;
                RubixMod.LOGGER.info("RubixMod: Saved '{} > {}': {}/{}", category, mobName, currentKills, maxKills);
            }
        }

        if (savedAny) {
            BestiaryData.save();
        }
    }
}