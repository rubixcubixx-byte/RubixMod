package com.rubixmod.entity;

import com.rubixmod.bestiary.BestiaryData;
import com.rubixmod.bestiary.LiveMobTracker;
import com.rubixmod.config.RubixConfig;
import com.rubixmod.mining.LittlefootTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EntityGlowManager {

    private static final int LITTLEFOOT_COLOR = 0xFF8C00;

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

    public static void register() {
        // Tick-based pass: full scan every tick to keep glow state correct and evict stale cache entries.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) return;
            update(client);
        });
        // Instant identification is handled via ClientLevelEntityLoadMixin → onEntityAdded().
    }

    private static int glowCount = 0; // not used for logging; kept for future metrics

    /**
     * Persists entity UUID → mob name across ticks.
     * Populated when a match is found (direct name or ArmorStand proximity).
     * Allows us to re-identify a fake-player entity on ticks where its ArmorStand
     * nametag hasn't been streamed in yet (common for mobs at medium range).
     */
    private static final Map<UUID, String> entityGlowCache = new HashMap<>();

    // -----------------------------------------------------------------------
    // Instant entity-load handler
    // -----------------------------------------------------------------------

    /**
     * Called by EntityNameUpdateMixin the instant an ArmorStand's custom name is set
     * (including delayed metadata updates — the common case on Hypixel where the name
     * packet arrives 3-4 seconds after the entity itself).
     */
    public static void onArmorStandNameSet(Minecraft client, Entity armorStand, ClientLevel level) {
        RubixConfig cfg = RubixConfig.get();
        if (!cfg.hudEnabled) return;

        List<String> hudNames = buildHudNames(cfg);
        if (hudNames.isEmpty()) return;

        String name = strippedName(armorStand);
        if (name == null) return;

        for (int i = 0; i < hudNames.size(); i++) {
            if (matchesMobName(name, hudNames.get(i))) {
                String mobName = hudNames.get(i);
                int color = PALETTE[i % PALETTE.length];
                // Prefer an already-cached entity so a mob jumping on top can't steal
                // the match. Fall back to raw closest only if no cached entity is nearby.
                Entity match = null;
                double matchDist = 4.0;
                for (Entity nearby : level.entitiesForRendering()) {
                    if (nearby instanceof ArmorStand) continue;
                    if (isRealPlayer(client, nearby)) continue;
                    double d = nearby.distanceTo(armorStand);
                    if (d >= 4.0) continue;
                    boolean cached = mobName.equals(entityGlowCache.get(nearby.getUUID()));
                    if (cached && d < matchDist) { matchDist = d; match = nearby; }
                }
                if (match == null) {
                    for (Entity nearby : level.entitiesForRendering()) {
                        if (nearby instanceof ArmorStand) continue;
                        if (isRealPlayer(client, nearby)) continue;
                        double d = nearby.distanceTo(armorStand);
                        if (d < matchDist) { matchDist = d; match = nearby; }
                    }
                }
                if (match != null) {
                    entityGlowCache.put(match.getUUID(), mobName);
                    if (match instanceof RubixEntityGlowAccess glow) {
                        glow.rubix$setGlowing(true, color, true);
                    }
                }
                break;
            }
        }
    }

    /**
     * Called by ClientLevelEntityLoadMixin the instant an entity is added to the world.
     *
     * Two cases:
     *  A) An ArmorStand with a tracked mob name just loaded — delegate to onArmorStandNameSet.
     *  B) A non-ArmorStand entity just loaded — check cache or existing nearby ArmorStands
     *     so it glows immediately if we already know what it is.
     */
    public static void onEntityAdded(Minecraft client, Entity entity, ClientLevel level) {
        RubixConfig cfg = RubixConfig.get();
        if (!cfg.hudEnabled) return;

        List<String> hudNames = buildHudNames(cfg);
        if (hudNames.isEmpty()) return;

        if (entity instanceof ArmorStand) {
            // Delegate — handles the case where the name is already set at load time.
            onArmorStandNameSet(client, entity, level);
        } else {
            // Case B: new mob entity — try to identify it right away.
            if (isRealPlayer(client, entity)) return;
            if (!(entity instanceof RubixEntityGlowAccess glow)) return;

            int matchIdx = -1;

            // 1. Direct name on the entity.
            String eName = strippedName(entity);
            if (eName != null) {
                for (int i = 0; i < hudNames.size(); i++) {
                    if (matchesMobName(eName, hudNames.get(i))) { matchIdx = i; break; }
                }
            }

            // 2. Nearby ArmorStand already in the world.
            if (matchIdx < 0) {
                outer:
                for (Entity as : level.entitiesForRendering()) {
                    if (!(as instanceof ArmorStand)) continue;
                    if (entity.distanceTo(as) >= 4.0) continue;
                    String asName = strippedName(as);
                    if (asName == null) continue;
                    for (int i = 0; i < hudNames.size(); i++) {
                        if (matchesMobName(asName, hudNames.get(i))) { matchIdx = i; break outer; }
                    }
                }
            }

            // 3. UUID cache (entity was seen in a previous spawn / session tick).
            if (matchIdx < 0) {
                String cachedName = entityGlowCache.get(entity.getUUID());
                if (cachedName != null) {
                    for (int i = 0; i < hudNames.size(); i++) {
                        if (hudNames.get(i).equals(cachedName)) { matchIdx = i; break; }
                    }
                }
            }

            if (matchIdx >= 0) {
                entityGlowCache.put(entity.getUUID(), hudNames.get(matchIdx));
                glow.rubix$setGlowing(true, PALETTE[matchIdx % PALETTE.length], true);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Per-tick full scan (keeps state consistent, evicts stale cache entries)
    // -----------------------------------------------------------------------

    private static void update(Minecraft client) {
        glowCount = 0;
        RubixConfig cfg = RubixConfig.get();

        List<String> hudNames = buildHudNames(cfg);

        // Pre-pass: for each tracked ArmorStand, identify the single closest mob entity
        // (skipped entirely when highlighting is disabled)
        // within 4 blocks. Storing only the closest prevents nearby unrelated mobs from
        // being incorrectly outlined just because they walked past the ArmorStand.
        // Map: entity UUID → HUD name index.
        Map<UUID, Integer> armorStandMatches = new HashMap<>();
        if (!hudNames.isEmpty() && cfg.highlightBestiaryMobs) {
            for (Entity as : client.level.entitiesForRendering()) {
                if (!(as instanceof ArmorStand)) continue;
                String name = strippedName(as);
                if (name == null) continue;
                for (int i = 0; i < hudNames.size(); i++) {
                    if (matchesMobName(name, hudNames.get(i))) {
                        // Prefer an already-cached entity (the one we previously identified as
                        // this mob type) so a different mob jumping on top can't steal the match.
                        // Fall back to the raw closest entity only if no cached one is nearby.
                        Entity match = null;
                        double matchDist = 4.0;
                        for (Entity nearby : client.level.entitiesForRendering()) {
                            if (nearby instanceof ArmorStand) continue;
                            if (isRealPlayer(client, nearby)) continue;
                            double d = nearby.distanceTo(as);
                            if (d >= 4.0) continue;
                            boolean cached = hudNames.get(i).equals(entityGlowCache.get(nearby.getUUID()));
                            if (cached && d < matchDist) { matchDist = d; match = nearby; }
                        }
                        if (match == null) { // no cached entity nearby — take the closest
                            for (Entity nearby : client.level.entitiesForRendering()) {
                                if (nearby instanceof ArmorStand) continue;
                                if (isRealPlayer(client, nearby)) continue;
                                double d = nearby.distanceTo(as);
                                if (d < matchDist) { matchDist = d; match = nearby; }
                            }
                        }
                        if (match != null) armorStandMatches.put(match.getUUID(), i);
                        break;
                    }
                }
            }
        }

        // Main pass.
        Set<UUID> seenThisTick = new HashSet<>();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof RubixEntityGlowAccess glow)) continue;
            if (entity instanceof ArmorStand) {
                glow.rubix$setGlowing(false, 0, false);
                continue;
            }
            if (isRealPlayer(client, entity)) {
                glow.rubix$setGlowing(false, 0, false);
                continue;
            }

            seenThisTick.add(entity.getUUID());

            if (cfg.littlefootTrackerEnabled && LittlefootTracker.isTracked(entity)) {
                glow.rubix$setGlowing(true, LITTLEFOOT_COLOR, true);
                glowCount++;
                continue;
            }

            if (!hudNames.isEmpty() && cfg.highlightBestiaryMobs) {
                int matchIdx = -1;

                // 1. Direct name match on the entity itself.
                String eName = strippedName(entity);
                if (eName != null) {
                    for (int i = 0; i < hudNames.size(); i++) {
                        if (matchesMobName(eName, hudNames.get(i))) { matchIdx = i; break; }
                    }
                }

                // 2. ArmorStand fallback: only if this entity is the closest to its ArmorStand.
                if (matchIdx < 0) {
                    Integer idx = armorStandMatches.get(entity.getUUID());
                    if (idx != null) matchIdx = idx;
                }

                // 3. UUID cache fallback.
                if (matchIdx < 0) {
                    String cachedName = entityGlowCache.get(entity.getUUID());
                    if (cachedName != null) {
                        for (int i = 0; i < hudNames.size(); i++) {
                            if (hudNames.get(i).equals(cachedName)) { matchIdx = i; break; }
                        }
                    }
                }

                if (matchIdx >= 0) {
                    entityGlowCache.put(entity.getUUID(), hudNames.get(matchIdx));
                    // Show outlined mobs through walls within 30 blocks; hide beyond that.
                    boolean show = entity.distanceTo(client.player) <= 30.0;
                    glow.rubix$setGlowing(show, PALETTE[matchIdx % PALETTE.length], show);
                    if (show) glowCount++;
                } else {
                    glow.rubix$setGlowing(false, 0, false);
                }
                continue;
            }

            glow.rubix$setGlowing(false, 0, false);
        }

        // Evict cache entries for entities no longer in render range / despawned.
        entityGlowCache.keySet().removeIf(uuid -> !seenThisTick.contains(uuid));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Builds the ordered list of lowercase mob names currently shown in the HUD. */
    private static List<String> buildHudNames(RubixConfig cfg) {
        List<String> hudNames = new ArrayList<>();
        if (!cfg.hudEnabled) return hudNames;
        List<?> tracked = cfg.hudAutoTrack
                ? LiveMobTracker.getActiveMobs()
                : cfg.trackedMobs;
        int max = Math.max(1, Math.min(15, cfg.hudMaxMobs));
        int count = Math.min(tracked.size(), max);
        for (int i = 0; i < count; i++) {
            String[] parts = BestiaryData.parseTrackedKey((String) tracked.get(i));
            if (parts != null) hudNames.add(parts[1].toLowerCase());
        }
        return hudNames;
    }

    /**
     * True for the local player and any player whose UUID is in the server's player list.
     * Fake player entities that Hypixel uses for custom mob models return false.
     */
    private static boolean isRealPlayer(Minecraft client, Entity entity) {
        if (!(entity instanceof Player)) return false;
        if (entity == client.player) return true;
        return client.getConnection() != null
                && client.getConnection().getPlayerInfo(entity.getUUID()) != null;
    }

    /**
     * True when mobName matches the name portion of an entity display name.
     *
     * Hypixel entity names look like "[Lv100] Wolf 5000/5000❤" or "♣ Old Wolf 250/250❤".
     * We strip the level/symbol prefix, then require the mob name to appear at the very
     * start of what remains — so "wolf" never matches an "old wolf" entity because after
     * stripping the prefix we get "old wolf …" which does not start with "wolf".
     */
    private static boolean matchesMobName(String entityName, String mobName) {
        String s = entityName;
        // Strip leading non-letter characters (brackets, symbols like ♣)
        s = s.replaceAll("^[^a-z]+", "");
        // Strip level prefix: "lv100 ", "lv100] ", etc.
        s = s.replaceAll("^lv\\s*\\d+[a-z]*\\]?\\s*", "");
        // Strip any remaining non-letter prefix after the bracket close
        s = s.replaceAll("^[^a-z]+", "");
        // Mob name must be at the very start of what remains
        if (!s.startsWith(mobName)) return false;
        int end = mobName.length();
        return end >= s.length() || !Character.isLetterOrDigit(s.charAt(end));
    }

    private static String strippedName(Entity entity) {
        String raw = entity.hasCustomName()
                ? entity.getCustomName().getString()
                : entity.getDisplayName().getString();
        if (raw == null || raw.isBlank()) return null;
        return raw.replaceAll("\u00a7.", "").toLowerCase().trim();
    }

    /**
     * True if at least one of three sample points on the entity has an unobstructed
     * line of sight from the player's eye. Retained for optional future use.
     */
    @SuppressWarnings("unused")
    private static boolean isVisible(Minecraft client, Entity entity) {
        if (client.player == null) return false;
        Vec3 eye = client.player.getEyePosition();
        net.minecraft.world.phys.AABB box = entity.getBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        Vec3[] targets = {
            entity.getEyePosition(),
            new Vec3(cx, (box.minY + box.maxY) * 0.5, cz),
            new Vec3(cx, box.maxY - 0.1, cz),
        };
        for (Vec3 target : targets) {
            BlockHitResult hit = client.level.clip(new ClipContext(
                    eye, target,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    client.player));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }
}
