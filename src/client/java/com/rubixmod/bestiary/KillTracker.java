package com.rubixmod.bestiary;

import com.rubixmod.RubixMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KillTracker {

    private static String currentLocation = null;

    private static final Pattern AREA_PATTERN = Pattern.compile("Area:\\s*(.+)");

    private static final Pattern STRIP_PATTERN = Pattern.compile(
            "\u00a7."
                    + "|\\[Lv\\s*\\d+\\]"
                    + "|\\[\\d+\\]"
                    + "|Lv\\d+"
                    + "|\u2764.*"
                    + "|\\d+/\\d+"
                    + "|[\u2726\u272f\u2748\u2741\u2694\u2620\u2605\u2663\u2660\u2665\u2666\u29e3]"
    );

    private static final Map entityHealthMap = new HashMap();
    private static int tickCounter = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;
            tickCounter++;

            if (tickCounter % 40 == 0) {
                String area = readAreaFromTabList(client);
                if (area != null && !area.equals(currentLocation)) {
                    currentLocation = area;
                    RubixMod.LOGGER.info("RubixMod: Location updated to '{}'", currentLocation);
                }
            }

            checkForDeaths(client);

            if (tickCounter % 200 == 0) {
                entityHealthMap.clear();
            }
        });
    }

    private static String readAreaFromTabList(Minecraft client) {
        try {
            if (client.getConnection() == null) return null;

            // Check player display names in the tab list for "Area: XYZ"
            Collection players = client.getConnection().getListedOnlinePlayers();
            for (Object obj : players) {
                PlayerInfo info = (PlayerInfo) obj;
                if (info.getTabListDisplayName() == null) continue;

                String displayName = info.getTabListDisplayName().getString()
                        .replaceAll("\u00a7.", "").trim();

                Matcher m = AREA_PATTERN.matcher(displayName);
                if (m.find()) {
                    return m.group(1).trim();
                }
            }
        } catch (Exception e) {
            // fail silently
        }
        return null;
    }

    private static void checkForDeaths(Minecraft client) {
        if (currentLocation == null) return;

        ClientLevel level = client.level;
        Player player = client.player;
        if (level == null || player == null) return;

        for (Object obj : level.entitiesForRendering()) {
            if (!(obj instanceof LivingEntity)) continue;
            if (obj instanceof Player) continue;

            LivingEntity living = (LivingEntity) obj;
            int id = living.getId();
            float health = living.getHealth();
            Float lastHealth = (Float) entityHealthMap.get(id);

            if (lastHealth != null && lastHealth > 0 && health <= 0) {
                if (player.distanceTo(living) <= 20.0) {
                    handleEntityDeath(living);
                }
                entityHealthMap.remove(id);
            } else {
                entityHealthMap.put(id, health);
            }
        }
    }

    private static void handleEntityDeath(Entity entity) {
        String rawName = entity.hasCustomName() && entity.getCustomName() != null
                ? entity.getCustomName().getString()
                : entity.getName().getString();

        String cleanName = cleanEntityName(rawName);
        if (cleanName.isEmpty()) return;

        List categories = getCategoriesForLocation(currentLocation);
        if (categories.isEmpty()) return;

        for (int i = 0; i < categories.size(); i++) {
            String category = (String) categories.get(i);
            String matchedMob = findBestiaryMatch(category, cleanName);
            if (matchedMob == null) continue;

            long[] kills = BestiaryData.getKills(category, matchedMob);
            if (kills == null) continue;

            long newKills = Math.min(kills[0] + 1, kills[1]);
            BestiaryData.saveMob(category, matchedMob, newKills, kills[1]);
            BestiaryData.save();

            RubixMod.LOGGER.info("RubixMod: Kill tracked! {} > {} ({}/{})",
                    category, matchedMob, newKills, kills[1]);
            return;
        }
    }

    public static String cleanEntityName(String raw) {
        return STRIP_PATTERN.matcher(raw).replaceAll(" ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String findBestiaryMatch(String category, String cleanName) {
        Set mobs = BestiaryData.getMobsInCategory(category);
        if (mobs == null || mobs.isEmpty()) return null;

        String lowerClean = cleanName.toLowerCase();

        for (Object obj : mobs) {
            String mob = (String) obj;
            if (mob.equalsIgnoreCase(cleanName)) return mob;
        }
        for (Object obj : mobs) {
            String mob = (String) obj;
            if (lowerClean.contains(mob.toLowerCase())) return mob;
        }
        for (Object obj : mobs) {
            String mob = (String) obj;
            if (mob.toLowerCase().contains(lowerClean)) return mob;
        }
        return null;
    }

    private static List getCategoriesForLocation(String location) {
        List result = new ArrayList();
        if (location == null) return result;

        String loc = location.toLowerCase();

        if (loc.contains("private island") || loc.contains("your island")) {
            result.add("Your Island");
        }
        if (loc.contains("hub") || loc.contains("village") || loc.contains("graveyard")
                || loc.contains("coal mine") || loc.contains("gold mine")
                || loc.contains("birch park") || loc.contains("spruce woods")
                || loc.contains("oak wood") || loc.contains("savanna woodland")
                || loc.contains("dark thicket")) {
            result.add("Hub");
            result.add("Mythological Creatures");
            result.add("Spooky Festival");
        }
        if (loc.contains("barn") || loc.contains("mushroom desert")
                || loc.contains("farming island")) {
            result.add("The Farming Islands");
            result.add("Spooky Festival");
        }
        if (loc.contains("garden")) {
            result.add("The Garden");
        }
        if (loc.contains("spider")) {
            result.add("Spider's Den");
            result.add("Spooky Festival");
        }
        if (loc.contains("the end") || loc.contains("dragon's nest")
                || loc.contains("void sepulture") || loc.contains("void slate")) {
            result.add("The End");
        }
        if (loc.contains("crimson") || loc.contains("burning desert")
                || loc.contains("magma fields") || loc.contains("blazing volcano")
                || loc.contains("stronghold") || loc.contains("barbarian")) {
            result.add("Crimson Isle");
            result.add("Fishing > Lava");
        }
        if (loc.contains("deep caverns") || loc.contains("gunpowder mines")
                || loc.contains("lapis quarry") || loc.contains("pigmen's den")
                || loc.contains("slimehill") || loc.contains("diamond reserve")
                || loc.contains("obsidian sanctuary")) {
            result.add("Deep Caverns");
        }
        if (loc.contains("dwarven mines") || loc.contains("abandoned quarry")
                || loc.contains("rampart") || loc.contains("royal mines")
                || loc.contains("lava springs") || loc.contains("forge")) {
            result.add("Dwarven Mines");
        }
        if (loc.contains("crystal hollows") || loc.contains("goblin holdout")
                || loc.contains("mithril deposits") || loc.contains("magma chamber")) {
            result.add("Crystal Hollows");
            result.add("Fishing > Lava");
        }
        if (loc.contains("the park") || loc.contains("howling cave")
                || loc.contains("tranquil acres") || loc.contains("lonely island")) {
            result.add("The Park");
        }
        if (loc.contains("galatea")) {
            result.add("Galatea");
        }
        if (loc.contains("backwater")) {
            result.add("Fishing > Backwater Bayou");
            result.add("Fishing > Fishing");
        }
        if (loc.contains("catacombs") || loc.contains("dungeon")) {
            result.add("The Catacombs");
        }
        if (loc.contains("kuudra")) {
            result.add("Kuudra");
        }
        if (loc.contains("jerry")) {
            result.add("Jerry");
            result.add("Fishing > Winter");
        }
        if (loc.contains("spooky")) {
            result.add("Spooky Festival");
        }

        // Global: Jerry and general fishing mobs can appear anywhere
        result.add("Jerry");
        result.add("Fishing > Fishing");

        return result;
    }

    public static String getCurrentLocation() {
        return currentLocation;
    }
}