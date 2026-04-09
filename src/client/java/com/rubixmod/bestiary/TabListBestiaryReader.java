package com.rubixmod.bestiary;

import com.rubixmod.RubixMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Hypixel tab list Bestiary section every second and live-updates
 * BestiaryData so the HUD reflects kills immediately without opening /bestiary.
 *
 * Tab list entries look like:
 *   "Sea Archer 14: 3,282/4,000"
 *   "Blue Jerry 10: MAX"
 */
public class TabListBestiaryReader {

    // Matches "[Mob Name] [Tier]: [current]/[max]" or "[Mob Name] [Tier]: MAX"
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "^(.+?)\\s+(\\d+):\\s+(?:([\\d,]+)/([\\d,]+)|(MAX))$"
    );

    private static int tickCount = 0;

    /**
     * Per-session kill counts last seen in the tab list.
     * First time we see a mob this session we just record the count — no HUD touch.
     * On subsequent reads, a changed count means you actually killed something.
     */
    private static final java.util.Map<String, Long> sessionCounts = new java.util.HashMap<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getConnection() == null) return;
            if (++tickCount % 20 != 0) return; // once per second

            readTabList(client.getConnection().getListedOnlinePlayers());
        });
    }

    private static void readTabList(Collection players) {
        boolean updatedAny = false;

        for (Object obj : players) {
            PlayerInfo info = (PlayerInfo) obj;
            if (info.getTabListDisplayName() == null) continue;

            String line = info.getTabListDisplayName().getString()
                    .replaceAll("\u00a7.", "").trim();

            Matcher m = ENTRY_PATTERN.matcher(line);
            if (!m.matches()) continue;

            String mobName = m.group(1).trim();
            boolean isMax  = m.group(5) != null; // matched "MAX"

            // Resolve ALL categories that contain this mob name.
            // This handles mobs that share a name across categories (e.g. Enderman
            // in "Your Island" and "The End") — we update whichever one has stored
            // data and a changed kill count.
            List<String[]> allMatches = findAllCategoryAndMob(mobName);
            if (allMatches.isEmpty()) continue;

            long tabCurrent = 0;
            if (!isMax) {
                try {
                    tabCurrent = Long.parseLong(m.group(3).replace(",", ""));
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }

            // For mobs that appear in multiple categories (e.g. Enderman in "Your Island"
            // and "The End"), pick the category whose stored kill count is closest to the
            // tab list value — the mob you're actively grinding will always be closest.
            // For MAX entries, only apply to categories already at their stored max.
            // For unique mobs, just use whichever category has stored data.
            String[] bestMatch = null;
            long bestDiff = Long.MAX_VALUE;

            for (String[] catMob : allMatches) {
                long[] existing = BestiaryData.getKills(catMob[0], catMob[1]);
                if (existing == null || existing[1] <= 0) continue;

                if (isMax) {
                    // Only apply MAX to a category that is already fully complete
                    if (existing[0] >= existing[1]) {
                        bestMatch = catMob;
                        break;
                    }
                } else {
                    long diff = Math.abs(existing[0] - tabCurrent);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestMatch = catMob;
                    }
                }
            }

            if (bestMatch == null) continue;

            long[] existing = BestiaryData.getKills(bestMatch[0], bestMatch[1]);
            if (existing == null) continue;
            long storedMax = existing[1];
            long current   = isMax ? storedMax : tabCurrent;

            String trackedKey = bestMatch[0] + " > " + bestMatch[1];
            Long lastSeen = sessionCounts.get(trackedKey);

            // Always keep BestiaryData up to date
            if (existing[0] != current) {
                BestiaryData.saveMob(bestMatch[0], bestMatch[1], current, storedMax);
                updatedAny = true;
            }

            // Only touch the HUD if we already saw this mob this session AND
            // its count has since gone up — proving you just actively killed it.
            // First appearance is just recorded silently to establish a baseline,
            // preventing stale-data mismatches on area load from polluting the HUD.
            if (lastSeen != null && current > lastSeen && current < storedMax) {
                LiveMobTracker.touch(trackedKey);
            }
            sessionCounts.put(trackedKey, current);
        }

        if (updatedAny) {
            BestiaryData.save();
            RubixMod.LOGGER.info("RubixMod: Tab list bestiary data updated.");
        }
    }

    /** Returns every (category, canonicalMobName) pair that matches the given mob name. */
    private static List<String[]> findAllCategoryAndMob(String mobName) {
        List<String[]> results = new java.util.ArrayList<>();
        for (Object catObj : BestiaryMobList.CATEGORIES.keySet()) {
            String cat = (String) catObj;
            if (cat.equals("Fishing")) continue;
            List mobs = (List) BestiaryMobList.CATEGORIES.get(cat);
            for (Object mobObj : mobs) {
                String mob = (String) mobObj;
                if (mob.equalsIgnoreCase(mobName)) results.add(new String[]{cat, mob});
            }
        }
        for (Object subKeyObj : BestiaryMobList.FISHING_SUBCATEGORY_KEYS) {
            String subKey = (String) subKeyObj;
            List mobs = (List) BestiaryMobList.FISHING_SUBCATEGORIES.get(subKey);
            for (Object mobObj : mobs) {
                String mob = (String) mobObj;
                if (mob.equalsIgnoreCase(mobName)) results.add(new String[]{subKey, mob});
            }
        }
        return results;
    }
}
