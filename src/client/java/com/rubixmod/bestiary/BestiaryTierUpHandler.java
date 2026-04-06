package com.rubixmod.bestiary;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;

public class BestiaryTierUpHandler {

    public static class TierUpEntry {
        public final String mobName;
        public final int newTier;
        public final int tiersGained;
        public final long timestamp;

        public TierUpEntry(String mobName, int newTier, int tiersGained) {
            this.mobName = mobName;
            this.newTier = newTier;
            this.tiersGained = tiersGained;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 5000;
        }
    }

    private static final ArrayList activePopups = new ArrayList();
    private static final int MAX_POPUPS = 8;

    public static void onTierUp(String mobName, int newTier, int tiersGained) {
        activePopups.add(new TierUpEntry(mobName, newTier, tiersGained));
        if (activePopups.size() > MAX_POPUPS) {
            activePopups.remove(0);
        }

        // Play XP orb pickup sound
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.execute(() -> {
                    client.player.playSound(
                            SoundEvents.EXPERIENCE_ORB_PICKUP,
                            1.0f,  // volume
                            1.0f   // pitch
                    );
                });
            }
        } catch (Exception e) {
            // Silently ignore sound errors
        }
    }

    public static ArrayList getActivePopups() {
        for (int i = activePopups.size() - 1; i >= 0; i--) {
            TierUpEntry entry = (TierUpEntry) activePopups.get(i);
            if (entry.isExpired()) {
                activePopups.remove(i);
            }
        }
        ArrayList copy = new ArrayList();
        copy.addAll(activePopups);
        return copy;
    }

    public static void clear() {
        activePopups.clear();
    }
}