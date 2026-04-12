package com.rubixmod.dungeon;

import com.rubixmod.config.RubixConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BatDeathDetector {

    private static final Map<Integer, Integer> trackedBats  = new HashMap<>();
    private static final Set<Integer>          alertedBats  = new HashSet<>();

    /**
     * Bats that received damage at least once (hurtTime > 0 was observed).
     * Only bats in this set can trigger a death alert — bats that vanish without
     * ever being hit (e.g. despawned by a wither door opening) are ignored.
     */
    private static final Set<Integer> damagedBats = new HashSet<>();

    private static final int MIN_TICKS_BEFORE_ALERT = 5;

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            trackedBats.clear();
            alertedBats.clear();
            damagedBats.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!RubixConfig.get().batDeathAlertEnabled) return;
            if (client.player == null || client.level == null) return;

            if (!isInCatacombs()) {
                if (!trackedBats.isEmpty()) {
                    trackedBats.clear();
                    alertedBats.clear();
                    damagedBats.clear();
                }
                return;
            }

            List<Integer> currentBatIds = new ArrayList<>();

            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof AmbientCreature)) continue;
                int id = entity.getId();
                currentBatIds.add(id);

                if (entity instanceof LivingEntity living) {
                    // Mark bat as damaged if it has a non-zero hurtTime (was hit)
                    if (living.hurtTime > 0) {
                        damagedBats.add(id);
                    }

                    int ticksSeen = trackedBats.getOrDefault(id, 0);
                    if (living.getHealth() <= 0
                            && !alertedBats.contains(id)
                            && ticksSeen >= MIN_TICKS_BEFORE_ALERT
                            && damagedBats.contains(id)) {
                        alertedBats.add(id);
                        onBatKilled(client);
                    }
                }
            }

            // Alert when a tracked, previously-damaged bat disappears from the entity list.
            // Hypixel removes dead entities server-side without setting health to 0.
            // The damagedBats gate prevents door-despawned bats from triggering false alerts.
            List<Integer> toRemove = new ArrayList<>();
            for (int batId : trackedBats.keySet()) {
                if (!currentBatIds.contains(batId)) toRemove.add(batId);
            }
            for (int batId : toRemove) {
                int ticksSeen = trackedBats.getOrDefault(batId, 0);
                if (!alertedBats.contains(batId)
                        && ticksSeen >= MIN_TICKS_BEFORE_ALERT
                        && damagedBats.contains(batId)) {
                    onBatKilled(client);
                }
                trackedBats.remove(batId);
                alertedBats.remove(batId);
                damagedBats.remove(batId);
            }

            for (int batId : currentBatIds) {
                trackedBats.merge(batId, 1, Integer::sum);
            }
        });
    }

    private static void onBatKilled(Minecraft client) {
        client.execute(() -> {
            if (client.player != null) {
                client.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 2.0f);
            }
            client.gui.setTitle(
                    Component.literal("BAT KILLED")
                            .withStyle(s -> s.withColor(0xFF5555).withBold(true))
            );
            client.gui.setTimes(5, 20, 10);
        });
    }

    private static boolean isInCatacombs() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;

            if (mc.getConnection() != null) {
                for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
                    if (info.getTabListDisplayName() == null) continue;
                    String text = info.getTabListDisplayName().getString()
                            .replaceAll("\u00a7.", "").trim();
                    if (text.contains("Catacombs")) return true;
                }
            }

            if (mc.level == null) return false;
            Scoreboard sb = mc.level.getScoreboard();

            for (PlayerTeam team : sb.getPlayerTeams()) {
                String txt = (team.getPlayerPrefix().getString()
                        + team.getPlayerSuffix().getString())
                        .replaceAll("\u00a7.", "").trim();
                if (txt.contains("Catacombs")) return true;
            }

            for (Objective obj : sb.getObjectives()) {
                if (obj.getDisplayName().getString()
                        .replaceAll("\u00a7.", "").trim().contains("Catacombs")) return true;
            }

            Objective sidebar = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (sidebar != null) {
                if (sidebar.getDisplayName().getString()
                        .replaceAll("\u00a7.", "").contains("Catacombs")) return true;

                for (PlayerScoreEntry e : sb.listPlayerScores(sidebar)) {
                    String raw = e.owner().replaceAll("\u00a7.", "").trim();
                    if (raw.contains("Catacombs")) return true;
                    if (e.display() != null) {
                        String disp = e.display().getString()
                                .replaceAll("\u00a7.", "").trim();
                        if (disp.contains("Catacombs")) return true;
                    }
                }
            }

            return false;
        } catch (Exception ignored) {
            return false;
        }
    }
}
