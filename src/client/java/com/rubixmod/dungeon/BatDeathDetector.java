package com.rubixmod.dungeon;

import com.rubixmod.RubixMod;
import com.rubixmod.config.RubixConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
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

    private static final Map trackedBats = new HashMap<>();
    private static final Set alertedBats = new HashSet<>();
    private static final int MIN_TICKS_BEFORE_ALERT = 5;

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            trackedBats.clear();
            alertedBats.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!RubixConfig.get().batDeathAlertEnabled) return;
            if (client.player == null || client.level == null) return;
            if (!isInCatacombs()) {
                if (!trackedBats.isEmpty()) {
                    trackedBats.clear();
                    alertedBats.clear();
                }
                return;
            }

            List currentBatIds = new ArrayList<>();

            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof AmbientCreature)) continue;
                int id = entity.getId();
                currentBatIds.add(id);

                // Trigger instantly when bat health hits 0
                if (entity instanceof LivingEntity) {
                    LivingEntity living = (LivingEntity) entity;
                    int ticksSeen = trackedBats.containsKey(id) ? (Integer) trackedBats.get(id) : 0;
                    if (living.getHealth() <= 0 && !alertedBats.contains(id) && ticksSeen >= MIN_TICKS_BEFORE_ALERT) {
                        alertedBats.add(id);
                        RubixMod.LOGGER.info("RubixMod: Bat id={} health<=0 after {} ticks, triggering alert!", id, ticksSeen);
                        onBatKilled(client);
                    }
                }
            }

            // Clean up bats that have fully unloaded (no alert, just cleanup)
            List toRemove = new ArrayList<>();
            for (Object keyObj : new ArrayList<>(trackedBats.keySet())) {
                int batId = (Integer) keyObj;
                if (!currentBatIds.contains(batId)) {
                    toRemove.add(batId);
                }
            }
            for (Object idObj : toRemove) {
                trackedBats.remove(idObj);
                alertedBats.remove(idObj);
            }

            // Add/increment tracked bats
            for (Object idObj : currentBatIds) {
                int batId = (Integer) idObj;
                int prev = trackedBats.containsKey(batId) ? (Integer) trackedBats.get(batId) : 0;
                trackedBats.put(batId, prev + 1);
            }
        });
    }

    private static void onBatKilled(Minecraft client) {
        RubixMod.LOGGER.info("RubixMod: Bat killed in Catacombs!");
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
            Minecraft client = Minecraft.getInstance();
            if (client.level == null) return false;
            Scoreboard scoreboard = client.level.getScoreboard();

            for (PlayerTeam team : scoreboard.getPlayerTeams()) {
                String entry = team.getPlayerPrefix().getString()
                        + team.getPlayerSuffix().getString();
                if (entry.contains("Catacombs")) return true;
            }

            for (Objective obj : scoreboard.getObjectives()) {
                String name = obj.getDisplayName().getString();
                if (name.contains("Catacombs") || name.contains("SKYBLOCK")) return true;
            }

            Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (sidebar != null) {
                for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
                    if (entry.owner().contains("Catacombs")) return true;
                }
            }

            return false;
        } catch (Exception e) {
            RubixMod.LOGGER.info("RubixMod: isInCatacombs error: {}", e.getMessage());
            return false;
        }
    }
}