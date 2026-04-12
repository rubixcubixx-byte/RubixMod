package com.rubixmod.bestiary;

import com.rubixmod.RubixMod;
import com.rubixmod.config.RubixConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final Map<String, Long> sessionCounts = new HashMap<>();

    /**
     * Tier data from the tab list, keyed by trackedKey ("Category > Mob").
     * Updated every time the tab list is read.
     */
    private static final Map<String, TierInfo> tierData = new HashMap<>();

    /**
     * Mobs that have been maxed this session — prevents re-firing the MAX animation
     * every second after the mob is first detected as maxed.
     */
    private static final java.util.Set<String> maxedThisSession = new java.util.HashSet<>();

    // ── TierInfo ──────────────────────────────────────────────────────────────

    /**
     * Live tier data read directly from the Hypixel tab list for a single mob.
     */
    public static class TierInfo {
        /** Current tier number shown in the tab list (e.g. 10). */
        public final int tierNum;
        /** Current total kill count shown in the tab list. */
        public final long tabCurrent;
        /** Total kill count at which this tier ends (next tier starts). */
        public final long tabTierMax;
        /** Total kill count at which this tier started (set when tier advances). */
        public final long tabTierStart;

        TierInfo(int tierNum, long tabCurrent, long tabTierMax, long tabTierStart) {
            this.tierNum     = tierNum;
            this.tabCurrent  = tabCurrent;
            this.tabTierMax  = tabTierMax;
            this.tabTierStart = tabTierStart;
        }
    }

    /**
     * Returns the most recent tier data for the given trackedKey, or null if
     * the tab list has not yet seen this mob.
     */
    public static TierInfo getTierInfo(String trackedKey) {
        return tierData.get(trackedKey);
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getConnection() == null) return;
            if (++tickCount % 20 != 0) return; // once per second

            readTabList(client.getConnection().getListedOnlinePlayers());
        });
    }

    // ── Main reader ───────────────────────────────────────────────────────────

    private static void readTabList(Collection players) {
        boolean updatedAny      = false;
        boolean tierCacheChanged = false;

        for (Object obj : players) {
            PlayerInfo info = (PlayerInfo) obj;
            if (info.getTabListDisplayName() == null) continue;

            String line = info.getTabListDisplayName().getString()
                    .replaceAll("\u00a7.", "").trim();

            Matcher m = ENTRY_PATTERN.matcher(line);
            if (!m.matches()) continue;

            String mobName  = m.group(1).trim();
            boolean isMax   = m.group(5) != null; // matched "MAX"
            int     tabTierNum = 0;
            long    tabTierMax = 0;

            try {
                tabTierNum = Integer.parseInt(m.group(2));
            } catch (NumberFormatException ignored) {}

            long tabCurrent = 0;
            if (!isMax) {
                try {
                    tabCurrent = Long.parseLong(m.group(3).replace(",", ""));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                try {
                    tabTierMax = Long.parseLong(m.group(4).replace(",", ""));
                } catch (NumberFormatException ignored) {}
            }

            // Resolve ALL categories that contain this mob name.
            List<String[]> allMatches = findAllCategoryAndMob(mobName);
            if (allMatches.isEmpty()) continue;

            // Pick best matching category:
            // - For non-MAX: closest stored current kills to the tab value.
            // - For MAX: the category whose stored count is closest to its stored max
            //   (i.e. closest to completion — it's the one being actively ground).
            String[] bestMatch = null;
            long bestDiff = Long.MAX_VALUE;

            for (String[] catMob : allMatches) {
                long[] existing = BestiaryData.getKills(catMob[0], catMob[1]);
                if (existing == null || existing[1] <= 0) continue;

                long diff;
                if (isMax) {
                    // Prefer the category closest to maxed (smallest gap to completion)
                    diff = existing[1] - existing[0];
                    if (diff < 0) diff = 0;
                } else {
                    diff = Math.abs(existing[0] - tabCurrent);
                }

                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestMatch = catMob;
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

            // Update tier data (non-MAX entries only — MAX has no current/max values)
            if (!isMax && tabTierNum > 0 && tabTierMax > 0) {
                TierInfo prev = tierData.get(trackedKey);
                long tierStart;
                if (prev == null) {
                    // First sight this session — try to restore the saved start from config so
                    // the bar position survives log-offs and game restarts.
                    tierStart = restoreTierStart(trackedKey, tabTierNum, tabCurrent);
                } else if (tabTierNum > prev.tierNum) {
                    // Tier advanced: previous tier's max is the exact start of the new tier
                    tierStart = prev.tabTierMax;
                } else {
                    // Same tier — keep the in-memory start
                    tierStart = prev.tabTierStart;
                }
                tierData.put(trackedKey, new TierInfo(tabTierNum, tabCurrent, tabTierMax, tierStart));

                // Persist the start so it survives restarts; only write when something changed
                String cacheEntry = tabTierNum + "," + tierStart;
                if (!cacheEntry.equals(RubixConfig.get().tierStartCache.get(trackedKey))) {
                    RubixConfig.get().tierStartCache.put(trackedKey, cacheEntry);
                    tierCacheChanged = true;
                }
            }

            // Touch the HUD when:
            // - We've seen this mob before (not a cold-start baseline read)
            // - AND the count has gone up (new kill) OR the mob just became maxed
            boolean justMaxed = isMax && (lastSeen == null || lastSeen < storedMax);
            if (lastSeen != null && current > lastSeen) {
                LiveMobTracker.touch(trackedKey);
            } else if (justMaxed && lastSeen != null) {
                // Mob reached max — push to HUD so it shows "MAX" for 3 minutes
                LiveMobTracker.touch(trackedKey);
            }

            // Fire the special MAX animation exactly once per session per mob
            if (justMaxed && lastSeen != null && !maxedThisSession.contains(trackedKey)) {
                maxedThisSession.add(trackedKey);
                BestiaryTierUpHandler.onMobMaxed(bestMatch[1]);
            }

            sessionCounts.put(trackedKey, current);
        }

        if (updatedAny) {
            BestiaryData.save();
        }
        if (tierCacheChanged) {
            RubixConfig.save();
        }
    }

    /**
     * Tries to restore a saved tierStart from the config for the given mob and tier.
     * Falls back to tabCurrent (bar starts at 0% this session) if nothing is saved
     * or if the saved entry belongs to a different tier number.
     */
    private static long restoreTierStart(String trackedKey, int tabTierNum, long tabCurrent) {
        String saved = RubixConfig.get().tierStartCache.get(trackedKey);
        if (saved != null) {
            String[] parts = saved.split(",", 2);
            if (parts.length == 2) {
                try {
                    int savedTier  = Integer.parseInt(parts[0]);
                    long savedStart = Long.parseLong(parts[1]);
                    if (savedTier == tabTierNum) {
                        return savedStart;   // same tier — restore saved position
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return tabCurrent;  // no saved data or different tier — start fresh
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
