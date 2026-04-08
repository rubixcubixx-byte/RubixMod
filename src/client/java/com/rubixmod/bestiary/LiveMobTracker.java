package com.rubixmod.bestiary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks which bestiary mobs have been recently killed via the tab list.
 * Used by the Auto Track HUD mode to show only mobs you are actively grinding.
 *
 * Entries expire after EXPIRE_MS of inactivity and are removed automatically.
 */
public class LiveMobTracker {

    /** How long a mob stays on the HUD without a new kill (3 minutes). */
    private static final long EXPIRE_MS = 3 * 60 * 1000L;

    /**
     * Insertion-ordered map: trackedKey -> last-update timestamp (ms).
     * New mobs are appended to the tail; existing mobs keep their position.
     */
    private static final LinkedHashMap<String, Long> active = new LinkedHashMap<>();

    /**
     * Called by TabListBestiaryReader when a mob's kill count changes.
     * Refreshes the expiry timer without moving the mob in the list.
     * New mobs are added at the top (newest-first display order).
     * @param trackedKey  e.g. "Hub > Zombie" or "Fishing > Lava > Fiery Scuttler"
     */
    public static synchronized void touch(String trackedKey) {
        // put() updates value but preserves insertion order for existing keys,
        // so position only changes when the mob is brand new to the list.
        active.put(trackedKey, System.currentTimeMillis());
    }

    /**
     * Returns the list of currently active mob keys, newest-added first.
     * Automatically removes entries older than EXPIRE_MS.
     */
    public static synchronized List getActiveMobs() {
        long now = System.currentTimeMillis();

        // Purge expired entries
        active.entrySet().removeIf(e -> now - e.getValue() > EXPIRE_MS);

        // Reverse so newest additions appear at the top
        List keys = new ArrayList(active.keySet());
        java.util.Collections.reverse(keys);
        return keys;
    }

    /** Returns true if there are any active (non-expired) entries. */
    public static synchronized boolean hasActiveMobs() {
        long now = System.currentTimeMillis();
        for (Object val : active.values()) {
            if (now - (Long) val <= EXPIRE_MS) return true;
        }
        return false;
    }

    public static synchronized void clear() {
        active.clear();
    }
}
