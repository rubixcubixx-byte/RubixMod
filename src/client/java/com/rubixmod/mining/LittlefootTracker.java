package com.rubixmod.mining;

import com.rubixmod.config.RubixConfig;
import com.rubixmod.mixin.ClientLevelEntitiesAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans ALL loaded entities every tick for any entity whose name contains
 * "Littlefoot", but ONLY while the player is inside a Glacite Mineshaft.
 *
 * Mineshaft state is re-checked every 20 ticks (1s) so the per-tick entity
 * scan stays cheap. The state is only cleared when we positively confirm the
 * player has left — a brief tab-list update delay won't wipe detections.
 */
public class LittlefootTracker {

    private static final List<Entity> tracked = new ArrayList<>();

    /** Entity IDs that have already had their coords posted to party chat. */
    private static final Set<Integer> announcedIds = new HashSet<>();

    /**
     * Block positions [x, y, z] of every Littlefoot that has been announced.
     * Used to deduplicate announcements when the same mob re-enters tracking range
     * with a different entity ID (Hypixel assigns a new ID on each re-spawn packet).
     */
    private static final List<int[]> announcedPositions = new ArrayList<>();

    // ── Mineshaft state (re-checked every 20 ticks) ───────────────────────────
    private static boolean inMineshaft    = false;
    private static int     locationTick   = 0;
    private static final int LOCATION_CHECK_INTERVAL = 20;

    // ── Detection timestamp (local) ───────────────────────────────────────────
    private static long lastDetectionTime = 0;
    public static long getLastDetectionTime() { return lastDetectionTime; }

    // ── Remote detection (another player found one) ───────────────────────────
    // Parses party chat including the coordinates so we can check for same-instance duplicates.
    // Format: "Party > [RANK] Name: [RubixMod] LITTLEFOOT found! Coords: X, Y, Z"
    private static final Pattern PARTY_LITTLEFOOT = Pattern.compile(
            "Party > (?:\\[.+?\\] )?(\\w+): \\[RubixMod\\] LITTLEFOOT found! Coords: (-?\\d+), (-?\\d+), (-?\\d+)");
    private static String remoteFinderName   = null;
    private static long   remoteDetectionTime = 0;
    public static String getRemoteFinderName()    { return remoteFinderName; }
    public static long   getRemoteDetectionTime() { return remoteDetectionTime; }

    // ── Beep sequencer ────────────────────────────────────────────────────────
    private static int beepsRemaining = 0;
    private static int beepCooldown   = 0;
    private static final int BEEP_GAP_TICKS = 12; // ~600 ms between beeps

    // ─────────────────────────────────────────────────────────────────────────

    public static void register() {

        // ── Listen for party chat from other RubixMod users ──────────────────
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            String text = message.getString().replaceAll("\u00a7.", "").trim();
            Matcher m = PARTY_LITTLEFOOT.matcher(text);
            if (!m.find()) return;

            String finder = m.group(1);
            // Ignore our own message echoed back
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && finder.equalsIgnoreCase(
                    mc.player.getGameProfile().name())) return;

            // Parse the coordinates from the remote message
            double rx = Double.parseDouble(m.group(2));
            double ry = Double.parseDouble(m.group(3));
            double rz = Double.parseDouble(m.group(4));

            // If we're already tracking a Littlefoot at nearly the same position,
            // we're in the same instance — suppress the remote popup to avoid duplicate alerts.
            for (Entity e : tracked) {
                double dx = e.getX() - rx;
                double dy = e.getY() - ry;
                double dz = e.getZ() - rz;
                if (dx * dx + dy * dy + dz * dz < 10 * 10) return; // within 10 blocks = same mob
            }

            remoteFinderName    = finder;
            remoteDetectionTime = System.currentTimeMillis();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            tracked.clear();
            announcedIds.clear();
            announcedPositions.clear();
            inMineshaft    = false;
            locationTick   = LOCATION_CHECK_INTERVAL; // check immediately on first tick
            beepsRemaining = 0;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!RubixConfig.get().littlefootTrackerEnabled) {
                tracked.clear();
                inMineshaft = false;
                return;
            }
            if (client.level == null || client.player == null) {
                tracked.clear();
                inMineshaft  = false;
                locationTick = LOCATION_CHECK_INTERVAL;
                return;
            }

            // ── Fire pending beeps ────────────────────────────────────────────
            if (beepsRemaining > 0) {
                if (--beepCooldown <= 0) {
                    client.player.playSound(
                            SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
                    beepsRemaining--;
                    beepCooldown = BEEP_GAP_TICKS;
                }
            }

            // ── Re-check mineshaft state every 20 ticks ───────────────────────
            if (++locationTick >= LOCATION_CHECK_INTERVAL) {
                locationTick = 0;
                inMineshaft  = checkIsInGlaciteMineshaft();
                if (!inMineshaft) {
                    tracked.clear();
                    announcedIds.clear();
                    announcedPositions.clear();
                    return;
                }
            }

            // If we've never confirmed we're in the mineshaft yet, skip scan
            if (!inMineshaft) return;

            // ── Scan every tick ───────────────────────────────────────────────
            tracked.clear();

            try {
                Iterable<Entity> all =
                        ((ClientLevelEntitiesAccessor) client.level)
                                .rubix_getEntityGetter().getAll();
                for (Entity entity : all) {
                    if (isLittlefoot(entity)) tracked.add(entity);
                }
            } catch (Exception ignored) {
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (isLittlefoot(entity)) tracked.add(entity);
                }
            }

            // ── New detection event ───────────────────────────────────────────
            // Build a list of Littlefoots that haven't been announced yet.
            // Each one gets its own party chat message so teammates get separate
            // waypoints for each individual mob.
            //
            // Two-layer deduplication:
            //  1. Entity ID — fast path, same ID = definitely same mob
            //  2. Position  — catches the case where Hypixel sends a new spawn
            //     packet (new entity ID) when the mob re-enters tracking range
            List<Entity> newlyFound = new ArrayList<>();
            for (Entity e : tracked) {
                // Fast path: already announced by ID
                if (announcedIds.contains(e.getId())) continue;

                int fx = (int) Math.floor(e.getX());
                int fy = (int) Math.floor(e.getY());
                int fz = (int) Math.floor(e.getZ());

                // Position check: within 8 blocks of a previously announced mob?
                boolean nearKnown = false;
                for (int[] pos : announcedPositions) {
                    int dx = fx - pos[0], dy = fy - pos[1], dz = fz - pos[2];
                    if (dx * dx + dy * dy + dz * dz <= 8 * 8) {
                        nearKnown = true;
                        break;
                    }
                }

                // Register the new ID regardless so future ticks hit the fast path
                announcedIds.add(e.getId());

                if (nearKnown) continue; // same mob, new ID — skip alert

                announcedPositions.add(new int[]{fx, fy, fz});
                newlyFound.add(e);
            }

            if (!newlyFound.isEmpty()) {
                lastDetectionTime = System.currentTimeMillis();
                beepsRemaining    = 3;
                beepCooldown      = 1;

                if (client.getConnection() != null) {
                    for (Entity e : newlyFound) {
                        int fx = (int) Math.floor(e.getX());
                        int fy = (int) Math.floor(e.getY());
                        int fz = (int) Math.floor(e.getZ());
                        client.getConnection().sendCommand(
                                "pc [RubixMod] LITTLEFOOT found! Coords: " + fx + ", " + fy + ", " + fz);
                    }
                }
            }
        });
    }

    /**
     * Returns true when the tab list or scoreboard sidebar contains "Mineshaft".
     * Called only once per second — not on every tick.
     */
    private static boolean checkIsInGlaciteMineshaft() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return false;

            // ── Tab list ──────────────────────────────────────────────────────
            if (mc.getConnection() != null) {
                for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
                    if (info.getTabListDisplayName() == null) continue;
                    String text = info.getTabListDisplayName().getString()
                            .replaceAll("\u00a7.", "").trim();
                    if (text.contains("Mineshaft")) return true;
                }
            }

            // ── Scoreboard sidebar ────────────────────────────────────────────
            Scoreboard sb      = mc.level.getScoreboard();
            Objective  sidebar = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (sidebar != null) {
                if (sidebar.getDisplayName().getString()
                        .replaceAll("\u00a7.", "").contains("Mineshaft")) return true;

                for (PlayerScoreEntry entry : sb.listPlayerScores(sidebar)) {
                    if (entry.display() != null && entry.display().getString()
                            .replaceAll("\u00a7.", "").contains("Mineshaft")) return true;
                    if (entry.owner().replaceAll("\u00a7.", "").contains("Mineshaft")) return true;
                }
            }
        } catch (Exception ignored) { }

        return false;
    }

    private static boolean isLittlefoot(Entity entity) {
        if (entity.hasCustomName()) {
            String name = entity.getCustomName().getString()
                    .replaceAll("\u00a7.", "").trim();
            if (name.contains("Littlefoot")) return true;
        }
        return entity.getDisplayName().getString()
                .replaceAll("\u00a7.", "").trim()
                .contains("Littlefoot");
    }

    public static List<Entity> getTracked() {
        return new ArrayList<>(tracked);
    }

    public static boolean isTracked(Entity entity) {
        return tracked.contains(entity);
    }
}
