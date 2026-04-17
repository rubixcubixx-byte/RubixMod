package com.rubixmod.bestiary;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;

public class BestiaryTierUpHandler {

    public static class TierUpEntry {
        public final String mobName;
        public final int newTier;
        public final int tiersGained;
        public final long timestamp;
        public final boolean isMaxEvent;

        public TierUpEntry(String mobName, int newTier, int tiersGained) {
            this(mobName, newTier, tiersGained, false);
        }

        public TierUpEntry(String mobName, int newTier, int tiersGained, boolean isMaxEvent) {
            this.mobName = mobName;
            this.newTier = newTier;
            this.tiersGained = tiersGained;
            this.timestamp = System.currentTimeMillis();
            this.isMaxEvent = isMaxEvent;
        }

        public boolean isExpired() {
            long duration = isMaxEvent ? 8000L : 5000L;
            return System.currentTimeMillis() - timestamp > duration;
        }
    }

    private static final ArrayList activePopups = new ArrayList();
    private static final int MAX_POPUPS = 8;

    /** When true (HUD editor is open), getActivePopups() never removes expired entries. */
    private static boolean previewMode = false;

    public static void setPreviewMode(boolean enabled) {
        previewMode = enabled;
    }

    public static void onTierUp(String mobName, int newTier, int tiersGained) {
        activePopups.add(new TierUpEntry(mobName, newTier, tiersGained, false));
        if (activePopups.size() > MAX_POPUPS) {
            activePopups.remove(0);
        }

        // Play XP orb pickup sound
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.execute(() -> client.player.playSound(
                        SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Called when a mob reaches its final bestiary tier (fully maxed).
     * Triggers a special rainbow "MAXED" popup with firework sounds.
     */
    public static void onMobMaxed(String mobName) {
        activePopups.add(new TierUpEntry(mobName, -1, 0, true));
        if (activePopups.size() > MAX_POPUPS) {
            activePopups.remove(0);
        }

        // Play firework sounds for the special MAX event
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.execute(() -> {
                    client.player.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
                    client.player.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
                    client.player.playSound(SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.8f, 1.0f);
                });
            }
        } catch (Exception ignored) {}
    }

    public static ArrayList getActivePopups() {
        // Don't remove entries while the HUD editor is open — the preview must stay visible
        if (!previewMode) {
            for (int i = activePopups.size() - 1; i >= 0; i--) {
                TierUpEntry entry = (TierUpEntry) activePopups.get(i);
                if (entry.isExpired()) {
                    activePopups.remove(i);
                }
            }
        }
        ArrayList copy = new ArrayList();
        copy.addAll(activePopups);
        return copy;
    }

    /** Returns all popups without filtering expired ones — used by the HUD editor preview. */
    public static ArrayList getRawPopups() {
        ArrayList copy = new ArrayList();
        copy.addAll(activePopups);
        return copy;
    }

    public static void clear() {
        activePopups.clear();
    }
}
