package com.rubixmod.entity;

import com.rubixmod.bestiary.BestiaryData;
import com.rubixmod.bestiary.LiveMobTracker;
import com.rubixmod.config.RubixConfig;
import com.rubixmod.mining.LittlefootTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains a per-tick map of entity ID → glow colour for two groups:
 *
 *  1. Littlefoot entities tracked by LittlefootTracker  — always orange
 *  2. Mobs currently shown in the Bestiary HUD           — one colour per slot
 *
 * The mixin {@code LittlefootGlowMixin} queries this manager to decide
 * whether to force {@code isCurrentlyGlowing()} and what colour to return
 * from {@code getTeamColor()}.
 */
public class EntityGlowManager {

    /** Fixed orange for Littlefoot — always wins over HUD palette. */
    private static final int LITTLEFOOT_COLOR = 0xFF8C00;

    /**
     * Eight distinct colours assigned to HUD mob slots in order.
     * Slot 0 = first tracked mob, slot 1 = second, etc.
     * Chosen to be vivid and clearly distinguishable from each other.
     */
    static final int[] PALETTE = {
            0xFF5555, // red
            0x55FF55, // green
            0x55FFFF, // cyan
            0xFFFF55, // yellow
            0xFF55FF, // magenta
            0x5599FF, // light blue
            0xAAFF44, // lime
            0xFF99AA, // pink
    };

    /** Entity ID → glow colour. Rebuilt from scratch every tick. */
    private static final Map<Integer, Integer> glowMap = new HashMap<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            glowMap.clear();
            if (client.level == null || client.player == null) return;
            update(client);
        });
    }

    private static void update(Minecraft client) {
        RubixConfig cfg = RubixConfig.get();

        // Build ordered list of plain mob names from the HUD tracked list.
        // Only populated when the HUD is enabled — matches what the player sees.
        List<String> hudNames = new ArrayList<>();
        if (cfg.hudEnabled) {
            List<?> tracked = cfg.hudAutoTrack
                    ? LiveMobTracker.getActiveMobs()
                    : cfg.trackedMobs;
            int max = Math.max(1, Math.min(15, cfg.hudMaxMobs));
            int count = Math.min(tracked.size(), max);
            for (int i = 0; i < count; i++) {
                String[] parts = BestiaryData.parseTrackedKey((String) tracked.get(i));
                if (parts != null) hudNames.add(parts[1].toLowerCase());
            }
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            // Skip players — their names can accidentally match mob names.
            if (entity instanceof Player) continue;

            // Littlefoot: highest priority regardless of HUD state.
            if (cfg.littlefootTrackerEnabled && LittlefootTracker.isTracked(entity)) {
                glowMap.put(entity.getId(), LITTLEFOOT_COLOR);
                continue;
            }

            // HUD mobs: match entity display name against each tracked mob name.
            if (!hudNames.isEmpty()) {
                String eName = strippedName(entity);
                if (eName != null) {
                    for (int i = 0; i < hudNames.size(); i++) {
                        String mob = hudNames.get(i);
                        // Use contains both ways to handle level prefixes like "[Lv200] Zombie"
                        if (eName.contains(mob) || mob.contains(eName)) {
                            glowMap.put(entity.getId(), PALETTE[i % PALETTE.length]);
                            break;
                        }
                    }
                }
            }
        }
    }

    /** Strips Minecraft colour codes and lowercases the entity's display/custom name. */
    private static String strippedName(Entity entity) {
        String raw = entity.hasCustomName()
                ? entity.getCustomName().getString()
                : entity.getDisplayName().getString();
        if (raw == null || raw.isBlank()) return null;
        return raw.replaceAll("\u00a7.", "").toLowerCase().trim();
    }

    public static boolean isGlowing(int entityId) {
        return glowMap.containsKey(entityId);
    }

    public static Integer getColor(int entityId) {
        return glowMap.get(entityId);
    }
}
