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

            // Resolve category and mob from BestiaryMobList
            String[] catMob = findCategoryAndMob(mobName);
            if (catMob == null) continue;

            // Always use the stored max — never overwrite it from the tab list
            long[] existing = BestiaryData.getKills(catMob[0], catMob[1]);
            if (existing == null || existing[1] <= 0) continue; // no stored max, skip

            long storedMax = existing[1];
            long current;

            if (isMax) {
                // "MAX" means fully completed — treat as current == max
                current = storedMax;
            } else {
                try {
                    current = Long.parseLong(m.group(3).replace(",", ""));
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }

            // Only write if current kill count actually changed
            if (existing[0] != current) {
                BestiaryData.saveMob(catMob[0], catMob[1], current, storedMax);
                // Notify live tracker so Auto Track HUD can show this mob
                String trackedKey = catMob[0] + " > " + catMob[1];
                LiveMobTracker.touch(trackedKey);
                updatedAny = true;
            }
        }

        if (updatedAny) {
            BestiaryData.save();
            RubixMod.LOGGER.info("RubixMod: Tab list bestiary data updated.");
        }
    }

    /** Looks up which (category, canonicalMobName) pair matches the given mob name. */
    private static String[] findCategoryAndMob(String mobName) {
        // Search regular categories
        for (Object catObj : BestiaryMobList.CATEGORIES.keySet()) {
            String cat = (String) catObj;
            if (cat.equals("Fishing")) continue;
            List mobs = (List) BestiaryMobList.CATEGORIES.get(cat);
            for (Object mobObj : mobs) {
                String mob = (String) mobObj;
                if (mob.equalsIgnoreCase(mobName)) return new String[]{cat, mob};
            }
        }
        // Search fishing subcategories
        for (Object subKeyObj : BestiaryMobList.FISHING_SUBCATEGORY_KEYS) {
            String subKey = (String) subKeyObj;
            List mobs = (List) BestiaryMobList.FISHING_SUBCATEGORIES.get(subKey);
            for (Object mobObj : mobs) {
                String mob = (String) mobObj;
                if (mob.equalsIgnoreCase(mobName)) return new String[]{subKey, mob};
            }
        }
        return null;
    }
}
